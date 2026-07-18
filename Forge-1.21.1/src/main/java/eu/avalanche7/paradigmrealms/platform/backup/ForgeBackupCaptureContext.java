package eu.avalanche7.paradigmrealms.platform.backup;

import java.nio.file.Path;

import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupOperation;
import eu.avalanche7.paradigmrealms.backup.RealmMetadataSnapshot;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import net.minecraft.server.world.ServerWorld;

record ForgeBackupCaptureContext(
        Realm realm,
        BackupOperation operation,
        BackupCellBounds bounds,
        String ownerName,
        RealmMetadataSnapshot metadata,
        Path stagingDirectory,
        ServerWorld world,
        RealmBackupMutationLocks.Handle lock) {}
