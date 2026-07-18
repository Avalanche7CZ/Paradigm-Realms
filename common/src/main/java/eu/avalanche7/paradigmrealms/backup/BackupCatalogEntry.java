package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BackupCatalogEntry(BackupId backupId, long realmId, UUID ownerUuid,
        String ownerNameSnapshot, Instant createdAt, BackupReason reason, long sizeBytes,
        String archiveRelativePath, BackupIntegrityStatus integrityStatus, boolean pinned,
        boolean restoreInUse, int formatVersion, String minecraftVersion, String realmsVersion,
        Map<BackupStorageKind, Integer> chunkCounts, String allocationProfile,
        BackupStrategy strategy, int payloadFileCount) {
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
        Objects.requireNonNull(allocationProfile, "allocationProfile");
        Objects.requireNonNull(strategy, "strategy");
        if (payloadFileCount < 0) throw new IllegalArgumentException("payload file count cannot be negative");
        chunkCounts = Map.copyOf(chunkCounts);
    }

    public BackupCatalogEntry(BackupId backupId, long realmId, UUID ownerUuid,
            String ownerNameSnapshot, Instant createdAt, BackupReason reason, long sizeBytes,
            String archiveRelativePath, BackupIntegrityStatus integrityStatus, boolean pinned,
            boolean restoreInUse, int formatVersion, String minecraftVersion, String realmsVersion,
            Map<BackupStorageKind, Integer> chunkCounts) {
        this(backupId, realmId, ownerUuid, ownerNameSnapshot, createdAt, reason, sizeBytes,
                archiveRelativePath, integrityStatus, pinned, restoreInUse, formatVersion, minecraftVersion,
                realmsVersion, chunkCounts, "custom-v1", BackupStrategy.CHUNK_EXTRACT,
                chunkCounts.values().stream().mapToInt(Integer::intValue).sum());
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
                chunkCounts,
                allocationProfile,
                strategy,
                payloadFileCount);
    }
}
