package eu.avalanche7.paradigmrealms.persistence.validation;

import java.util.Collection;
import java.util.List;

public record ValidationReport(List<ValidationIssue> issues) {
    public ValidationReport {
        issues = List.copyOf(issues);
    }

    public static ValidationReport valid() {
        return new ValidationReport(List.of());
    }

    public boolean isValid() {
        return issues.stream().noneMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    public ValidationReport plus(Collection<ValidationIssue> additional) {
        java.util.ArrayList<ValidationIssue> combined = new java.util.ArrayList<>(issues);
        combined.addAll(additional);
        return new ValidationReport(combined);
    }
}
