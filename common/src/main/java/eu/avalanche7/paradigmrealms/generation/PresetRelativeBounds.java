package eu.avalanche7.paradigmrealms.generation;

import eu.avalanche7.paradigmrealms.region.BlockVolume;

public record PresetRelativeBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public PresetRelativeBounds {
        new BlockVolume(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int width() { return Math.addExact(Math.subtractExact(maxX, minX), 1); }
    public int height() { return Math.addExact(Math.subtractExact(maxY, minY), 1); }
    public int depth() { return Math.addExact(Math.subtractExact(maxZ, minZ), 1); }

    public boolean contains(PresetRelativeSpawn spawn) {
        return containsFloor(spawn.x(), minX, maxX)
                && containsFloor(spawn.y(), minY, maxY)
                && containsFloor(spawn.z(), minZ, maxZ);
    }

    private static boolean containsFloor(double value, int minimum, int maximum) {
        double floor = Math.floor(value);
        return floor >= minimum && floor <= maximum;
    }
}
