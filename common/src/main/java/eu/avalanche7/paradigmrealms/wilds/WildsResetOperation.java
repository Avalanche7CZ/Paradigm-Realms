package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record WildsResetOperation(
        UUID operationId,
        long sourceEpoch,
        long targetEpoch,
        long sourceSeed,
        long targetSeed,
        WildsProfileId sourceProfile,
        WildsProfileId targetProfile,
        Instant createdAt,
        Instant scheduledFor,
        Instant lastWarningCheck,
        Set<Long> emittedWarningsSeconds,
        WildsOperationSettings settings) {
    public WildsResetOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(sourceProfile, "sourceProfile");
        Objects.requireNonNull(targetProfile, "targetProfile");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(scheduledFor, "scheduledFor");
        Objects.requireNonNull(lastWarningCheck, "lastWarningCheck");
        Objects.requireNonNull(emittedWarningsSeconds, "emittedWarningsSeconds");
        Objects.requireNonNull(settings, "settings");
        emittedWarningsSeconds = Set.copyOf(emittedWarningsSeconds);
        if (sourceEpoch < 1 || targetEpoch != Math.addExact(sourceEpoch, 1)) {
            throw new IllegalArgumentException("target epoch must be source epoch + 1");
        }
        if (scheduledFor.isBefore(createdAt)) throw new IllegalArgumentException("schedule precedes creation");
    }

    public WildsResetOperation withWarnings(Set<Long> emitted, Instant checkedAt) {
        return new WildsResetOperation(operationId, sourceEpoch, targetEpoch, sourceSeed, targetSeed,
                sourceProfile, targetProfile, createdAt, scheduledFor, checkedAt, emitted, settings);
    }
}
