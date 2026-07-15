package eu.avalanche7.paradigmrealms.region;

public record ChunkBounds(int minX, int minZ, int maxX, int maxZ) {
    public ChunkBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("minimum chunk bounds must not exceed maximum bounds");
        }
    }

    public int width() {
        return Math.toIntExact(Math.addExact(Math.subtractExact((long) maxX, minX), 1L));
    }

    public int depth() {
        return Math.toIntExact(Math.addExact(Math.subtractExact((long) maxZ, minZ), 1L));
    }

    public boolean contains(ChunkCoordinate coordinate) {
        return coordinate.x() >= minX && coordinate.x() <= maxX
                && coordinate.z() >= minZ && coordinate.z() <= maxZ;
    }

    public boolean contains(ChunkBounds other) {
        return other.minX >= minX && other.maxX <= maxX
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public boolean overlaps(ChunkBounds other) {
        return minX <= other.maxX && maxX >= other.minX
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
