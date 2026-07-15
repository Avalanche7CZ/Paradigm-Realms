package eu.avalanche7.paradigmrealms.persistence;

import java.util.List;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;

public record StateLoadResult(RealmStateDtoV1 state, List<ValidationIssue> issues, boolean writable) {
    public StateLoadResult {
        Objects.requireNonNull(state, "state");
        issues = List.copyOf(issues);
    }
}
