package eu.avalanche7.paradigmrealms.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmBan;
import eu.avalanche7.paradigmrealms.domain.realm.RealmMemberRole;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSettings;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSetting;
import eu.avalanche7.paradigmrealms.config.RealmSettingsPolicy;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmOwnerManagementService {
    private final RealmRepository repository;
    private final Clock clock;
    private final RealmSettingsPolicy settingsPolicy;

    public RealmOwnerManagementService(RealmRepository repository, Clock clock) {
        this(repository, clock, RealmSettingsPolicy.secureDefaults());
    }

    public RealmOwnerManagementService(
            RealmRepository repository, Clock clock, RealmSettingsPolicy settingsPolicy) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settingsPolicy = Objects.requireNonNull(settingsPolicy, "settingsPolicy");
    }

    public Result setName(UUID actor, String displayName) {
        return mutateOwned(actor, realm -> realm.withIdentity(displayName, realm.description(), realm.listed()));
    }

    public Result setDescription(UUID actor, String description) {
        return mutateOwned(actor, realm -> realm.withIdentity(realm.displayName(), description, realm.listed()));
    }

    public Result setListed(UUID actor, boolean listed) {
        return mutateManaged(actor, realm -> realm.withIdentity(realm.displayName(), realm.description(), listed));
    }

    public Result setRole(UUID actor, UUID target, RealmMemberRole role) {
        Optional<Realm> found = repository.findByOwner(actor);
        if (found.isEmpty()) return Result.noRealm();
        Realm realm = found.orElseThrow();
        if (operationInProgress(realm)) return new Result(Status.OPERATION_IN_PROGRESS, Optional.of(realm));
        if (target.equals(actor)) return new Result(Status.INVALID_TARGET, Optional.of(realm));
        Set<UUID> members = new HashSet<>(realm.members());
        Set<UUID> managers = new HashSet<>(realm.managers());
        if (role == RealmMemberRole.MANAGER) {
            members.remove(target);
            managers.add(target);
        } else {
            managers.remove(target);
            members.add(target);
        }
        Realm updated = realm.withRoles(members, managers);
        repository.saveRealmAndInvitations(updated, repository.listInvitations());
        return new Result(Status.CHANGED, Optional.of(updated));
    }

    public Result remove(UUID actor, UUID target) {
        return mutateManaged(actor, realm -> {
            if (target.equals(realm.owner().uuid()) || realm.managers().contains(target)) {
                throw new ForbiddenTargetException();
            }
            Set<UUID> members = new HashSet<>(realm.members());
            members.remove(target);
            return realm.withRoles(members, realm.managers());
        }, target);
    }

    public Result ban(UUID actor, UUID target, String targetName, Optional<String> reason) {
        return mutateManaged(actor, realm -> {
            if (target.equals(realm.owner().uuid()) || (realm.managers().contains(actor) && realm.managers().contains(target))) {
                throw new ForbiddenTargetException();
            }
            Set<UUID> members = new HashSet<>(realm.members());
            Set<UUID> managers = new HashSet<>(realm.managers());
            members.remove(target);
            managers.remove(target);
            Map<UUID, RealmBan> bans = new HashMap<>(realm.bans());
            bans.put(target, new RealmBan(target, targetName, actor, reason,
                    CreationTimestamp.from(clock.instant())));
            return realm.withRoles(members, managers).withBans(bans);
        }, target);
    }

    public Result unban(UUID actor, UUID target) {
        return mutateManaged(actor, realm -> {
            Map<UUID, RealmBan> bans = new HashMap<>(realm.bans());
            if (bans.remove(target) == null) throw new MissingBanException();
            return realm.withBans(bans);
        });
    }

    public Result setSettings(UUID actor, RealmSettings settings) {
        return mutateManaged(actor, realm -> realm.withSettings(settings));
    }

    public Result setSetting(UUID actor, RealmSetting setting, boolean value) {
        Realm realm = managedRealm(actor).orElse(null);
        if (realm == null) return Result.noRealm();
        if (operationInProgress(realm)) return new Result(Status.OPERATION_IN_PROGRESS, Optional.of(realm));
        boolean manager = realm.managers().contains(actor);
        var policy = settingsPolicy.settings().get(setting);
        if (policy.forcedValue().isPresent()
                || (manager && !policy.managerMutable())
                || (!manager && !policy.ownerMutable())) {
            return new Result(Status.SERVER_LOCKED, Optional.of(realm));
        }
        Realm updated = realm.withSettings(realm.settings().with(setting, value));
        repository.saveRealmAndInvitations(updated, repository.listInvitations());
        return new Result(Status.CHANGED, Optional.of(updated));
    }

    private Result mutateOwned(UUID actor, Mutation mutation) {
        Optional<Realm> found = repository.findByOwner(actor);
        if (found.isEmpty()) return Result.noRealm();
        Realm realm = found.orElseThrow();
        if (operationInProgress(realm)) return new Result(Status.OPERATION_IN_PROGRESS, Optional.of(realm));
        try {
            Realm updated = mutation.apply(realm);
            repository.saveRealmAndInvitations(updated, repository.listInvitations());
            return new Result(Status.CHANGED, Optional.of(updated));
        } catch (ForbiddenTargetException exception) {
            return new Result(Status.FORBIDDEN, Optional.of(realm));
        }
    }

    private Result mutateManaged(UUID actor, Mutation mutation) {
        return mutateManaged(actor, mutation, null);
    }

    private Result mutateManaged(UUID actor, Mutation mutation, UUID invitationTarget) {
        Realm realm = managedRealm(actor).orElse(null);
        if (realm == null) return Result.noRealm();
        if (operationInProgress(realm)) return new Result(Status.OPERATION_IN_PROGRESS, Optional.of(realm));
        try {
            Realm updated = mutation.apply(realm);
            List<RealmInvitation> invitations = new ArrayList<>(repository.listInvitations());
            if (invitationTarget != null) {
                invitations.removeIf(invitation -> invitation.realmId().equals(realm.id())
                        && invitation.invitedPlayerUuid().equals(invitationTarget));
            }
            repository.saveRealmAndInvitations(updated, invitations);
            return new Result(Status.CHANGED, Optional.of(updated));
        } catch (ForbiddenTargetException exception) {
            return new Result(Status.FORBIDDEN, Optional.of(realm));
        } catch (MissingBanException exception) {
            return new Result(Status.NOT_FOUND, Optional.of(realm));
        }
    }

    public Optional<Realm> managedRealm(UUID actor) {
        return repository.list().stream().filter(value -> value.owner().uuid().equals(actor)
                || value.managers().contains(actor)).filter(value -> value.state()
                == eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ACTIVE).findFirst();
    }

    private static boolean operationInProgress(Realm realm) {
        return realm.lifecycleOperation().filter(operation -> operation.stage()
                != eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperationStage.FAILED).isPresent();
    }

    @FunctionalInterface
    private interface Mutation { Realm apply(Realm realm); }
    private static final class ForbiddenTargetException extends RuntimeException {}
    private static final class MissingBanException extends RuntimeException {}

    public enum Status {
        CHANGED, NO_REALM, FORBIDDEN, INVALID_TARGET, NOT_FOUND, SERVER_LOCKED, OPERATION_IN_PROGRESS
    }
    public record Result(Status status, Optional<Realm> realm) {
        static Result noRealm() { return new Result(Status.NO_REALM, Optional.empty()); }
        public boolean succeeded() { return status == Status.CHANGED; }
    }
}
