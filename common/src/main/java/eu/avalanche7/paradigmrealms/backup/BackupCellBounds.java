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
        if (maximumChunkX - minimumChunkX + 1 != 16
                || maximumChunkZ - minimumChunkZ + 1 != 16) {
            throw new IllegalArgumentException("realm backup cell must be exactly 16x16 chunks");
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
        ArrayList<ChunkCoordinate> result = new ArrayList<>(256);
        for (int x = minimumChunkX; x <= maximumChunkX; x++) {
            for (int z = minimumChunkZ; z <= maximumChunkZ; z++) {
                result.add(new ChunkCoordinate(x, z));
            }
        }
        return List.copyOf(result);
    }
}
