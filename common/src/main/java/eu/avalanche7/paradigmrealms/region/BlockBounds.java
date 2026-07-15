package eu.avalanche7.paradigmrealms.region;

public record BlockBounds(int minX, int minZ, int maxX, int maxZ) {
    public BlockBounds {
        if (minX > maxX || minZ > maxZ) {
            throw new IllegalArgumentException("minimum block bounds must not exceed maximum bounds");
        }
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public int width() {
        return Math.toIntExact(Math.addExact(Math.subtractExact((long) maxX, minX), 1L));
    }

    public int depth() {
        return Math.toIntExact(Math.addExact(Math.subtractExact((long) maxZ, minZ), 1L));
    }
}
