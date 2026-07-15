package eu.avalanche7.paradigmrealms.wilds;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.platform.wilds.WildsActionResult;

public final class WildsResetScheduleCoordinator {
    private final WildsLifecycleService lifecycle;
    private final Supplier<WildsConfig> config;
    private final Clock clock;
    private final WarningSchedule warnings = new WarningSchedule();
    private Instant lastWarningCheck;

    public WildsResetScheduleCoordinator(
            WildsLifecycleService lifecycle, Supplier<WildsConfig> config, Clock clock) {
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.lastWarningCheck = clock.instant();
    }

    public WildsActionResult schedule(Instant when, boolean profileValid) {
        if (!config.get().enabled()) return WildsActionResult.DISABLED;
        if (!profileValid) return WildsActionResult.PROFILE_INVALID;
        try {
            WildsConfig current = config.get();
            lifecycle.schedule(when, current.generationProfile(), current.operationSettings());
            lastWarningCheck = clock.instant();
            return WildsActionResult.SUCCESS;
        } catch (RuntimeException exception) {
            return WildsActionResult.INVALID_STATE;
        }
    }

    public WildsActionResult cancel() {
        try {
            lifecycle.cancel();
            return WildsActionResult.SUCCESS;
        } catch (RuntimeException exception) {
            return WildsActionResult.INVALID_STATE;
        }
    }

    public Tick tick() {
        WildsState state = lifecycle.state();
        if (state.lifecycle() != WildsLifecycleState.RESET_SCHEDULED) return Tick.NONE;
        WildsResetOperation operation = state.operation().orElseThrow();
        Instant now = clock.instant();
        Set<Long> crossed = warnings.crossed(
                operation.scheduledFor(), lastWarningCheck, now,
                config.get().warningTimesSeconds(), operation.emittedWarningsSeconds());
        if (!crossed.isEmpty()) {
            HashSet<Long> emitted = new HashSet<>(operation.emittedWarningsSeconds());
            emitted.addAll(crossed);
            lifecycle.updateOperation(operation.withWarnings(emitted, now));
        }
        lastWarningCheck = now;
        return new Tick(crossed.stream().sorted(java.util.Comparator.reverseOrder()).toList(),
                !now.isBefore(operation.scheduledFor()));
    }

    public void skipElapsedWarnings() {
        WildsResetOperation operation = lifecycle.state().operation().orElseThrow();
        Instant now = clock.instant();
        Set<Long> emitted = warnings.skipElapsed(
                operation.scheduledFor(), now, config.get().warningTimesSeconds(),
                operation.emittedWarningsSeconds());
        lifecycle.updateOperation(operation.withWarnings(emitted, now));
        lastWarningCheck = now;
    }

    public Optional<Instant> nextRecurringReset() {
        WildsConfig current = config.get();
        if (!current.scheduleEnabled()
                || lifecycle.state().lifecycle() != WildsLifecycleState.ACTIVE) {
            return Optional.empty();
        }
        return Optional.of(clock.instant().plus(current.scheduleInterval()));
    }

    public record Tick(List<Long> crossedWarningsSeconds, boolean resetDue) {
        private static final Tick NONE = new Tick(List.of(), false);
        public Tick {
            crossedWarningsSeconds = List.copyOf(crossedWarningsSeconds);
        }
    }
}
