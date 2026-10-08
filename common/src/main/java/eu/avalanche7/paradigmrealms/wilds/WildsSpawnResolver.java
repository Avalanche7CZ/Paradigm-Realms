package eu.avalanche7.paradigmrealms.wilds;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.platform.wilds.WildsSpawnPort;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;

public final class WildsSpawnResolver {
    private static final int[] RADII = {16, 64, 256, 1024};

    public WildsSpawn resolve(long epoch, BlockCoordinate origin, WildsSpawnPort port) {
        if (epoch < 1) throw new IllegalArgumentException("spawn epoch must be positive");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(port, "port");
        var center = port.findSafeSurface(origin.x(), origin.z());
        if (center.isPresent()) return spawn(epoch, center.orElseThrow());
        for (int radius : RADII) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    var candidate = port.findSafeSurface(origin.x() + dx * radius, origin.z() + dz * radius);
                    if (candidate.isPresent()) return spawn(epoch, candidate.orElseThrow());
                }
            }
        }
        throw new IllegalStateException("No safe natural Wilds spawn found within the bounded search");
    }

    private static WildsSpawn spawn(long epoch, BlockCoordinate feet) {
        return new WildsSpawn(epoch, feet.x() + 0.5, feet.y(), feet.z() + 0.5, 0, 0);
    }
}
