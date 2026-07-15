package eu.avalanche7.paradigmrealms.persistence.validation;

import java.util.Objects;

public record ValidationIssue(String code, String path, String message, ValidationSeverity severity) {
    public ValidationIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(severity, "severity");
    }

    public static ValidationIssue error(String code, String path, String message) {
        return new ValidationIssue(code, path, message, ValidationSeverity.ERROR);
    }

    public static ValidationIssue warning(String code, String path, String message) {
        return new ValidationIssue(code, path, message, ValidationSeverity.WARNING);
    }
}
