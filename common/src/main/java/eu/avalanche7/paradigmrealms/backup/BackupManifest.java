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
        String preset, String lifecycleState, String allocationProfile, BackupCellBounds cellBounds,
        BackupStrategy strategy, Map<BackupStorageKind, List<ChunkCoordinate>> chunks,
        List<String> regionFiles, String metadataMode, boolean pinned) {
    public static final int CURRENT_FORMAT_VERSION = 2;

    public BackupManifest {
        if (formatVersion < 1 || formatVersion > CURRENT_FORMAT_VERSION) {
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
        allocationProfile = nonBlank(allocationProfile, "allocationProfile");
        Objects.requireNonNull(cellBounds, "cellBounds");
        Objects.requireNonNull(strategy, "strategy");
        regionFiles = List.copyOf(Objects.requireNonNull(regionFiles, "regionFiles"));
        metadataMode = nonBlank(metadataMode, "metadataMode");
        EnumMap<BackupStorageKind, List<ChunkCoordinate>> copy = new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            List<ChunkCoordinate> values = List.copyOf(chunks.getOrDefault(kind, List.of()));
            if (values.size() > 1_024 || values.stream().distinct().count() != values.size()) {
                throw new IllegalArgumentException("duplicate or excessive " + kind + " chunk coordinates");
            }
            if (values.stream().anyMatch(coordinate -> !cellBounds.contains(coordinate))) {
                throw new IllegalArgumentException("out-of-cell " + kind + " chunk coordinate");
            }
            copy.put(kind, values.stream().sorted().toList());
        }
        chunks = Map.copyOf(copy);
        if (strategy == BackupStrategy.REGION_COPY) {
            if (!"region-aligned-32-v1".equals(allocationProfile)
                    || cellBounds.maximumChunkX() - cellBounds.minimumChunkX() + 1 != 32
                    || cellBounds.maximumChunkZ() - cellBounds.minimumChunkZ() + 1 != 32
                    || Math.floorMod(cellBounds.minimumChunkX(), 32) != 0
                    || Math.floorMod(cellBounds.minimumChunkZ(), 32) != 0
                    || chunks.values().stream().anyMatch(values -> !values.isEmpty())) {
                throw new IllegalArgumentException("REGION_COPY requires an exact region-aligned allocation");
            }
        } else if (!regionFiles.isEmpty()) {
            throw new IllegalArgumentException("CHUNK_EXTRACT must not contain raw region files");
        }
        if (regionFiles.stream().distinct().count() != regionFiles.size()) {
            throw new IllegalArgumentException("duplicate raw region file");
        }
        regionFiles.forEach(name -> validateRegionFileName(name, cellBounds));
    }

    public BackupManifest(
            int formatVersion, BackupId backupId, Instant createdAt, BackupActor createdBy,
            BackupReason reason, String minecraftVersion, String realmsVersion, String dimension,
            String worldIdentity, long realmId, UUID ownerUuid, String ownerNameSnapshot,
            String preset, String lifecycleState, BackupCellBounds cellBounds,
            Map<BackupStorageKind, List<ChunkCoordinate>> chunks, String metadataMode, boolean pinned) {
        this(formatVersion, backupId, createdAt, createdBy, reason, minecraftVersion, realmsVersion,
                dimension, worldIdentity, realmId, ownerUuid, ownerNameSnapshot, preset, lifecycleState,
                "custom-v1", cellBounds, BackupStrategy.CHUNK_EXTRACT, chunks, List.of(), metadataMode, pinned);
    }

    public int chunkCount(BackupStorageKind kind) { return chunks.get(kind).size(); }
    public int totalChunkCount() { return chunks.values().stream().mapToInt(List::size).sum(); }

    private static void validateRegionFileName(String name, BackupCellBounds bounds) {
        if (!name.matches("(region|entities|poi)/(r\\.-?[0-9]+\\.-?[0-9]+\\.mca|c\\.-?[0-9]+\\.-?[0-9]+\\.mcc)")) {
            throw new IllegalArgumentException("invalid raw region file " + name);
        }
        String filename = name.substring(name.indexOf('/') + 1);
        String[] parts = filename.split("\\.");
        int x = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        if (filename.startsWith("r.")) {
            if (x != Math.floorDiv(bounds.minimumChunkX(), 32)
                    || z != Math.floorDiv(bounds.minimumChunkZ(), 32)) {
                throw new IllegalArgumentException("raw region file belongs to another region");
            }
        } else if (!bounds.contains(new ChunkCoordinate(x, z))) {
            throw new IllegalArgumentException("external chunk payload belongs to another region");
        }
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > 256) throw new IllegalArgumentException("invalid " + name);
        return value;
    }
}
