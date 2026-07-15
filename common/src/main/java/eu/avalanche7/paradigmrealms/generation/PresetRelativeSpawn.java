package eu.avalanche7.paradigmrealms.generation;

public record PresetRelativeSpawn(double x, double y, double z, float yaw, float pitch) {
    public PresetRelativeSpawn {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("relative spawn values must be finite");
        }
        if (pitch < -90 || pitch > 90) {
            throw new IllegalArgumentException("relative spawn pitch must be between -90 and 90");
        }
    }
}
