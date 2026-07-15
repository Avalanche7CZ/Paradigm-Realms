package eu.avalanche7.paradigmrealms.wilds;

public record WildsSpawn(long epoch, double x, double y, double z, float yaw, float pitch) {
    public WildsSpawn {
        if (epoch < 1) throw new IllegalArgumentException("spawn epoch must be positive");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("spawn coordinates must be finite");
        }
    }
}
