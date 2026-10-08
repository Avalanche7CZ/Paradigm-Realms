package eu.avalanche7.paradigmrealms.platform.wilds;

import java.util.Optional;

import eu.avalanche7.paradigmrealms.region.BlockCoordinate;

public interface WildsSpawnPort {
    Optional<BlockCoordinate> findSafeSurface(int x, int z);
}
