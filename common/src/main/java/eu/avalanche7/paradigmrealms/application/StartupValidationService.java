package eu.avalanche7.paradigmrealms.application;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationReport;

public final class StartupValidationService {
    private final RealmRepository repository;

    public StartupValidationService(RealmRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public ValidationReport validate(Set<DimensionId> loadedDimensions) {
        Objects.requireNonNull(loadedDimensions, "loadedDimensions");
        ArrayList<ValidationIssue> issues = new ArrayList<>(repository.validate().issues());
        requireDimension(loadedDimensions, DimensionId.REALMS, issues);
        requireDimension(loadedDimensions, DimensionId.WILDS, issues);
        requireDimension(loadedDimensions, DimensionId.OVERWORLD, issues);
        return new ValidationReport(issues);
    }

    private static void requireDimension(
            Set<DimensionId> loaded, DimensionId required, ArrayList<ValidationIssue> issues) {
        if (!loaded.contains(required)) {
            issues.add(ValidationIssue.error("MISSING_DIMENSION", "dimensions." + required,
                    "required static dimension is not loaded"));
        }
    }
}
