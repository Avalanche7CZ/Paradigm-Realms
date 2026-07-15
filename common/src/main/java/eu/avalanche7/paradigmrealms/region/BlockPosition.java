package eu.avalanche7.paradigmrealms.region;

public record BlockPosition(double x, double y, double z, float yaw, float pitch) {
    public BlockPosition {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("position and rotation values must be finite");
        }
    }

    public ChunkCoordinate chunk() {
        return CoordinateMath.chunkContaining(x, z);
    }
}
