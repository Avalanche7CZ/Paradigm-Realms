package eu.avalanche7.paradigmrealms.generation;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public record PresetCatalogSnapshot(
        RealmPresetCatalog catalog,
        PresetSelectionConfig selection,
        boolean allowExternalPresets,
        Set<RealmPresetId> importedPresetIds,
        Instant loadedAt) {
    public PresetCatalogSnapshot {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(importedPresetIds, "importedPresetIds");
        Objects.requireNonNull(loadedAt, "loadedAt");
        importedPresetIds = Set.copyOf(importedPresetIds);
    }
}
