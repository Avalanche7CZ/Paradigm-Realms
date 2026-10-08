package eu.avalanche7.paradigmrealms.platform.backup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.avalanche7.paradigmrealms.backup.*;
import eu.avalanche7.paradigmrealms.backup.io.BackupArchiveWriter;

class FabricBackupCatalogServiceTest {
    @TempDir Path world;

    @Test
    void failedCatalogWriteDoesNotRemoveLivePinOrRestoreProtection() throws Exception {
        FabricBackupPaths paths = new FabricBackupPaths(world);
        var catalog = new FabricBackupCatalogService(paths, RealmBackupConfig.DEFAULTS,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        BackupId id = archive(paths);
        catalog.rebuild();
        catalog.pin(id, true);
        catalog.markRestoreInUse(id, true);
        Path directory = paths.backupRoot().resolve("catalog");
        Files.move(directory, paths.backupRoot().resolve("saved-catalog"));
        Files.writeString(directory, "simulated catalog filesystem failure");
        assertThrows(IOException.class, () -> catalog.pin(id, false));
        assertThrows(IOException.class, () -> catalog.markRestoreInUse(id, false));
        assertTrue(catalog.find(id).orElseThrow().pinned());
        assertTrue(catalog.find(id).orElseThrow().restoreInUse());
        assertFalse(catalog.delete(id));
    }

    @Test
    void liveCatalogRebuildPreservesBothProtections() throws Exception {
        FabricBackupPaths paths = new FabricBackupPaths(world);
        var catalog = new FabricBackupCatalogService(paths, RealmBackupConfig.DEFAULTS, Clock.systemUTC());
        BackupId id = archive(paths);
        catalog.rebuild();
        catalog.pin(id, true);
        catalog.markRestoreInUse(id, true);
        catalog.rebuild();
        assertTrue(catalog.find(id).orElseThrow().pinned());
        assertTrue(catalog.find(id).orElseThrow().restoreInUse());
        assertFalse(catalog.delete(id));
    }

    private static BackupId archive(FabricBackupPaths paths) throws Exception {
        var bounds = new BackupCellBounds(0, 0, 15, 15);
        var manifest = new BackupManifest(1, BackupId.generate(), Instant.EPOCH,
                BackupActor.system(), BackupReason.MANUAL, "1.21.1", "test", "paradigm_realms:realms",
                "world-identity", 42, UUID.randomUUID(), "Owner", "paradigm_realms:starter_island",
                "ACTIVE", bounds, Map.of(), "SNAPSHOT_INCLUDED", false);
        var metadata = new RealmMetadataSnapshot(1, 42, manifest.ownerUuid(), "Owner", "Realm", "",
                manifest.preset(), "ACTIVE", manifest.dimension(), bounds, 0, 80, 0, 0, 0, "PRIVATE",
                List.of(), List.of(), List.of(), Map.of(), Map.of(), false, Instant.EPOCH);
        new BackupArchiveWriter().write(paths.realmDirectory(42).resolve("backup.zip"),
                manifest, metadata, Map.of(), 6);
        return manifest.backupId();
    }
}
