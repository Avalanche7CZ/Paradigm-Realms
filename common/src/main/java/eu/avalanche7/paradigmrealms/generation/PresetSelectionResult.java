package eu.avalanche7.paradigmrealms.generation;

import java.util.Objects;
import java.util.Optional;

public record PresetSelectionResult(
        PresetSelectionStatus status,
        Optional<RealmPresetDefinition> preset,
        String detail) {
    public PresetSelectionResult {
        Objects.requireNonNull(status, "status");
        preset = Objects.requireNonNull(preset, "preset");
        detail = Objects.requireNonNull(detail, "detail");
        if ((status == PresetSelectionStatus.SELECTED) != preset.isPresent()) {
            throw new IllegalArgumentException("only SELECTED results may contain a preset");
        }
    }

    public boolean selected() { return status == PresetSelectionStatus.SELECTED; }

    public static PresetSelectionResult selected(RealmPresetDefinition preset) {
        return new PresetSelectionResult(PresetSelectionStatus.SELECTED, Optional.of(preset), "");
    }

    public static PresetSelectionResult rejected(PresetSelectionStatus status, String detail) {
        return new PresetSelectionResult(status, Optional.empty(), detail);
    }
}
