package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BackupCatalogEntry(BackupId backupId, long realmId, UUID ownerUuid,
        String ownerNameSnapshot, Instant createdAt, BackupReason reason, long sizeBytes,
        String archiveRelativePath, BackupIntegrityStatus integrityStatus, boolean pinned,
        boolean restoreInUse, int formatVersion, String minecraftVersion, String realmsVersion,
        Map<BackupStorageKind, Integer> chunkCounts) {
    public BackupCatalogEntry {
        Objects.requireNonNull(backupId, "backupId");
        if (realmId < 1 || sizeBytes < 0) {
            throw new IllegalArgumentException("invalid catalog numeric value");
        }
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(ownerNameSnapshot, "ownerNameSnapshot");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(archiveRelativePath, "archiveRelativePath");
        if (archiveRelativePath.startsWith("/")
                || archiveRelativePath.contains("..")
                || archiveRelativePath.contains("\\")) {
            throw new IllegalArgumentException("unsafe archive path");
        }
        Objects.requireNonNull(integrityStatus, "integrityStatus");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(realmsVersion, "realmsVersion");
        chunkCounts = Map.copyOf(chunkCounts);
    }

    public BackupCatalogEntry withPinned(boolean value) {
        return copy(integrityStatus, value, restoreInUse);
    }

    public BackupCatalogEntry withIntegrity(BackupIntegrityStatus value) {
        return copy(value, pinned, restoreInUse);
    }

    public BackupCatalogEntry withRestoreInUse(boolean value) {
        return copy(integrityStatus, pinned, value);
    }

    private BackupCatalogEntry copy(
            BackupIntegrityStatus integrity,
            boolean pinnedValue,
            boolean restoreInUseValue) {
        return new BackupCatalogEntry(
                backupId,
                realmId,
                ownerUuid,
                ownerNameSnapshot,
                createdAt,
                reason,
                sizeBytes,
                archiveRelativePath,
                integrity,
                pinnedValue,
                restoreInUseValue,
                formatVersion,
                minecraftVersion,
                realmsVersion,
                chunkCounts);
    }
}
