package eu.avalanche7.paradigmrealms.generation;

import java.util.Objects;
import java.util.Set;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public record PresetSelectionConfig(
        RealmPresetId defaultPreset,
        boolean allowPlayerSelection,
        Set<RealmPresetId> allowedPresets) {
    public PresetSelectionConfig {
        Objects.requireNonNull(defaultPreset, "defaultPreset");
        defaultPreset.requireNamespaced();
        allowedPresets = Set.copyOf(Objects.requireNonNull(allowedPresets, "allowedPresets"));
    }
}
