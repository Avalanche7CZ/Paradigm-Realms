package eu.avalanche7.paradigmrealms.operations;

import java.util.List;

public record ConfigCommandResult(
        boolean valid,
        List<String> applied,
        List<String> deferred,
        List<String> restartRequired,
        List<String> rejected) {
    public ConfigCommandResult {
        applied = List.copyOf(applied);
        deferred = List.copyOf(deferred);
        restartRequired = List.copyOf(restartRequired);
        rejected = List.copyOf(rejected);
    }
}
