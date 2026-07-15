package eu.avalanche7.paradigmrealms.membership;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmMembershipService {
    private final RealmRepository repository;
    private final MembershipLimits limits;
    private final Clock clock;

    public RealmMembershipService(RealmRepository repository, MembershipLimits limits, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized MembershipResult invite(
            UUID owner,
            String ownerName,
            UUID target,
            String targetName) {
        Optional<Realm> found = repository.findByOwner(owner);
        if (found.isEmpty()) return result(MembershipStatus.NO_REALM);
        Realm realm = found.orElseThrow();
        if (realm.state() != RealmLifecycleState.ACTIVE) return result(MembershipStatus.REALM_NOT_ACTIVE, realm);
        if (!realm.owner().uuid().equals(owner)) return result(MembershipStatus.NOT_OWNER, realm);
        if (owner.equals(target)) return result(MembershipStatus.OWNER_CANNOT_BE_TARGET, realm);
        if (realm.members().contains(target)) return result(MembershipStatus.ALREADY_MEMBER, realm);

        long now = clock.millis();
        List<RealmInvitation> cleaned = withoutExpired(repository.listInvitations(), now);
        RealmInvitation existing = find(cleaned, realm, target).orElse(null);
        long expiry = Math.addExact(now, limits.invitationExpiry().toMillis());
        RealmInvitation replacement = new RealmInvitation(
                realm.id(), owner, target, ownerName, targetName,
                new CreationTimestamp(now), new CreationTimestamp(expiry), SchemaVersion.V1);
        if (existing == null && countForRealm(cleaned, realm) >= limits.maximumPendingInvitations()) {
            return result(MembershipStatus.MAXIMUM_PENDING_INVITATIONS, realm);
        }
        cleaned.removeIf(invitation -> samePair(invitation, realm, target));
        cleaned.add(replacement);
        repository.saveRealmAndInvitations(realm, cleaned);
        return new MembershipResult(existing == null ? MembershipStatus.CREATED : MembershipStatus.REFRESHED,
                Optional.of(realm), Optional.of(replacement));
    }

    public synchronized List<RealmInvitation> pendingFor(UUID invitedPlayer) {
        long now = clock.millis();
        return repository.listInvitations().stream()
                .filter(invitation -> invitation.invitedPlayerUuid().equals(invitedPlayer))
                .filter(invitation -> !invitation.expiredAt(now))
                .sorted(java.util.Comparator.comparingLong(
                        invitation -> invitation.expiresAt().epochMillis()))
                .toList();
    }

    public synchronized MembershipResult accept(UUID invitedPlayer, UUID owner) {
        Optional<Realm> found = repository.findByOwner(owner);
        if (found.isEmpty()) return result(MembershipStatus.INVITATION_NOT_FOUND);
        Realm realm = found.orElseThrow();
        List<RealmInvitation> invitations = new ArrayList<>(repository.listInvitations());
        RealmInvitation invitation = find(invitations, realm, invitedPlayer).orElse(null);
        if (invitation == null) return result(MembershipStatus.INVITATION_NOT_FOUND, realm);
        if (invitation.expiredAt(clock.millis())) {
            invitations.remove(invitation);
            repository.saveRealmAndInvitations(realm, invitations);
            return result(MembershipStatus.INVITATION_EXPIRED, realm);
        }
        if (realm.state() != RealmLifecycleState.ACTIVE) return result(MembershipStatus.REALM_NOT_ACTIVE, realm);
        if (realm.members().contains(invitedPlayer)) {
            invitations.remove(invitation);
            repository.saveRealmAndInvitations(realm, invitations);
            return result(MembershipStatus.ALREADY_MEMBER, realm);
        }
        if (realm.members().size() >= limits.maximumMembers()) {
            return result(MembershipStatus.MAXIMUM_MEMBERS, realm);
        }
        Set<UUID> members = new HashSet<>(realm.members());
        members.add(invitedPlayer);
        Realm updated = realm.withMembers(members);
        invitations.remove(invitation);
        repository.saveRealmAndInvitations(updated, invitations);
        return new MembershipResult(MembershipStatus.ACCEPTED, Optional.of(updated), Optional.of(invitation));
    }

    public synchronized MembershipResult decline(UUID invitedPlayer, UUID owner) {
        Optional<Realm> found = repository.findByOwner(owner);
        if (found.isEmpty()) return result(MembershipStatus.INVITATION_NOT_FOUND);
        Realm realm = found.orElseThrow();
        List<RealmInvitation> invitations = new ArrayList<>(repository.listInvitations());
        RealmInvitation invitation = find(invitations, realm, invitedPlayer).orElse(null);
        if (invitation == null) return result(MembershipStatus.INVITATION_NOT_FOUND, realm);
        invitations.remove(invitation);
        repository.saveRealmAndInvitations(realm, invitations);
        MembershipStatus status = invitation.expiredAt(clock.millis())
                ? MembershipStatus.INVITATION_EXPIRED : MembershipStatus.DECLINED;
        return new MembershipResult(status, Optional.of(realm), Optional.of(invitation));
    }

    public synchronized MembershipResult remove(UUID owner, UUID target) {
        Optional<Realm> found = repository.findByOwner(owner);
        if (found.isEmpty()) return result(MembershipStatus.NO_REALM);
        Realm realm = found.orElseThrow();
        if (owner.equals(target)) return result(MembershipStatus.OWNER_CANNOT_BE_TARGET, realm);
        List<RealmInvitation> invitations = new ArrayList<>(repository.listInvitations());
        invitations.removeIf(invitation -> samePair(invitation, realm, target));
        if (!realm.members().contains(target)) {
            if (!invitations.equals(repository.listInvitations())) {
                repository.saveRealmAndInvitations(realm, invitations);
            }
            return result(MembershipStatus.NOT_MEMBER, realm);
        }
        Set<UUID> members = new HashSet<>(realm.members());
        members.remove(target);
        Realm updated = realm.withMembers(members);
        repository.saveRealmAndInvitations(updated, invitations);
        return result(MembershipStatus.REMOVED, updated);
    }

    public synchronized MembershipResult leave(UUID member, UUID owner) {
        Optional<Realm> found = repository.findByOwner(owner);
        if (found.isEmpty()) return result(MembershipStatus.NO_REALM);
        Realm realm = found.orElseThrow();
        if (realm.owner().uuid().equals(member)) return result(MembershipStatus.OWNER_CANNOT_BE_TARGET, realm);
        if (!realm.members().contains(member)) return result(MembershipStatus.NOT_MEMBER, realm);
        Set<UUID> members = new HashSet<>(realm.members());
        members.remove(member);
        Realm updated = realm.withMembers(members);
        repository.saveRealmAndInvitations(updated, repository.listInvitations());
        return result(MembershipStatus.LEFT, updated);
    }

    public synchronized MembershipResult setAccess(UUID owner, RealmAccessPolicy policy) {
        if (policy != RealmAccessPolicy.PRIVATE && policy != RealmAccessPolicy.PUBLIC_VISIT) {
            throw new IllegalArgumentException("realm access must be PRIVATE or PUBLIC_VISIT");
        }
        Optional<Realm> found = repository.findByOwner(owner);
        if (found.isEmpty()) return result(MembershipStatus.NO_REALM);
        Realm realm = found.orElseThrow();
        if (realm.state() != RealmLifecycleState.ACTIVE) return result(MembershipStatus.REALM_NOT_ACTIVE, realm);
        if (realm.accessPolicy() == policy) return result(MembershipStatus.NO_CHANGE, realm);
        Realm updated = realm.withAccessPolicy(policy);
        repository.saveRealmAndInvitations(updated, repository.listInvitations());
        return result(MembershipStatus.ACCESS_CHANGED, updated);
    }

    public synchronized List<Realm> membershipsFor(UUID player) {
        return repository.list().stream().filter(realm -> realm.members().contains(player)).toList();
    }

    public synchronized int cleanupExpired() {
        long now = clock.millis();
        List<RealmInvitation> current = repository.listInvitations();
        List<RealmInvitation> cleaned = withoutExpired(current, now);
        if (cleaned.size() == current.size()) return 0;
        Realm anchor = repository.list().stream().findFirst().orElse(null);
        if (anchor == null) return 0;
        repository.saveRealmAndInvitations(anchor, cleaned);
        return current.size() - cleaned.size();
    }

    private static Optional<RealmInvitation> find(
            List<RealmInvitation> invitations, Realm realm, UUID target) {
        return invitations.stream().filter(invitation -> samePair(invitation, realm, target)).findFirst();
    }

    private static boolean samePair(RealmInvitation invitation, Realm realm, UUID target) {
        return invitation.realmId().equals(realm.id()) && invitation.invitedPlayerUuid().equals(target);
    }

    private static int countForRealm(List<RealmInvitation> invitations, Realm realm) {
        return (int) invitations.stream().filter(invitation -> invitation.realmId().equals(realm.id())).count();
    }

    private static List<RealmInvitation> withoutExpired(List<RealmInvitation> source, long now) {
        return new ArrayList<>(source.stream().filter(invitation -> !invitation.expiredAt(now)).toList());
    }

    private static MembershipResult result(MembershipStatus status) {
        return new MembershipResult(status, Optional.empty(), Optional.empty());
    }

    private static MembershipResult result(MembershipStatus status, Realm realm) {
        return new MembershipResult(status, Optional.of(realm), Optional.empty());
    }
}
