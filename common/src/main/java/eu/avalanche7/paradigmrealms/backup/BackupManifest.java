package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BackupManifest(
        int formatVersion, BackupId backupId, Instant createdAt, BackupActor createdBy,
        BackupReason reason, String minecraftVersion, String realmsVersion, String dimension,
        String worldIdentity, long realmId, UUID ownerUuid, String ownerNameSnapshot,
        String preset, String lifecycleState, BackupCellBounds cellBounds,
        Map<BackupStorageKind, List<ChunkCoordinate>> chunks, String metadataMode, boolean pinned) {
    public static final int CURRENT_FORMAT_VERSION = 1;

    public BackupManifest {
        if (formatVersion != CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported backup format version " + formatVersion);
        }
        Objects.requireNonNull(backupId, "backupId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(reason, "reason");
        minecraftVersion = nonBlank(minecraftVersion, "minecraftVersion");
        realmsVersion = nonBlank(realmsVersion, "realmsVersion");
        dimension = nonBlank(dimension, "dimension");
        worldIdentity = nonBlank(worldIdentity, "worldIdentity");
        if (realmId < 1) throw new IllegalArgumentException("realm ID must be positive");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        ownerNameSnapshot = Objects.requireNonNullElse(ownerNameSnapshot, "UnknownOwner");
        preset = nonBlank(preset, "preset");
        lifecycleState = nonBlank(lifecycleState, "lifecycleState");
        Objects.requireNonNull(cellBounds, "cellBounds");
        metadataMode = nonBlank(metadataMode, "metadataMode");
        EnumMap<BackupStorageKind, List<ChunkCoordinate>> copy = new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            List<ChunkCoordinate> values = List.copyOf(chunks.getOrDefault(kind, List.of()));
            if (values.size() > 256 || values.stream().distinct().count() != values.size()) {
                throw new IllegalArgumentException("duplicate or excessive " + kind + " chunk coordinates");
            }
            if (values.stream().anyMatch(coordinate -> !cellBounds.contains(coordinate))) {
                throw new IllegalArgumentException("out-of-cell " + kind + " chunk coordinate");
            }
            copy.put(kind, values.stream().sorted().toList());
        }
        chunks = Map.copyOf(copy);
    }

    public int chunkCount(BackupStorageKind kind) { return chunks.get(kind).size(); }
    public int totalChunkCount() { return chunks.values().stream().mapToInt(List::size).sum(); }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException("invalid " + name);
        return value;
    }
}
