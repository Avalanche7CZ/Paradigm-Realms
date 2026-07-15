package eu.avalanche7.paradigmrealms.generation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public final class PresetSelectionService {
    public PresetSelectionResult select(
            RealmPresetCatalog catalog,
            PresetSelectionConfig config,
            Optional<RealmPresetId> requestedByPlayer) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(requestedByPlayer, "requestedByPlayer");
        if (requestedByPlayer.isPresent() && !config.allowPlayerSelection()) {
            return PresetSelectionResult.rejected(PresetSelectionStatus.PLAYER_SELECTION_DISABLED,
                    "player preset selection is disabled");
        }
        RealmPresetId requested = requestedByPlayer.orElse(config.defaultPreset());
        RealmPresetDefinition definition = catalog.resolve(requested).orElse(null);
        if (definition == null) {
            return PresetSelectionResult.rejected(requestedByPlayer.isPresent()
                            ? PresetSelectionStatus.UNKNOWN_PRESET
                            : PresetSelectionStatus.DEFAULT_PRESET_UNAVAILABLE,
                    "preset is not present in the validated catalog: " + requested.value());
        }
        if (!definition.enabled()) {
            return PresetSelectionResult.rejected(PresetSelectionStatus.PRESET_DISABLED,
                    String.join("; ", definition.disableReasons()));
        }
        if (!definition.playerSelectable()) {
            return PresetSelectionResult.rejected(PresetSelectionStatus.PRESET_NOT_SELECTABLE,
                    "preset is legacy or not player-selectable");
        }
        if (!isAllowed(catalog, config, definition)) {
            return PresetSelectionResult.rejected(PresetSelectionStatus.PRESET_NOT_ALLOWED,
                    "preset is not in the server allowlist");
        }
        return PresetSelectionResult.selected(definition);
    }

    public List<RealmPresetDefinition> selectable(
            RealmPresetCatalog catalog,
            PresetSelectionConfig config) {
        return catalog.all().stream()
                .filter(RealmPresetDefinition::playerSelectable)
                .filter(definition -> isAllowed(catalog, config, definition))
                .sorted(Comparator.comparing(definition -> definition.id().value()))
                .toList();
    }

    private static boolean isAllowed(
            RealmPresetCatalog catalog,
            PresetSelectionConfig config,
            RealmPresetDefinition definition) {
        return config.allowedPresets().stream()
                .map(catalog::resolve)
                .flatMap(Optional::stream)
                .anyMatch(allowed -> allowed.id().equals(definition.id()));
    }
}
