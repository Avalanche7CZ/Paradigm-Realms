package eu.avalanche7.paradigmrealms.persistence.validation;

import java.util.List;
import java.util.Optional;

public record ConversionResult<T>(Optional<T> value, List<ValidationIssue> issues) {
    public ConversionResult {
        value = value == null ? Optional.empty() : value;
        issues = List.copyOf(issues);
    }

    public static <T> ConversionResult<T> success(T value) {
        return new ConversionResult<>(Optional.of(value), List.of());
    }

    public static <T> ConversionResult<T> failure(ValidationIssue issue) {
        return new ConversionResult<>(Optional.empty(), List.of(issue));
    }
}
