package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.generation.PresetRelativeBounds;

public record PresetImportResult(
        PresetImportStatus status,
        Optional<RealmPresetId> presetId,
        String sourceFile,
        Optional<ImportedTemplateFormat> format,
        Optional<String> fingerprint,
        Optional<PresetRelativeBounds> bounds,
        int blockCount,
        int sanitizedBlockEntityCount,
        List<String> warnings,
        List<String> errors) {
    public PresetImportResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(warnings, "warnings");
        Objects.requireNonNull(errors, "errors");
        if (sourceFile.isBlank()) throw new IllegalArgumentException("source file cannot be blank");
        if (blockCount < 0 || sanitizedBlockEntityCount < 0) {
            throw new IllegalArgumentException("import counts cannot be negative");
        }
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
        if ((status == PresetImportStatus.VALID || status == PresetImportStatus.PUBLISHED)
                && (!errors.isEmpty() || format.isEmpty() || fingerprint.isEmpty() || bounds.isEmpty())) {
            throw new IllegalArgumentException("successful import result requires validated metadata");
        }
    }

    public boolean successful() {
        return status == PresetImportStatus.VALID || status == PresetImportStatus.PUBLISHED
                || status == PresetImportStatus.REMOVED;
    }
}
