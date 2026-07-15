package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WildsState(
        int schemaVersion,
        long revision,
        WildsLifecycleState lifecycle,
        long activeEpoch,
        long activeSeed,
        Optional<WildsProfileId> activeProfile,
        Optional<WildsSpawn> spawn,
        boolean generationVerified,
        Optional<Instant> activatedAt,
        Optional<Instant> lastSuccessfulResetAt,
        Optional<Instant> nextScheduledReset,
        Optional<WildsResetOperation> operation,
        Map<UUID, Long> approvedPlayerEpochs,
        Optional<WildsFailure> failure) {
    public static final int SCHEMA_VERSION = 1;

    public WildsState {
        if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported Wilds schema");
        if (revision < 0 || activeEpoch < 0) throw new IllegalArgumentException("negative revision or epoch");
        Objects.requireNonNull(lifecycle, "lifecycle");
        Objects.requireNonNull(activeProfile, "activeProfile");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(activatedAt, "activatedAt");
        Objects.requireNonNull(lastSuccessfulResetAt, "lastSuccessfulResetAt");
        Objects.requireNonNull(nextScheduledReset, "nextScheduledReset");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(approvedPlayerEpochs, "approvedPlayerEpochs");
        Objects.requireNonNull(failure, "failure");
        approvedPlayerEpochs = Map.copyOf(approvedPlayerEpochs);
        approvedPlayerEpochs.forEach((uuid, epoch) -> {
            Objects.requireNonNull(uuid, "player UUID");
            if (epoch < 1) throw new IllegalArgumentException("approved player epoch must be positive");
        });
        spawn.ifPresent(value -> {
            if (value.epoch() != activeEpoch) throw new IllegalArgumentException("spawn epoch mismatch");
        });
        if (lifecycle == WildsLifecycleState.ACTIVE
                && (!generationVerified || activeEpoch < 1 || activeProfile.isEmpty() || spawn.isEmpty())) {
            throw new IllegalArgumentException("ACTIVE Wilds must be verified with profile and spawn");
        }
    }

    public static WildsState disabled() {
        return new WildsState(SCHEMA_VERSION, 0, WildsLifecycleState.DISABLED, 0, 0,
                Optional.empty(), Optional.empty(), false, Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Map.of(), Optional.empty());
    }

    public long approvedEpoch(UUID player) { return approvedPlayerEpochs.getOrDefault(player, 0L); }
}
