package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.security.MessageDigest;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.backup.BackupActor;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupManifest;
import eu.avalanche7.paradigmrealms.backup.BackupReason;
import eu.avalanche7.paradigmrealms.backup.RestoreManifestStage;
import eu.avalanche7.paradigmrealms.backup.RestoreMode;
import eu.avalanche7.paradigmrealms.backup.RestoreOperationManifest;
import eu.avalanche7.paradigmrealms.backup.RestorePreparationResult;
import eu.avalanche7.paradigmrealms.backup.io.BackupArchiveVerifier;
import eu.avalanche7.paradigmrealms.backup.io.RestoreManifestFile;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditEvent;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditSink;
import eu.avalanche7.paradigmrealms.platform.protection.RealmPresenceService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

final class FabricRestoreCoordinator {
    private static final int MAXIMUM_MANIFESTS = 100;
    private static final int CHUNKS_PER_TICK = 16;

    private final MinecraftServer server;
    private final RealmRepository realms;
    private final FabricBackupPaths paths;
    private final FabricBackupCatalogService catalog;
    private final RealmBackupMutationLocks locks;
    private final RealmPresenceService presence;
    private final Clock clock;
    private final String worldIdentity;
    private final Executor fileExecutor;
    private final RollbackRequester rollbackRequester;
    private final OperationalAuditSink audit;
    private final RestoreManifestFile manifestFile = new RestoreManifestFile();
    private final BackupArchiveVerifier verifier = new BackupArchiveVerifier();
    private final Map<Long, RealmBackupMutationLocks.Handle> restoreLocks = new HashMap<>();
    private final ArrayDeque<RuntimeVerification> runtimeVerifications = new ArrayDeque<>();

    FabricRestoreCoordinator(
            MinecraftServer server,
            RealmRepository realms,
            FabricBackupPaths paths,
            FabricBackupCatalogService catalog,
            RealmBackupMutationLocks locks,
            RealmPresenceService presence,
            Clock clock,
            String worldIdentity,
            Executor fileExecutor,
            OperationalAuditSink audit,
            RollbackRequester rollbackRequester) throws IOException {
        this.server = server;
        this.realms = realms;
        this.paths = paths;
        this.catalog = catalog;
        this.locks = locks;
        this.presence = presence;
        this.clock = clock;
        this.worldIdentity = worldIdentity;
        this.fileExecutor = fileExecutor;
        this.audit = audit;
        this.rollbackRequester = rollbackRequester;
        recoverManifests();
    }

    CompletableFuture<RestorePreparationResult> prepare(
            BackupId backupId,
            RestoreMode mode,
            BackupActor actor) {
        if (mode != RestoreMode.WORLD_ONLY) {
            return CompletableFuture.completedFuture(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.UNSUPPORTED_MODE,
                    "Only WORLD_ONLY restore is enabled until metadata conflict checks are completed."));
        }

        BackupCatalogEntry entry = catalog.find(backupId).orElse(null);
        if (entry == null) {
            return CompletableFuture.completedFuture(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.BACKUP_NOT_FOUND,
                    "No backup exists with that ID."));
        }

        CompletableFuture<RestorePreparationResult> result = new CompletableFuture<>();
        Path archive = paths.backupRoot().resolve(entry.archiveRelativePath());
        CompletableFuture
                .supplyAsync(() -> verifier.verify(archive), fileExecutor)
                .whenComplete((verification, failure) -> server.execute(() -> {
                    if (failure != null || !verification.valid()) {
                        result.complete(RestorePreparationResult.failed(
                                RestorePreparationResult.Status.BACKUP_INVALID,
                                "The backup failed integrity verification."));
                        return;
                    }
                    BackupManifest manifest = verification.manifest().orElseThrow();
                    if (!catalogMatchesManifest(entry, manifest)) {
                        result.complete(RestorePreparationResult.failed(
                                RestorePreparationResult.Status.BACKUP_INVALID,
                                "The catalog entry does not match the archive manifest."));
                        return;
                    }
                    prepareVerified(entry, manifest, mode, actor, result);
                }));
        return result;
    }

    void tick() {
        RuntimeVerification verification = runtimeVerifications.peek();
        if (verification == null) {
            return;
        }

        ServerWorld world = realmsWorld();
        for (int count = 0; count < CHUNKS_PER_TICK && verification.hasNext(); count++) {
            var coordinate = verification.next();
            world.getChunk(coordinate.x(), coordinate.z());
        }
        if (verification.hasNext()) {
            return;
        }

        runtimeVerifications.remove();
        finishRuntimeVerification(verification);
    }

    boolean cancel(BackupId backupId) {
        try {
            List<Path> manifests = manifestPaths();
            if (manifests.size() > MAXIMUM_MANIFESTS) {
                throw new IOException("restore manifest limit exceeded");
            }
            for (Path path : manifests) {
                RestoreOperationManifest operation = manifestFile.read(path);
                if (!operation.backupId().equals(backupId) || !canCancel(operation.stage())) {
                    continue;
                }

                manifestFile.write(
                        path,
                        operation.failed(
                                "CANCELLED_BY_ADMIN",
                                "Restore preparation was cancelled before offline rewriting.",
                                clock.instant()));
                try {
                    catalog.markRestoreInUse(operation.backupId(), false);
                    catalog.pin(operation.rollbackBackupId(), false);
                } finally {
                    release(operation.realmId());
                    presence.revalidateRealm(new RealmId(operation.realmId()));
                }
                return true;
            }
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.error("Could not cancel prepared restore: {}", exception.getMessage());
        }
        return false;
    }

    private void prepareVerified(
            BackupCatalogEntry sourceEntry,
            BackupManifest manifest,
            RestoreMode mode,
            BackupActor actor,
            CompletableFuture<RestorePreparationResult> result) {
        Realm target = realms.findById(new RealmId(manifest.realmId())).orElse(null);
        if (!matchesTarget(target, manifest)) {
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.TARGET_MISMATCH,
                    "The realm no longer owns the allocation captured by this backup."));
            return;
        }
        if (restoreLocks.containsKey(target.id().value())) {
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.TARGET_BUSY,
                    "This realm already has a restore operation in progress."));
            return;
        }

        rollbackRequester.request(target, BackupReason.PRE_MANUAL_RESTORE, actor,
                (successful, rollbackEntry) -> {
                    if (!successful || rollbackEntry.isEmpty()) {
                        result.complete(RestorePreparationResult.failed(
                                RestorePreparationResult.Status.ROLLBACK_BACKUP_FAILED,
                                "The current realm could not be backed up, so the restore was not prepared."));
                        return;
                    }
                    finishPreparation(
                            sourceEntry,
                            manifest,
                            mode,
                            actor,
                            rollbackEntry.orElseThrow(),
                            result);
                });
    }

    private void finishPreparation(
            BackupCatalogEntry sourceEntry,
            BackupManifest sourceManifest,
            RestoreMode mode,
            BackupActor actor,
            BackupCatalogEntry rollback,
            CompletableFuture<RestorePreparationResult> result) {
        Realm target = realms.findById(new RealmId(sourceManifest.realmId())).orElse(null);
        if (!matchesTarget(target, sourceManifest)) {
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.TARGET_MISMATCH,
                    "The realm allocation changed while its rollback backup was created."));
            return;
        }

        UUID operationId = UUID.randomUUID();
        BackupCellBounds bounds = sourceManifest.cellBounds();
        RealmBackupMutationLocks.Handle lock = locks.tryAcquireRestore(
                target.id().value(),
                bounds,
                operationId).orElse(null);
        if (lock == null) {
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.TARGET_BUSY,
                    "The realm became busy before restore preparation completed."));
            return;
        }

        if (!evacuate(target)) {
            lock.close();
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.EVACUATION_FAILED,
                    "One or more occupants could not be evacuated safely."));
            return;
        }

        Instant now = clock.instant();
        RestoreOperationManifest operation = new RestoreOperationManifest(
                RestoreOperationManifest.CURRENT_VERSION,
                operationId,
                sourceManifest.backupId(),
                target.id().value(),
                target.owner().uuid(),
                bounds,
                DimensionId.REALMS.toString(),
                sourceManifest.allocationProfile(),
                sourceManifest.strategy(),
                worldIdentity,
                realmStateDigest(),
                sourceEntry.archiveRelativePath(),
                "dimensions/paradigm_realms/realms",
                "backups/paradigm-realms/quarantine/" + operationId,
                rollback.backupId(),
                mode,
                RestoreManifestStage.SERVER_STOPPED_EXPECTED,
                now,
                now,
                Optional.empty(),
                Optional.empty());

        try {
            catalog.pin(rollback.backupId(), true);
            catalog.markRestoreInUse(sourceEntry.backupId(), true);
            Path manifestPath = manifestPath(operationId);
            manifestFile.write(manifestPath, operation);
            restoreLocks.put(target.id().value(), lock);
            presence.revalidateRealm(target.id());
            auditRestore(operation, actor.uuid(), "RESTORE_PREPARED", "PREPARED");
            result.complete(RestorePreparationResult.prepared(operationId, rollback.backupId()));
        } catch (IOException exception) {
            lock.close();
            clearPreparationFlags(sourceEntry.backupId(), rollback.backupId());
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                    "The durable restore manifest could not be written."));
        }
    }

    private void recoverManifests() throws IOException {
        List<Path> manifests = manifestPaths();
        if (manifests.size() > MAXIMUM_MANIFESTS) {
            throw new IOException("restore manifest recovery limit exceeded");
        }

        for (Path path : manifests) {
            RestoreOperationManifest operation = manifestFile.read(path);
            if (operation.stage() == RestoreManifestStage.COMPLETED
                    || operation.stage() == RestoreManifestStage.FAILED) {
                continue;
            }
            Realm realm = realms.findById(new RealmId(operation.realmId())).orElse(null);
            if (realm == null
                    || !realm.allocation().profile().value().equals(operation.allocationProfile())
                    || !BackupCellBounds.from(realm.allocation().cellBounds())
                            .equals(operation.targetBounds())) {
                manifestFile.write(
                        path,
                        operation.failed(
                                "STARTUP_TARGET_MISMATCH",
                                "Realm identity or allocation changed before restore recovery.",
                                clock.instant()));
                ParadigmRealms.LOGGER.error(
                        "Restore operation {} failed startup recovery because its target changed",
                        operation.operationId());
                catalog.markRestoreInUse(operation.backupId(), false);
                continue;
            }

            RealmBackupMutationLocks.Handle handle = locks.tryAcquireRestore(
                    realm.id().value(),
                    operation.targetBounds(),
                    operation.operationId()).orElse(null);
            if (handle == null) {
                manifestFile.write(
                        path,
                        operation.failed(
                                "STARTUP_RESTORE_CONFLICT",
                                "Another restore manifest already controls this realm.",
                                clock.instant()));
                catalog.markRestoreInUse(operation.backupId(), false);
                continue;
            }
            restoreLocks.put(realm.id().value(), handle);

            if (operation.stage() == RestoreManifestStage.OFFLINE_VERIFIED) {
                auditRestore(operation, Optional.empty(), "OFFLINE_RESTORE_COMPLETED", "OFFLINE_VERIFIED");
                RestoreOperationManifest booted = operation.withStage(
                        RestoreManifestStage.SERVER_BOOTED,
                        clock.instant());
                manifestFile.write(path, booted);
                scheduleRuntimeVerification(path, booted);
            } else if (operation.stage() == RestoreManifestStage.SERVER_BOOTED) {
                scheduleRuntimeVerification(path, operation);
            } else if (operation.stage() == RestoreManifestStage.RUNTIME_VERIFIED) {
                completeRecoveredVerification(path, operation);
            }
        }
    }

    private void scheduleRuntimeVerification(
            Path path,
            RestoreOperationManifest operation) {
        runtimeVerifications.add(new RuntimeVerification(
                path,
                operation,
                new ArrayDeque<>(operation.targetBounds().coordinates())));
    }

    private void completeRecoveredVerification(
            Path path,
            RestoreOperationManifest operation) throws IOException {
        RestoreOperationManifest completed = operation.withStage(
                RestoreManifestStage.COMPLETED,
                clock.instant());
        manifestFile.write(path, completed);
        catalog.markRestoreInUse(operation.backupId(), false);
        catalog.pin(operation.rollbackBackupId(), false);
        auditRestore(operation, Optional.empty(), "RUNTIME_RESTORE_VERIFIED", "COMPLETED");
        release(operation.realmId());
        presence.revalidateRealm(new RealmId(operation.realmId()));
    }

    private List<Path> manifestPaths() throws IOException {
        Path directory = paths.restoreManifestDirectory();
        try (var files = Files.list(directory)) {
            return files
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .limit(MAXIMUM_MANIFESTS + 1L)
                    .toList();
        }
    }

    private static boolean canCancel(RestoreManifestStage stage) {
        return stage == RestoreManifestStage.PREPARED
                || stage == RestoreManifestStage.SERVER_STOPPED_EXPECTED;
    }

    private void finishRuntimeVerification(RuntimeVerification verification) {
        RestoreOperationManifest operation = verification.operation();
        Realm realm = realms.findById(new RealmId(operation.realmId())).orElse(null);
        try {
            if (realm == null
                    || !realm.owner().uuid().equals(operation.expectedOwnerUuid())
                    || !realm.allocation().profile().value().equals(operation.allocationProfile())
                    || !BackupCellBounds.from(realm.allocation().cellBounds())
                            .equals(operation.targetBounds())) {
                throw new IOException("realm identity changed before runtime verification");
            }
            RestoreOperationManifest verified = operation.withStage(
                    RestoreManifestStage.RUNTIME_VERIFIED,
                    clock.instant());
            manifestFile.write(verification.path(), verified);
            RestoreOperationManifest completed = verified.withStage(
                    RestoreManifestStage.COMPLETED,
                    clock.instant());
            manifestFile.write(verification.path(), completed);
            catalog.markRestoreInUse(operation.backupId(), false);
            catalog.pin(operation.rollbackBackupId(), false);
            auditRestore(operation, Optional.empty(), "RUNTIME_RESTORE_VERIFIED", "COMPLETED");
            release(operation.realmId());
            presence.revalidateRealm(new RealmId(operation.realmId()));
        } catch (IOException exception) {
            try {
                manifestFile.write(
                        verification.path(),
                        operation.failed(
                                "RUNTIME_VERIFICATION_FAILED",
                                exception.getMessage(),
                                clock.instant()));
            } catch (IOException writeFailure) {
                ParadigmRealms.LOGGER.error(
                        "Could not persist restore verification failure: {}",
                        writeFailure.getMessage());
            }
            auditRestore(operation, Optional.empty(), "RUNTIME_RESTORE_FAILED", "FAILED");
        }
    }

    private boolean evacuate(Realm target) {
        for (int attempt = 0; attempt < 3; attempt++) {
            var result = presence.evacuateAndVerify(target);
            if (result == eu.avalanche7.paradigmrealms.application.RealmLifecycleEffects.EvacuationResult.COMPLETE) {
                return true;
            }
            if (result == eu.avalanche7.paradigmrealms.application.RealmLifecycleEffects.EvacuationResult.FAILED) {
                return false;
            }
        }
        return false;
    }

    private boolean matchesTarget(Realm realm, BackupManifest manifest) {
        return realm != null
                && realm.id().value() == manifest.realmId()
                && realm.state() == RealmLifecycleState.ACTIVE
                && realm.lifecycleOperation().isEmpty()
                && realm.dimension().equals(DimensionId.REALMS)
                && manifest.dimension().equals(DimensionId.REALMS.toString())
                && manifest.worldIdentity().equals(worldIdentity)
                && manifest.allocationProfile().equals(realm.allocation().profile().value())
                && manifest.strategy() == eu.avalanche7.paradigmrealms.backup.BackupStrategySelector
                        .select(realm.allocation())
                && BackupCellBounds.from(realm.allocation().cellBounds())
                        .equals(manifest.cellBounds());
    }

    private static boolean catalogMatchesManifest(
            BackupCatalogEntry entry,
            BackupManifest manifest) {
        return entry.backupId().equals(manifest.backupId())
                && entry.realmId() == manifest.realmId()
                && entry.ownerUuid().equals(manifest.ownerUuid())
                && entry.formatVersion() == manifest.formatVersion();
    }

    private void clearPreparationFlags(BackupId sourceBackupId, BackupId rollbackBackupId) {
        try {
            catalog.markRestoreInUse(sourceBackupId, false);
            catalog.pin(rollbackBackupId, false);
        } catch (IOException cleanupFailure) {
            ParadigmRealms.LOGGER.error(
                    "Could not roll back restore catalog flags: {}",
                    cleanupFailure.getMessage());
        }
    }

    private void auditRestore(
            RestoreOperationManifest operation,
            Optional<UUID> actor,
            String event,
            String outcome) {
        audit.append(new OperationalAuditEvent(
                1,
                clock.instant(),
                event,
                outcome,
                Optional.of(operation.operationId()),
                actor,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new RealmId(operation.realmId())),
                Optional.empty(),
                Map.of(
                        "backupId", operation.backupId().value(),
                        "rollbackBackupId", operation.rollbackBackupId().value(),
                        "mode", operation.mode().name())),
                true);
    }

    private Path manifestPath(UUID operationId) throws IOException {
        return paths.restoreManifestDirectory().resolve(operationId + ".json");
    }

    private ServerWorld realmsWorld() {
        var key = net.minecraft.registry.RegistryKey.of(
                net.minecraft.registry.RegistryKeys.WORLD,
                net.minecraft.util.Identifier.of(
                        DimensionId.REALMS.namespace(),
                        DimensionId.REALMS.path()));
        ServerWorld world = server.getWorld(key);
        if (world == null) {
            throw new IllegalStateException("Realms dimension is not loaded");
        }
        return world;
    }

    private void release(long realmId) {
        RealmBackupMutationLocks.Handle lock = restoreLocks.remove(realmId);
        if (lock != null) {
            lock.close();
        }
    }

    private String realmStateDigest() {
        Path state = paths.worldRoot().resolve("data/paradigm_realms.dat");
        try (var input = Files.newInputStream(state)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("could not fingerprint Realms persistent state", exception);
        }
    }

    @FunctionalInterface
    interface RollbackRequester {
        void request(
                Realm realm,
                BackupReason reason,
                BackupActor actor,
                FabricRealmBackupService.CompletionHandler completion);
    }

    private record RuntimeVerification(
            Path path,
            RestoreOperationManifest operation,
            ArrayDeque<eu.avalanche7.paradigmrealms.backup.ChunkCoordinate> chunks) {
        boolean hasNext() {
            return !chunks.isEmpty();
        }

        eu.avalanche7.paradigmrealms.backup.ChunkCoordinate next() {
            return chunks.removeFirst();
        }
    }
}
