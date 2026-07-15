package eu.avalanche7.paradigmrealms.platform.teleport;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

public record TeleportRequest(UUID player, DimensionId dimension, BlockPosition destination) {
    public TeleportRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(destination, "destination");
    }
}
