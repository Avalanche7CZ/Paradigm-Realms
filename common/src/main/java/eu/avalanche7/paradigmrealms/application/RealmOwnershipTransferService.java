package eu.avalanche7.paradigmrealms.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.RealmOwner;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.ownership.PreviousOwnerRole;
import eu.avalanche7.paradigmrealms.ownership.RealmOwnershipTransfer;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmOwnershipTransferService {
    private final RealmRepository repository;
    private final Clock clock;
    private final Supplier<UUID> operationIds;
    private final Duration expiry;
    private final PreviousOwnerRole previousOwnerRole;

    public RealmOwnershipTransferService(
            RealmRepository repository,
            Clock clock,
            Supplier<UUID> operationIds,
            Duration expiry,
            PreviousOwnerRole previousOwnerRole) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
        this.expiry = Objects.requireNonNull(expiry, "expiry");
        this.previousOwnerRole = Objects.requireNonNull(previousOwnerRole, "previousOwnerRole");
        if (expiry.isZero() || expiry.isNegative()) throw new IllegalArgumentException("transfer expiry must be positive");
    }

    public Result offer(UUID owner, String ownerName, UUID target, String targetName) {
        Realm realm = repository.findByOwner(owner).orElse(null);
        if (realm == null) return Result.of(Status.NO_REALM);
        if (realm.state() != RealmLifecycleState.ACTIVE || realm.lifecycleOperation().isPresent()) {
            return Result.of(Status.LIFECYCLE_CONFLICT);
        }
        if (owner.equals(target)) return Result.of(Status.INVALID_TARGET);
        if (repository.findByOwner(target).isPresent()) return Result.of(Status.TARGET_ALREADY_OWNS_REALM);
        if (realm.bans().containsKey(target)) return Result.of(Status.TARGET_BANNED);
        List<RealmOwnershipTransfer> current = pruneExpired();
        if (current.stream().anyMatch(value -> value.realmId().equals(realm.id())
                || involved(value, owner) || involved(value, target))) {
            return Result.of(Status.TRANSFER_CONFLICT);
        }
        CreationTimestamp created = now();
        RealmOwnershipTransfer transfer = new RealmOwnershipTransfer(
                operationIds.get(), realm.id(), owner, target, ownerName, targetName, created,
                new CreationTimestamp(Math.addExact(created.epochMillis(), expiry.toMillis())));
        ArrayList<RealmOwnershipTransfer> updated = new ArrayList<>(current);
        updated.add(transfer);
        repository.saveOwnershipTransfers(updated);
        return new Result(Status.OFFERED, Optional.of(transfer), Optional.of(realm));
    }

    public Result accept(UUID target, UUID owner) {
        List<RealmOwnershipTransfer> current = pruneExpired();
        RealmOwnershipTransfer transfer = current.stream()
                .filter(value -> value.currentOwner().equals(owner) && value.target().equals(target))
                .findFirst().orElse(null);
        if (transfer == null) return Result.of(Status.NOT_FOUND);
        Realm realm = repository.findById(transfer.realmId()).orElse(null);
        if (realm == null || realm.state() != RealmLifecycleState.ACTIVE
                || !realm.owner().uuid().equals(owner) || realm.lifecycleOperation().isPresent()) {
            return Result.of(Status.LIFECYCLE_CONFLICT);
        }
        if (repository.findByOwner(target).isPresent()) return Result.of(Status.TARGET_ALREADY_OWNS_REALM);
        if (realm.bans().containsKey(target)) return Result.of(Status.TARGET_BANNED);

        HashSet<UUID> members = new HashSet<>(realm.members());
        HashSet<UUID> managers = new HashSet<>(realm.managers());
        HashSet<UUID> visitors = new HashSet<>(realm.invitedVisitors());
        members.remove(target);
        managers.remove(target);
        visitors.remove(target);
        members.remove(owner);
        managers.remove(owner);
        visitors.remove(owner);
        if (previousOwnerRole == PreviousOwnerRole.MANAGER) managers.add(owner);
        if (previousOwnerRole == PreviousOwnerRole.MEMBER) members.add(owner);
        Realm replacement = realm.transferOwnership(new RealmOwner(target), members, managers, visitors);

        List<RealmInvitation> invitations = repository.listInvitations().stream()
                .filter(invitation -> !invitation.realmId().equals(realm.id())
                        || !invitation.invitedPlayerUuid().equals(target))
                .map(invitation -> invitation.realmId().equals(realm.id())
                        ? new RealmInvitation(
                                invitation.realmId(), target, invitation.invitedPlayerUuid(), transfer.targetName(),
                                invitation.invitedNameSnapshot(), invitation.createdAt(), invitation.expiresAt(),
                                invitation.schemaVersion())
                        : invitation)
                .toList();
        List<RealmOwnershipTransfer> remaining = current.stream()
                .filter(value -> !value.operationId().equals(transfer.operationId())).toList();
        repository.commitOwnershipTransfer(replacement, invitations, remaining);
        return new Result(Status.COMPLETED, Optional.of(transfer), Optional.of(replacement));
    }

    public Result decline(UUID target, UUID owner) {
        return remove(owner, target, false);
    }

    public Result cancel(UUID owner) {
        return remove(owner, null, true);
    }

    public List<RealmOwnershipTransfer> pendingFor(UUID player) {
        return pruneExpired().stream().filter(value -> involved(value, player)).toList();
    }

    public int cleanupExpired() {
        int before = repository.listOwnershipTransfers().size();
        int after = pruneExpired().size();
        return before - after;
    }

    private Result remove(UUID owner, UUID target, boolean ownerCancellation) {
        List<RealmOwnershipTransfer> current = pruneExpired();
        RealmOwnershipTransfer transfer = current.stream().filter(value -> value.currentOwner().equals(owner)
                && (ownerCancellation || value.target().equals(target))).findFirst().orElse(null);
        if (transfer == null) return Result.of(Status.NOT_FOUND);
        repository.saveOwnershipTransfers(current.stream()
                .filter(value -> !value.operationId().equals(transfer.operationId())).toList());
        return new Result(ownerCancellation ? Status.CANCELLED : Status.DECLINED,
                Optional.of(transfer), repository.findById(transfer.realmId()));
    }

    private List<RealmOwnershipTransfer> pruneExpired() {
        List<RealmOwnershipTransfer> current = repository.listOwnershipTransfers();
        CreationTimestamp now = now();
        List<RealmOwnershipTransfer> retained = current.stream().filter(value -> !value.expired(now)).toList();
        if (retained.size() != current.size()) repository.saveOwnershipTransfers(retained);
        return retained;
    }

    private CreationTimestamp now() {
        return new CreationTimestamp(clock.millis());
    }

    private static boolean involved(RealmOwnershipTransfer transfer, UUID player) {
        return transfer.currentOwner().equals(player) || transfer.target().equals(player);
    }

    public enum Status {
        OFFERED,
        COMPLETED,
        DECLINED,
        CANCELLED,
        NO_REALM,
        NOT_FOUND,
        INVALID_TARGET,
        TARGET_ALREADY_OWNS_REALM,
        TARGET_BANNED,
        LIFECYCLE_CONFLICT,
        TRANSFER_CONFLICT
    }

    public record Result(Status status, Optional<RealmOwnershipTransfer> transfer, Optional<Realm> realm) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(transfer, "transfer");
            Objects.requireNonNull(realm, "realm");
        }

        public static Result of(Status status) {
            return new Result(status, Optional.empty(), Optional.empty());
        }
    }
}
