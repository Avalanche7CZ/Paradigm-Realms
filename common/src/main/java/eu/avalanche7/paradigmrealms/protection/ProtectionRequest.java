package eu.avalanche7.paradigmrealms.protection;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;

public record ProtectionRequest(
        Optional<UUID> actingPlayer,
        DimensionId dimension,
        BlockCoordinate target,
        ProtectionAction action,
        boolean adminBypassActive,
        Optional<UUID> responsiblePlayer) {
    public ProtectionRequest {
        Objects.requireNonNull(actingPlayer, "actingPlayer");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(responsiblePlayer, "responsiblePlayer");
    }

    public Optional<UUID> effectiveActor() {
        return responsiblePlayer.or(() -> actingPlayer);
    }
}
