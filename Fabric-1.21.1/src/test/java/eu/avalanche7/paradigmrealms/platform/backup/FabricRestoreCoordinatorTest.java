package eu.avalanche7.paradigmrealms.platform.backup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.backup.*;
import eu.avalanche7.paradigmrealms.backup.io.RestoreManifestFile;
import eu.avalanche7.paradigmrealms.domain.*;
import eu.avalanche7.paradigmrealms.domain.realm.*;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditSink;
import eu.avalanche7.paradigmrealms.persistence.*;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

class FabricRestoreCoordinatorTest {
    @TempDir Path world;
    private final RestoreManifestFile manifests = new RestoreManifestFile();
    private final Clock clock = Clock.systemUTC();
    private final Realm realm = realm();
    private final RealmBackupMutationLocks locks = new RealmBackupMutationLocks(0, (source, template, values, fallback) -> {});

    @Test
    void ownerMismatchRefusesRecoveryWithoutOverwritingTheOperation() throws Exception {
        var paths = new FabricBackupPaths(world);
        var operation = operation(RestoreManifestStage.SERVER_STOPPED_EXPECTED);
        Path path = write(paths, operation);
        var transferred = realm.transferOwnership(new RealmOwner(UUID.randomUUID()), Set.of(), Set.of(), Set.of());
        assertThrows(IOException.class, () -> coordinator(paths, catalog(paths), transferred, Runnable::run));
        assertEquals(operation, manifests.read(path));
    }

    @Test
    void failedRestoreRecoversProtectionAndRejectedRetryKeepsItEvenWithBackupsDisabled() throws Exception {
        var paths = new FabricBackupPaths(world);
        var operation = operation(RestoreManifestStage.SERVER_BOOTED)
                .failed("RUNTIME_VERIFICATION_FAILED", "failure", Instant.EPOCH);
        write(paths, operation);
        var catalog = catalog(paths);
        catalog.add(entry(operation.backupId()));
        catalog.add(entry(operation.rollbackBackupId()));
        var coordinator = coordinator(paths, catalog, realm, task -> { throw new RejectedExecutionException("stopped"); });
        assertTrue(locks.realmEntryBlocked(realm.id().value()));
        assertTrue(catalog.find(operation.backupId()).orElseThrow().restoreInUse());
        assertTrue(catalog.find(operation.rollbackBackupId()).orElseThrow().pinned());

        for (int attempt = 0; attempt < 2; attempt++) {
            assertEquals(RestorePreparationResult.Status.BACKUP_INVALID, coordinator.prepare(
                    operation.backupId(), RestoreMode.WORLD_ONLY, BackupActor.system(), false).join().status());
            assertTrue(locks.realmEntryBlocked(realm.id().value()));
            assertTrue(catalog.find(operation.backupId()).orElseThrow().restoreInUse());
            assertTrue(catalog.find(operation.rollbackBackupId()).orElseThrow().pinned());
        }
    }

    @Test
    void resolvedHistoryDoesNotBlockRecoveryOrPendingCancellationLookup() throws Exception {
        var paths = new FabricBackupPaths(world);
        for (int index = 0; index < 101; index++) write(paths, operation(RestoreManifestStage.COMPLETED));
        var pending = operation(RestoreManifestStage.SERVER_STOPPED_EXPECTED);
        write(paths, pending);
        var coordinator = coordinator(paths, catalog(paths), realm, Runnable::run);
        assertTrue(locks.realmEntryBlocked(realm.id().value()));
        assertFalse(coordinator.cancel(BackupId.generate()));
        assertTrue(locks.realmEntryBlocked(realm.id().value()));
    }

    @Test
    void failedServerDispatchCompletesRetryAndRetainsProtection() throws Exception {
        var paths = new FabricBackupPaths(world);
        var operation = operation(RestoreManifestStage.SERVER_BOOTED)
                .failed("RUNTIME_VERIFICATION_FAILED", "failure", Instant.EPOCH);
        write(paths, operation);
        var catalog = catalog(paths);
        catalog.add(entry(operation.backupId()));
        catalog.add(entry(operation.rollbackBackupId()));
        var coordinator = coordinator(paths, catalog, realm, Runnable::run);
        var retry = coordinator.prepare(operation.backupId(), RestoreMode.WORLD_ONLY, BackupActor.system(), true);
        assertTrue(retry.isDone());
        assertEquals(RestorePreparationResult.Status.BACKUP_INVALID, retry.join().status());
        assertTrue(locks.realmEntryBlocked(realm.id().value()));
        assertTrue(catalog.find(operation.backupId()).orElseThrow().restoreInUse());
    }

    @Test
    void quarantineAndRetriedPreparationCannotBeCancelled() throws Exception {
        var paths = new FabricBackupPaths(world);
        var operation = operation(RestoreManifestStage.SERVER_STOPPED_EXPECTED);
        Path path = write(paths, operation);
        var coordinator = coordinator(paths, catalog(paths), realm, Runnable::run);
        Files.createDirectories(world.resolve(operation.quarantineRelativePath()));
        assertFalse(coordinator.cancel(operation.backupId()));
        assertEquals(operation, manifests.read(path));
        assertTrue(locks.realmEntryBlocked(realm.id().value()));

        Files.delete(world.resolve(operation.quarantineRelativePath()));
        var retry = RestoreRecoveryPolicy.retry(operation.failed("RUNTIME_VERIFICATION_FAILED", "failed", Instant.EPOCH),
                realm, "world-identity", "1".repeat(64), Instant.now());
        manifests.write(path, retry);
        assertFalse(coordinator.cancel(operation.backupId()));
        assertEquals(retry, manifests.read(path));
        assertTrue(locks.realmEntryBlocked(realm.id().value()));
    }

    @Test
    void conflictingManifestsRefuseRecoveryWithoutErasingEitherStage() throws Exception {
        var paths = new FabricBackupPaths(world);
        var first = operation(RestoreManifestStage.SERVER_STOPPED_EXPECTED);
        var second = operation(RestoreManifestStage.SERVER_STOPPED_EXPECTED);
        Path firstPath = write(paths, first);
        Path secondPath = write(paths, second);
        assertThrows(IOException.class, () -> coordinator(paths, catalog(paths), realm, Runnable::run));
        assertEquals(first, manifests.read(firstPath));
        assertEquals(second, manifests.read(secondPath));
    }

    private FabricBackupCatalogService catalog(FabricBackupPaths paths) throws IOException {
        return new FabricBackupCatalogService(paths, RealmBackupConfig.DEFAULTS, clock);
    }

    private FabricRestoreCoordinator coordinator(FabricBackupPaths paths, FabricBackupCatalogService catalog,
            Realm target, Executor executor) throws IOException {
        RealmAllocator allocator = new RealmAllocator();
        var repository = new PersistentRealmRepository(new RealmStateStore() {
            @Override public StateLoadResult load() { return new StateLoadResult(RealmStateDtoV1.empty(), List.of(), true); }
            @Override public void save(RealmStateDtoV1 replacement) {}
        }, allocator);
        repository.allocateNextRealmId();
        repository.save(target);
        return new FabricRestoreCoordinator(null, repository, paths, catalog, locks, null, clock,
                "world-identity", executor, OperationalAuditSink.disabled(), (requested, reason, actor, completion) -> {
                    throw new AssertionError("Recovered retries must not request another rollback backup");
                });
    }

    private Path write(FabricBackupPaths paths, RestoreOperationManifest operation) throws IOException {
        Path path = paths.restoreManifestDirectory().resolve(operation.operationId() + ".json");
        manifests.write(path, operation);
        return path;
    }

    private RestoreOperationManifest operation(RestoreManifestStage stage) {
        return new RestoreOperationManifest(2, UUID.randomUUID(), BackupId.generate(), realm.id().value(),
                realm.owner().uuid(), BackupCellBounds.from(realm.allocation().cellBounds()), DimensionId.REALMS.toString(),
                realm.allocation().profile().value(), BackupStrategySelector.select(realm.allocation()),
                "world-identity", "0".repeat(64), "realms/1/backup.zip", "dimensions/paradigm_realms/realms",
                "backups/paradigm-realms/quarantine/" + UUID.randomUUID(), BackupId.generate(), RestoreMode.WORLD_ONLY,
                stage, Instant.EPOCH, Instant.EPOCH, Optional.empty(), Optional.empty());
    }

    private BackupCatalogEntry entry(BackupId backupId) {
        return new BackupCatalogEntry(backupId, realm.id().value(), realm.owner().uuid(), "Owner", Instant.EPOCH,
                BackupReason.MANUAL, 0, "realms/1/backup.zip", BackupIntegrityStatus.VERIFIED, false, false,
                2, "1.21.1", "test", Map.of(), realm.allocation().profile().value(),
                BackupStrategySelector.select(realm.allocation()), 0);
    }

    private static Realm realm() {
        var allocation = new RealmAllocator().preview(new RealmId(1));
        return new Realm(new RealmId(1), new RealmOwner(UUID.randomUUID()), RealmLifecycleState.ACTIVE,
                DimensionId.REALMS, allocation,
                new BlockPosition(allocation.buildableBounds().minX() * 16 + 8.5, 80,
                        allocation.buildableBounds().minZ() * 16 + 8.5, 0, 0),
                new RealmPresetId("default"), Set.of(), Set.of(), RealmAccessPolicy.PRIVATE,
                new CreationTimestamp(1), SchemaVersion.CURRENT, Optional.empty(), Optional.empty());
    }
}
