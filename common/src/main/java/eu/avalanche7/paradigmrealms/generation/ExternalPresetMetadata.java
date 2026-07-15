package eu.avalanche7.paradigmrealms.generation;

import java.util.Objects;
import java.util.Set;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public record ExternalPresetMetadata(
        RealmPresetId id,
        int version,
        String displayName,
        String description,
        RealmPresetId structure,
        PresetRelativeSpawn spawn,
        PresetRelativeBounds bounds,
        Set<RealmPresetId> aliases,
        Set<String> requiredMods,
        boolean selectable,
        boolean legacy) {
    public ExternalPresetMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(bounds, "bounds");
        aliases = Set.copyOf(Objects.requireNonNull(aliases, "aliases"));
        requiredMods = Set.copyOf(Objects.requireNonNull(requiredMods, "requiredMods"));
        if (version < 1) throw new IllegalArgumentException("preset version must be positive");
    }
}
