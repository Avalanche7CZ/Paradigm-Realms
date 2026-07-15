package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.generation.PresetRelativeBounds;

public record StructurallyValidatedTemplate(
        NormalizedImportedTemplate template,
        PresetRelativeBounds centeredSourceBounds,
        int nonAirBlockCount) {
    public StructurallyValidatedTemplate {
        Objects.requireNonNull(template, "template"); Objects.requireNonNull(centeredSourceBounds, "centeredSourceBounds");
        if (nonAirBlockCount < 1) throw new IllegalArgumentException("template must contain a non-air block");
    }
}
