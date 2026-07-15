package eu.avalanche7.paradigmrealms.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.access.AccessAction;
import eu.avalanche7.paradigmrealms.access.AccessDecision;
import eu.avalanche7.paradigmrealms.access.RealmAccessService;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmVisitService {
    private final RealmRepository repository;
    private final RealmAccessService access;

    public RealmVisitService(RealmRepository repository) {
        this(repository, new RealmAccessService());
    }

    RealmVisitService(RealmRepository repository, RealmAccessService access) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = Objects.requireNonNull(access, "access");
    }

    public Decision evaluate(UUID visitor, UUID owner, boolean adminBypassActive) {
        Objects.requireNonNull(visitor, "visitor");
        Objects.requireNonNull(owner, "owner");
        Optional<Realm> target = repository.findByOwner(owner);
        if (target.isEmpty()) {
            return new Decision(Status.TARGET_NOT_FOUND, Optional.empty(), Optional.empty(), false);
        }

        Realm realm = target.orElseThrow();
        AccessDecision accessDecision = access.evaluate(realm, visitor, AccessAction.ENTER);
        if (accessDecision.allowed()) {
            return new Decision(Status.ALLOWED, target, Optional.of(accessDecision), false);
        }
        if (realm.state() != RealmLifecycleState.ACTIVE) {
            return new Decision(Status.REALM_NOT_ACTIVE, target, Optional.of(accessDecision), false);
        }
        if (adminBypassActive) {
            return new Decision(Status.ALLOWED, target, Optional.of(accessDecision), true);
        }
        return new Decision(Status.ACCESS_DENIED, target, Optional.of(accessDecision), false);
    }

    public enum Status {
        ALLOWED,
        TARGET_NOT_FOUND,
        REALM_NOT_ACTIVE,
        ACCESS_DENIED
    }

    public record Decision(
            Status status,
            Optional<Realm> realm,
            Optional<AccessDecision> accessDecision,
            boolean adminBypassUsed) {
        public Decision {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(realm, "realm");
            Objects.requireNonNull(accessDecision, "accessDecision");
            if (status == Status.ALLOWED && realm.isEmpty()) {
                throw new IllegalArgumentException("an allowed visit requires a realm");
            }
            if (adminBypassUsed && status != Status.ALLOWED) {
                throw new IllegalArgumentException("admin bypass can only be used by an allowed visit");
            }
        }

        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }
}
