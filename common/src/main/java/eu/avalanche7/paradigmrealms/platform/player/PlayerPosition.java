package eu.avalanche7.paradigmrealms.platform.player;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

public record PlayerPosition(DimensionId dimension, BlockPosition position) {
    public PlayerPosition {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
    }
}
