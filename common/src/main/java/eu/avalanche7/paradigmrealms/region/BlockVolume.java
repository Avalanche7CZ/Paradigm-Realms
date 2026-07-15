package eu.avalanche7.paradigmrealms.region;

public record BlockVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public BlockVolume {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("block volume minimum must not exceed maximum");
        }
    }

    public int width() { return Math.addExact(Math.subtractExact(maxX, minX), 1); }
    public int height() { return Math.addExact(Math.subtractExact(maxY, minY), 1); }
    public int depth() { return Math.addExact(Math.subtractExact(maxZ, minZ), 1); }

    public long volume() {
        return Math.multiplyExact(Math.multiplyExact((long) width(), height()), depth());
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
