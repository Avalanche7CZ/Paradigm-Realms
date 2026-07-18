package eu.avalanche7.paradigmrealms.backup;

import java.util.ArrayList;
import java.util.List;

import eu.avalanche7.paradigmrealms.region.ChunkBounds;

public record BackupCellBounds(int minimumChunkX, int minimumChunkZ,
        int maximumChunkX, int maximumChunkZ) {
    public BackupCellBounds {
        if (maximumChunkX < minimumChunkX || maximumChunkZ < minimumChunkZ) {
            throw new IllegalArgumentException("invalid cell bounds");
        }
        int width = Math.addExact(Math.subtractExact(maximumChunkX, minimumChunkX), 1);
        int depth = Math.addExact(Math.subtractExact(maximumChunkZ, minimumChunkZ), 1);
        if (width > 32 || depth > 32) {
            throw new IllegalArgumentException("realm backup cell must not exceed 32x32 chunks");
        }
    }

    public static BackupCellBounds from(ChunkBounds bounds) {
        return new BackupCellBounds(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ());
    }

    public boolean contains(ChunkCoordinate coordinate) {
        return coordinate.x() >= minimumChunkX && coordinate.x() <= maximumChunkX
                && coordinate.z() >= minimumChunkZ && coordinate.z() <= maximumChunkZ;
    }

    public List<ChunkCoordinate> coordinates() {
        ArrayList<ChunkCoordinate> result = new ArrayList<>();
        for (int x = minimumChunkX; x <= maximumChunkX; x++) {
            for (int z = minimumChunkZ; z <= maximumChunkZ; z++) {
                result.add(new ChunkCoordinate(x, z));
            }
        }
        return List.copyOf(result);
    }
}
