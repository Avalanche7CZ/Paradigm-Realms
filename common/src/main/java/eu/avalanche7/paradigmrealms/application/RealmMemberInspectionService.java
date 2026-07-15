package eu.avalanche7.paradigmrealms.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.access.AccessRole;
import eu.avalanche7.paradigmrealms.access.RealmAccessService;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmMemberInspectionService {
    private final RealmRepository repository;
    private final RealmAccessService access;

    public RealmMemberInspectionService(RealmRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.access = new RealmAccessService();
    }

    public Decision inspect(UUID actor, Optional<UUID> requestedOwner) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(requestedOwner, "requestedOwner");

        Optional<Realm> target;
        if (requestedOwner.isPresent()) {
            target = repository.findByOwner(requestedOwner.orElseThrow());
            if (target.isEmpty()) {
                return new Decision(Status.TARGET_NOT_FOUND, Optional.empty());
            }
        } else {
            target = repository.findByOwner(actor);
            if (target.isEmpty()) {
                List<Realm> memberships = repository.list().stream()
                        .filter(realm -> realm.members().contains(actor))
                        .toList();
                if (memberships.isEmpty()) {
                    return new Decision(Status.NO_REALM, Optional.empty());
                }
                if (memberships.size() != 1) {
                    return new Decision(Status.AMBIGUOUS_MEMBERSHIP, Optional.empty());
                }
                target = Optional.of(memberships.getFirst());
            }
        }

        Realm realm = target.orElseThrow();
        AccessRole role = access.roleOf(realm, actor);
        if (role != AccessRole.OWNER && role != AccessRole.MEMBER) {
            return new Decision(Status.NOT_AUTHORIZED, target);
        }
        return new Decision(Status.ALLOWED, target);
    }

    public enum Status {
        ALLOWED,
        TARGET_NOT_FOUND,
        NO_REALM,
        AMBIGUOUS_MEMBERSHIP,
        NOT_AUTHORIZED
    }

    public record Decision(Status status, Optional<Realm> realm) {
        public Decision {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(realm, "realm");
            if (status == Status.ALLOWED && realm.isEmpty()) {
                throw new IllegalArgumentException("allowed inspection requires a realm");
            }
        }

        public boolean allowed() {
            return status == Status.ALLOWED;
        }
    }
}
