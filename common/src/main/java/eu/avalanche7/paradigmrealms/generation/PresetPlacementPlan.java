package eu.avalanche7.paradigmrealms.generation;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import eu.avalanche7.paradigmrealms.region.BlockVolume;

public record PresetPlacementPlan(
        RealmPresetDefinition preset,
        BlockCoordinate anchor,
        BlockCoordinate origin,
        BlockVolume bounds,
        BlockPosition spawn) {
    public PresetPlacementPlan {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(spawn, "spawn");
        if (!bounds.contains((int) Math.floor(spawn.x()), (int) Math.floor(spawn.y()),
                (int) Math.floor(spawn.z()))) {
            throw new IllegalArgumentException("translated spawn must be inside placement bounds");
        }
    }
}
