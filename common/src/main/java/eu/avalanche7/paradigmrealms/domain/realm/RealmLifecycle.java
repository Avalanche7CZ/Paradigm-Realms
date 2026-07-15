package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RealmLifecycle {
    private static final Map<RealmLifecycleState, Set<RealmLifecycleState>> ALLOWED = allowedTransitions();

    public Realm transition(Realm realm, RealmLifecycleState target, Optional<RealmFailure> failure) {
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(failure, "failure");
        if (!canTransition(realm.state(), target)) {
            throw new InvalidLifecycleTransitionException(realm.state(), target);
        }
        if (target == RealmLifecycleState.FAILED && failure.isEmpty()) {
            throw new IllegalArgumentException("transition to FAILED requires failure metadata");
        }
        if (target != RealmLifecycleState.FAILED && failure.isPresent()) {
            throw new IllegalArgumentException("failure metadata is only accepted when entering FAILED");
        }
        return realm.withLifecycle(target, failure);
    }

    public boolean canTransition(RealmLifecycleState from, RealmLifecycleState to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return ALLOWED.get(from).contains(to);
    }

    private static Map<RealmLifecycleState, Set<RealmLifecycleState>> allowedTransitions() {
        EnumMap<RealmLifecycleState, Set<RealmLifecycleState>> transitions =
                new EnumMap<>(RealmLifecycleState.class);
        transitions.put(RealmLifecycleState.ALLOCATED,
                EnumSet.of(RealmLifecycleState.GENERATING, RealmLifecycleState.FAILED));
        transitions.put(RealmLifecycleState.GENERATING,
                EnumSet.of(RealmLifecycleState.READY, RealmLifecycleState.ACTIVE, RealmLifecycleState.FAILED));
        transitions.put(RealmLifecycleState.READY,
                EnumSet.of(RealmLifecycleState.ACTIVE, RealmLifecycleState.FAILED));
        transitions.put(RealmLifecycleState.ACTIVE, EnumSet.of(RealmLifecycleState.DELETING));
        transitions.put(RealmLifecycleState.FAILED,
                EnumSet.of(RealmLifecycleState.GENERATING, RealmLifecycleState.DELETING));
        transitions.put(RealmLifecycleState.DELETING, EnumSet.of(RealmLifecycleState.ARCHIVED));
        transitions.put(RealmLifecycleState.ARCHIVED, EnumSet.noneOf(RealmLifecycleState.class));
        transitions.put(RealmLifecycleState.DELETED, EnumSet.noneOf(RealmLifecycleState.class));
        return Map.copyOf(transitions);
    }
}
