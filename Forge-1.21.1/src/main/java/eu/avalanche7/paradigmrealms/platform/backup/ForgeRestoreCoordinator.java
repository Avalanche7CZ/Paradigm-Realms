package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
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
import eu.avalanche7.paradigmrealms.backup.RestoreRecoveryPolicy;
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

final class ForgeRestoreCoordinator {
    private static final int MAXIMUM_MANIFESTS = 100;
    private static final int CHUNKS_PER_TICK = 16;

    private final MinecraftServer server;
    private final RealmRepository realms;
    private final ForgeBackupPaths paths;
    private final ForgeBackupCatalogService catalog;
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
    private final java.util.Set<BackupId> preparingRestores = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final ArrayDeque<RuntimeVerification> runtimeVerifications = new ArrayDeque<>();

    ForgeRestoreCoordinator(
            MinecraftServer server,
            RealmRepository realms,
            ForgeBackupPaths paths,
            ForgeBackupCatalogService catalog,
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
            BackupActor actor,
            boolean backupsEnabled) {
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

        Map.Entry<Path, RestoreOperationManifest> retry = null;
        try {
            if (entry.restoreInUse()) {
                retry = pendingManifests().entrySet().stream()
                        .filter(item -> item.getValue().backupId().equals(backupId))
                        .filter(item -> RestoreRecoveryPolicy.canRetry(item.getValue()))
                        .findFirst().orElse(null);
            }
        } catch (IOException | RuntimeException exception) {
            return CompletableFuture.completedFuture(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                    "Could not read the failed restore manifest."));
        }
        if (!backupsEnabled && retry == null) {
            return CompletableFuture.completedFuture(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.ROLLBACK_BACKUP_FAILED, "Realm backups are disabled."));
        }
        if ((entry.restoreInUse() && retry == null) || !preparingRestores.add(backupId)) {
            return CompletableFuture.completedFuture(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.TARGET_BUSY,
                    "This backup is already required by a restore operation."));
        }
        try {
            if (!catalog.markRestoreInUse(backupId, true)) {
                throw new IOException("Source backup disappeared before verification");
            }
        } catch (IOException exception) {
            preparingRestores.remove(backupId);
            return CompletableFuture.completedFuture(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                    "Could not protect the source backup for restore preparation."));
        }

        CompletableFuture<RestorePreparationResult> result = new CompletableFuture<>();
        Map.Entry<Path, RestoreOperationManifest> retryManifest = retry;
        result.whenComplete((prepared, failure) -> {
            preparingRestores.remove(backupId);
            if (retryManifest == null
                    && (failure != null || prepared.status() != RestorePreparationResult.Status.PREPARED)) {
                try {
                    catalog.markRestoreInUse(backupId, false);
                } catch (IOException exception) {
                    ParadigmRealms.LOGGER.error("Could not release source backup protection", exception);
                }
            }
        });
        Path archive = paths.backupRoot().resolve(entry.archiveRelativePath());
        try {
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
                        try {
                            if (retryManifest != null) {
                                retryPreparation(retryManifest.getKey(), manifest, actor, result);
                            } else {
                                prepareVerified(entry, manifest, mode, actor, result);
                            }
                        } catch (RuntimeException exception) {
                            result.complete(RestorePreparationResult.failed(
                                    RestorePreparationResult.Status.ROLLBACK_BACKUP_FAILED,
                                    "Could not start rollback backup: " + exception.getMessage()));
                        }
                    }))
                    .exceptionally(dispatchFailure -> {
                        result.complete(RestorePreparationResult.failed(
                                RestorePreparationResult.Status.BACKUP_INVALID,
                                "Could not deliver backup verification to the server."));
                        return null;
                    });
        } catch (RuntimeException exception) {
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.BACKUP_INVALID,
                    "Could not start backup verification."));
        }
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
            if (preparingRestores.contains(backupId)) return false;
            for (var item : pendingManifests().entrySet()) {
                Path path = item.getKey();
                RestoreOperationManifest operation = item.getValue();
                if (!operation.backupId().equals(backupId) || !RestoreRecoveryPolicy.canCancel(operation)) {
                    continue;
                }
                Path quarantine = eu.avalanche7.paradigmrealms.backup.io.BackupPathSafety.resolveInside(
                        paths.worldRoot(), operation.quarantineRelativePath(), true);
                if (Files.exists(quarantine, LinkOption.NOFOLLOW_LINKS)) return false;

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

    private void retryPreparation(Path path, BackupManifest source, BackupActor actor,
            CompletableFuture<RestorePreparationResult> result) {
        RestoreOperationManifest retried = null;
        try {
            RestoreOperationManifest operation = manifestFile.read(path);
            Realm target = realms.findById(new RealmId(operation.realmId())).orElse(null);
            if (!RestoreRecoveryPolicy.canRetry(operation)
                    || !RestoreRecoveryPolicy.matchesTarget(operation, target, worldIdentity)
                    || !matchesTarget(target, source)
                    || !operation.backupId().equals(source.backupId())
                    || !restoreLocks.containsKey(operation.realmId())) {
                result.complete(RestorePreparationResult.failed(RestorePreparationResult.Status.TARGET_MISMATCH,
                        "Failed restore target changed before retry preparation."));
                return;
            }
            if (!evacuate(target)) {
                result.complete(RestorePreparationResult.failed(RestorePreparationResult.Status.EVACUATION_FAILED,
                        "One or more occupants could not be evacuated safely."));
                return;
            }
            if (!server.saveAll(false, true, true)) throw new IOException("Minecraft save barrier reported failure");
            RestoreOperationManifest replacement = RestoreRecoveryPolicy.retry(
                    operation, target, worldIdentity, realmStateDigest(), clock.instant());
            if (!catalog.pin(operation.rollbackBackupId(), true)
                    || !catalog.markRestoreInUse(operation.backupId(), true)) {
                throw new IOException("A protected restore backup disappeared before retry");
            }
            manifestFile.write(path, replacement);
            retried = replacement;
            presence.revalidateRealm(target.id());
            auditRestore(replacement, actor.uuid(), "RESTORE_PREPARED", "RETRIED");
            result.complete(RestorePreparationResult.prepared(replacement.operationId(), replacement.rollbackBackupId()));
        } catch (IOException | RuntimeException exception) {
            if (retried != null) {
                ParadigmRealms.LOGGER.error("Restore retry was prepared but post-preparation effects failed", exception);
                result.complete(RestorePreparationResult.prepared(retried.operationId(), retried.rollbackBackupId()));
            } else {
                result.complete(RestorePreparationResult.failed(RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                        "Could not prepare failed restore retry: " + exception.getMessage()));
            }
        }
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

        eu.avalanche7.paradigmrealms.backup.RollbackBackupRequest.start(completion ->
                rollbackRequester.request(target, BackupReason.PRE_MANUAL_RESTORE, actor, completion::completed))
                .whenComplete((rollback, failure) -> {
                    if (failure != null) {
                        result.complete(RestorePreparationResult.failed(
                                RestorePreparationResult.Status.ROLLBACK_BACKUP_FAILED,
                                "The current realm could not be backed up, so the restore was not prepared."));
                        return;
                    }
                    try {
                        finishPreparation(sourceEntry, manifest, mode, actor, rollback, result);
                    } catch (RuntimeException exception) {
                        result.complete(RestorePreparationResult.failed(
                                RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                                "Could not prepare restore: " + exception.getMessage()));
                    }
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

        try {
            if (pendingManifests().size() >= MAXIMUM_MANIFESTS) {
                result.complete(RestorePreparationResult.failed(RestorePreparationResult.Status.TARGET_BUSY,
                        "The maximum number of unresolved restores has been reached."));
                return;
            }
        } catch (IOException | RuntimeException exception) {
            result.complete(RestorePreparationResult.failed(RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                    "Could not read pending restore manifests."));
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

        boolean manifestWritten = false;
        try {
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

            if (!catalog.pin(rollback.backupId(), true)
                    || !catalog.markRestoreInUse(sourceEntry.backupId(), true)) {
                throw new IOException("A restore backup disappeared before preparation completed");
            }
            Path manifestPath = manifestPath(operationId);
            manifestFile.write(manifestPath, operation);
            manifestWritten = true;
            restoreLocks.put(target.id().value(), lock);
            presence.revalidateRealm(target.id());
            auditRestore(operation, actor.uuid(), "RESTORE_PREPARED", "PREPARED");
            result.complete(RestorePreparationResult.prepared(operationId, rollback.backupId()));
        } catch (IOException | RuntimeException exception) {
            if (manifestWritten) {
                ParadigmRealms.LOGGER.error("Restore {} was prepared but post-preparation effects failed", operationId, exception);
                result.complete(RestorePreparationResult.prepared(operationId, rollback.backupId()));
                return;
            }
            restoreLocks.remove(target.id().value());
            lock.close();
            clearPreparationFlags(sourceEntry.backupId(), rollback.backupId());
            result.complete(RestorePreparationResult.failed(
                    RestorePreparationResult.Status.MANIFEST_WRITE_FAILED,
                    "The durable restore manifest could not be written."));
        }
    }

    private void recoverManifests() throws IOException {
        Map<Path, RestoreOperationManifest> recovered = pendingManifests();
        java.util.Set<BackupId> protectedSources = new java.util.HashSet<>();
        for (RestoreOperationManifest operation : recovered.values()) {
            protectedSources.add(operation.backupId());
        }
        for (BackupCatalogEntry entry : catalog.list()) {
            if (entry.restoreInUse() && !protectedSources.contains(entry.backupId())) {
                catalog.markRestoreInUse(entry.backupId(), false);
            }
        }

        for (var item : recovered.entrySet()) {
            Path path = item.getKey();
            RestoreOperationManifest operation = item.getValue();
            catalog.markRestoreInUse(operation.backupId(), true);
            catalog.pin(operation.rollbackBackupId(), true);
            Realm realm = realms.findById(new RealmId(operation.realmId())).orElse(null);
            if (!RestoreRecoveryPolicy.matchesTarget(operation, realm, worldIdentity)) {
                throw new IOException("Unresolved restore target changed for operation " + operation.operationId());
            }

            RealmBackupMutationLocks.Handle handle = locks.tryAcquireRestore(
                    realm.id().value(),
                    operation.targetBounds(),
                    operation.operationId()).orElse(null);
            if (handle == null) {
                throw new IOException("Conflicting unresolved restores for realm " + realm.id().value());
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

    private Map<Path, RestoreOperationManifest> pendingManifests() throws IOException {
        return manifestFile.readPending(paths.restoreManifestDirectory(), MAXIMUM_MANIFESTS);
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
        eu.avalanche7.paradigmrealms.backup.BackupRequestResult request(
                Realm realm,
                BackupReason reason,
                BackupActor actor,
                ForgeRealmBackupService.CompletionHandler completion);
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
