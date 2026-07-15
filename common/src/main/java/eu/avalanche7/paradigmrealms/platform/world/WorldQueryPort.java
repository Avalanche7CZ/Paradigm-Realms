package eu.avalanche7.paradigmrealms.platform.world;

import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

public interface WorldQueryPort {
    boolean available(DimensionId dimension);
    boolean insideWorldBorder(DimensionId dimension, BlockCoordinate position);
    boolean safeStanding(DimensionId dimension, BlockCoordinate feet, StandingSafety safety);
    Optional<BlockPosition> spawnPosition(DimensionId dimension, UUID player);
}
