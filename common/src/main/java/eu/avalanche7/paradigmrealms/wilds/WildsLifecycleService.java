package eu.avalanche7.paradigmrealms.wilds;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class WildsLifecycleService {
    private final WildsStateStore store;
    private final Clock clock;
    private final Supplier<UUID> operationIds;
    private final LongSupplier seeds;

    public WildsLifecycleService(WildsStateStore store, Clock clock, Supplier<UUID> operationIds, LongSupplier seeds) {
        this.store = java.util.Objects.requireNonNull(store, "store");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.operationIds = java.util.Objects.requireNonNull(operationIds, "operationIds");
        this.seeds = java.util.Objects.requireNonNull(seeds, "seeds");
    }

    public WildsState state() { return store.load(); }

    public synchronized WildsState activateInitial(long seed, WildsProfileId profile, WildsSpawn spawn) {
        WildsState current = state();
        if (current.lifecycle() == WildsLifecycleState.ACTIVE) return current;
        require(current, WildsLifecycleState.DISABLED, WildsLifecycleState.ACTIVE);
        Instant now = clock.instant();
        return persist(new WildsState(1, current.revision() + 1, WildsLifecycleState.ACTIVE,
                spawn.epoch(), seed, Optional.of(profile), Optional.of(spawn), true,
                Optional.of(now), current.lastSuccessfulResetAt(), current.nextScheduledReset(),
                Optional.empty(), current.approvedPlayerEpochs(), Optional.empty()));
    }

    public synchronized WildsState schedule(
            Instant when, WildsProfileId targetProfile, WildsOperationSettings settings) {
        WildsState current = state();
        if (current.lifecycle() == WildsLifecycleState.RESET_SCHEDULED) return current;
        require(current, WildsLifecycleState.ACTIVE, WildsLifecycleState.RESET_SCHEDULED);
        Instant now = clock.instant();
        if (when.isBefore(now)) when = now;
        long targetSeed = settings.rotateSeed() ? seeds.getAsLong() : current.activeSeed();
        WildsResetOperation operation = new WildsResetOperation(
                operationIds.get(), current.activeEpoch(), Math.addExact(current.activeEpoch(), 1),
                current.activeSeed(), targetSeed, current.activeProfile().orElseThrow(), targetProfile,
                now, when, now, Set.of(), settings);
        return persist(copy(current, WildsLifecycleState.RESET_SCHEDULED,
                Optional.of(operation), Optional.empty(), Optional.of(when), true));
    }

    public synchronized WildsState cancel() {
        WildsState current = state();
        require(current, WildsLifecycleState.RESET_SCHEDULED, WildsLifecycleState.ACTIVE);
        return persist(copy(current, WildsLifecycleState.ACTIVE,
                Optional.empty(), Optional.empty(), Optional.empty(), true));
    }

    public synchronized WildsState closeEntry() {
        WildsState current = state();
        if (current.lifecycle() == WildsLifecycleState.ENTRY_CLOSED) return current;
        require(current, WildsLifecycleState.ACTIVE, WildsLifecycleState.ENTRY_CLOSED);
        return persist(copy(current, WildsLifecycleState.ENTRY_CLOSED,
                current.operation(), current.failure(), current.nextScheduledReset(), current.generationVerified()));
    }

    public synchronized WildsState openEntry() {
        WildsState current = state();
        require(current, WildsLifecycleState.ENTRY_CLOSED, WildsLifecycleState.ACTIVE);
        if (!current.generationVerified()) throw new IllegalStateException("generation is not verified");
        return persist(copy(current, WildsLifecycleState.ACTIVE,
                current.operation(), Optional.empty(), current.nextScheduledReset(), true));
    }

    public synchronized WildsState blockEntry() {
        WildsState current = state();
        if (current.lifecycle() == WildsLifecycleState.ENTRY_BLOCKED) return current;
        require(current, WildsLifecycleState.RESET_SCHEDULED, WildsLifecycleState.ENTRY_BLOCKED);
        return persist(copy(current, WildsLifecycleState.ENTRY_BLOCKED,
                current.operation(), Optional.empty(), current.nextScheduledReset(), false));
    }

    public synchronized WildsState beginEvacuation() { return transition(WildsLifecycleState.ENTRY_BLOCKED, WildsLifecycleState.EVACUATING); }
    public synchronized WildsState beginSaveBarrier() { return transition(WildsLifecycleState.EVACUATING, WildsLifecycleState.SAVE_BARRIER); }
    public synchronized WildsState markOfflinePending() { return transition(WildsLifecycleState.SAVE_BARRIER, WildsLifecycleState.OFFLINE_RESET_PENDING); }

    public synchronized WildsState beginVerification() {
        WildsState current = state();
        if (current.lifecycle() == WildsLifecycleState.VERIFYING) return current;
        if (current.lifecycle() != WildsLifecycleState.OFFLINE_RESET_PENDING
                && current.lifecycle() != WildsLifecycleState.FAILED) {
            throw new InvalidWildsTransitionException(current.lifecycle(), WildsLifecycleState.VERIFYING);
        }
        if (current.operation().isEmpty()) throw new IllegalStateException("missing reset operation");
        return persist(copy(current, WildsLifecycleState.VERIFYING,
                current.operation(), Optional.empty(), current.nextScheduledReset(), false));
    }

    public synchronized WildsState completeVerification(WildsSpawn spawn) {
        WildsState current = state();
        require(current, WildsLifecycleState.VERIFYING, WildsLifecycleState.ACTIVE);
        WildsResetOperation operation = current.operation().orElseThrow();
        if (spawn.epoch() != operation.targetEpoch()) throw new IllegalArgumentException("target spawn epoch mismatch");
        Instant now = clock.instant();
        return persist(new WildsState(1, current.revision() + 1, WildsLifecycleState.ACTIVE,
                operation.targetEpoch(), operation.targetSeed(), Optional.of(operation.targetProfile()),
                Optional.of(spawn), true, Optional.of(now), Optional.of(now), Optional.empty(),
                Optional.empty(), current.approvedPlayerEpochs(), Optional.empty()));
    }

    public synchronized WildsState completeRollback(WildsSpawn sourceSpawn) {
        WildsState current = state();
        if (current.lifecycle() != WildsLifecycleState.FAILED
                && current.lifecycle() != WildsLifecycleState.OFFLINE_RESET_PENDING
                && current.lifecycle() != WildsLifecycleState.VERIFYING) {
            throw new InvalidWildsTransitionException(current.lifecycle(), WildsLifecycleState.ACTIVE);
        }
        WildsResetOperation operation = current.operation().orElseThrow();
        if (current.activeEpoch() != operation.sourceEpoch()
                || current.activeSeed() != operation.sourceSeed()
                || !current.activeProfile().orElseThrow().equals(operation.sourceProfile())) {
            throw new IllegalStateException("persisted source generation does not match reset operation");
        }
        if (sourceSpawn.epoch() != operation.sourceEpoch()) {
            throw new IllegalArgumentException("source spawn epoch mismatch");
        }
        return persist(new WildsState(1, current.revision() + 1, WildsLifecycleState.ACTIVE,
                operation.sourceEpoch(), operation.sourceSeed(), Optional.of(operation.sourceProfile()),
                Optional.of(sourceSpawn), true, current.activatedAt(), current.lastSuccessfulResetAt(),
                Optional.empty(), Optional.empty(), current.approvedPlayerEpochs(), Optional.empty()));
    }

    public synchronized WildsState fail(String code, String detail) {
        WildsState current = state();
        if (current.lifecycle() == WildsLifecycleState.DISABLED) {
            throw new InvalidWildsTransitionException(current.lifecycle(), WildsLifecycleState.FAILED);
        }
        return persist(copy(current, WildsLifecycleState.FAILED, current.operation(),
                Optional.of(new WildsFailure(code, detail, clock.instant())),
                current.nextScheduledReset(), false));
    }

    public synchronized WildsState approvePlayer(UUID player) {
        WildsState current = state();
        if (!current.lifecycle().entryOpen() || !current.generationVerified()) {
            throw new IllegalStateException("Wilds entry is closed");
        }
        if (current.approvedEpoch(player) == current.activeEpoch()) return current;
        HashMap<UUID, Long> approvals = new HashMap<>(current.approvedPlayerEpochs());
        approvals.put(player, current.activeEpoch());
        return persist(new WildsState(1, current.revision() + 1, current.lifecycle(), current.activeEpoch(),
                current.activeSeed(), current.activeProfile(), current.spawn(), current.generationVerified(),
                current.activatedAt(), current.lastSuccessfulResetAt(), current.nextScheduledReset(),
                current.operation(), approvals, current.failure()));
    }

    public synchronized WildsState updateOperation(WildsResetOperation operation) {
        WildsState current = state();
        if (current.operation().isEmpty()
                || !current.operation().orElseThrow().operationId().equals(operation.operationId())) {
            throw new IllegalArgumentException("operation identity mismatch");
        }
        return persist(new WildsState(1, current.revision() + 1, current.lifecycle(), current.activeEpoch(),
                current.activeSeed(), current.activeProfile(), current.spawn(), current.generationVerified(),
                current.activatedAt(), current.lastSuccessfulResetAt(), current.nextScheduledReset(),
                Optional.of(operation), current.approvedPlayerEpochs(), current.failure()));
    }

    private WildsState transition(WildsLifecycleState from, WildsLifecycleState to) {
        WildsState current = state();
        if (current.lifecycle() == to) return current;
        require(current, from, to);
        return persist(copy(current, to, current.operation(), Optional.empty(), current.nextScheduledReset(), false));
    }

    private WildsState persist(WildsState replacement) { store.save(replacement); return replacement; }

    private static void require(WildsState current, WildsLifecycleState expected, WildsLifecycleState target) {
        if (current.lifecycle() != expected) throw new InvalidWildsTransitionException(current.lifecycle(), target);
    }

    private static WildsState copy(
            WildsState current, WildsLifecycleState lifecycle, Optional<WildsResetOperation> operation,
            Optional<WildsFailure> failure, Optional<Instant> nextReset, boolean verified) {
        return new WildsState(1, current.revision() + 1, lifecycle, current.activeEpoch(), current.activeSeed(),
                current.activeProfile(), current.spawn(), verified, current.activatedAt(),
                current.lastSuccessfulResetAt(), nextReset, operation,
                current.approvedPlayerEpochs(), failure);
    }
}
