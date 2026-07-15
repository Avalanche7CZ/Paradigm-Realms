package eu.avalanche7.paradigmrealms.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.allocation.AllocationException;
import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmFailure;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycle;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmInvitationDtoV1;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.persistence.validation.RealmDtoMapper;
import eu.avalanche7.paradigmrealms.persistence.validation.RealmStateValidator;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidatedRealmState;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationReport;
import eu.avalanche7.paradigmrealms.ownership.RealmOwnershipTransfer;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmOwnershipTransferDtoV1;

public final class PersistentRealmRepository implements RealmRepository {
    private final RealmStateStore store;
    private final RealmDtoMapper mapper;
    private final RealmLifecycle lifecycle;
    private final Map<RealmId, Realm> realms = new LinkedHashMap<>();
    private final Map<UUID, RealmId> activeOwners = new LinkedHashMap<>();
    private final List<RealmInvitation> invitations = new ArrayList<>();
    private final List<RealmOwnershipTransfer> ownershipTransfers = new ArrayList<>();
    private final List<ValidationIssue> startupIssues;
    private boolean writable;
    private long revision;
    private long nextRealmId;

    public PersistentRealmRepository(RealmStateStore store, RealmAllocator allocator) {
        this.store = store;
        this.mapper = new RealmDtoMapper(allocator);
        this.lifecycle = new RealmLifecycle();
        StateLoadResult loaded = store.load();
        ValidatedRealmState validated = new RealmStateValidator(mapper).validate(loaded.state());
        validated.realms().forEach(realm -> realms.put(realm.id(), realm));
        rebuildActiveOwners();
        invitations.addAll(validated.invitations());
        ownershipTransfers.addAll(validated.ownershipTransfers());
        List<ValidationIssue> combined = new ArrayList<>(loaded.issues());
        combined.addAll(validated.report().issues());
        this.startupIssues = List.copyOf(combined);
        this.writable = loaded.writable() && validated.report().isValid() && loaded.issues().isEmpty();
        this.revision = loaded.state().revision();
        this.nextRealmId = loaded.state().nextRealmId();
    }

    @Override
    public synchronized Optional<Realm> findById(RealmId id) {
        return Optional.ofNullable(realms.get(id));
    }

    @Override
    public synchronized Optional<Realm> findByOwner(UUID ownerUuid) {
        RealmId id = activeOwners.get(ownerUuid);
        return id == null ? Optional.empty() : Optional.ofNullable(realms.get(id));
    }

    @Override
    public synchronized List<Realm> list() {
        return realms.values().stream().sorted(Comparator.comparing(Realm::id)).toList();
    }

    @Override
    public synchronized void save(Realm realm) {
        requireWritable();
        if (realm.id().value() >= nextRealmId) {
            throw new IllegalArgumentException("realm ID must have been allocated before save");
        }
        Realm existing = realms.get(realm.id());
        if (existing != null && !existing.hasSameIdentityAndAllocation(realm)) {
            throw new IllegalArgumentException("realm identity or allocation data cannot change");
        }
        realms.values().stream()
                .filter(other -> !other.id().equals(realm.id()))
                .filter(other -> other.owner().equals(realm.owner()))
                .filter(other -> conflictingOwnerRecord(other, realm))
                .findAny()
                .ifPresent(other -> {
                    throw new IllegalArgumentException("owner already has realm " + other.id());
                });
        persistWith(realm.id(), realm, invitations, nextRealmId);
    }

    @Override
    public synchronized List<RealmInvitation> listInvitations() {
        return List.copyOf(invitations);
    }

    @Override public synchronized List<RealmOwnershipTransfer> listOwnershipTransfers() {
        return List.copyOf(ownershipTransfers);
    }

    @Override public synchronized void saveOwnershipTransfers(List<RealmOwnershipTransfer> transfers) {
        requireWritable();
        persistSnapshot(new LinkedHashMap<>(realms), invitations, List.copyOf(transfers), nextRealmId);
    }

    @Override public synchronized void commitOwnershipTransfer(
            Realm replacement,
            List<RealmInvitation> proposedInvitations,
            List<RealmOwnershipTransfer> transfers) {
        requireWritable();
        Realm existing = realms.get(replacement.id());
        if (existing == null || !existing.hasSameStableIdentityAndAllocation(replacement)
                || existing.owner().equals(replacement.owner())) {
            throw new IllegalArgumentException("invalid ownership replacement");
        }
        Map<RealmId, Realm> proposed = new LinkedHashMap<>(realms);
        proposed.put(replacement.id(), replacement);
        persistSnapshot(proposed, List.copyOf(proposedInvitations), List.copyOf(transfers), nextRealmId);
    }

    @Override
    public synchronized void saveRealmAndInvitations(Realm realm, List<RealmInvitation> replacements) {
        requireWritable();
        java.util.Objects.requireNonNull(realm, "realm");
        java.util.Objects.requireNonNull(replacements, "replacements");
        Realm existing = realms.get(realm.id());
        if (existing == null || !existing.hasSameIdentityAndAllocation(realm)) {
            throw new IllegalArgumentException("realm identity or allocation data cannot change");
        }
        persistWith(realm.id(), realm, List.copyOf(replacements), nextRealmId);
    }

    @Override
    public synchronized void saveAll(List<Realm> replacements, List<RealmInvitation> proposedInvitations) {
        requireWritable();
        java.util.Objects.requireNonNull(replacements, "replacements");
        java.util.Objects.requireNonNull(proposedInvitations, "proposedInvitations");
        Map<RealmId, Realm> proposed = new LinkedHashMap<>(realms);
        for (Realm replacement : replacements) {
            Realm existing = proposed.get(replacement.id());
            if (existing == null || !existing.hasSameIdentityAndAllocation(replacement)) {
                throw new IllegalArgumentException("realm identity or allocation data cannot change");
            }
            proposed.put(replacement.id(), replacement);
        }
        persistSnapshot(proposed, List.copyOf(proposedInvitations), ownershipTransfers, nextRealmId);
    }

    @Override
    public synchronized RealmId allocateNextRealmId() {
        requireWritable();
        if (nextRealmId > RealmAllocator.MAX_REALM_ID) {
            throw new AllocationException("realm allocator is exhausted");
        }
        RealmId allocated = new RealmId(nextRealmId);
        long advanced = Math.addExact(nextRealmId, 1L);
        persistWith(null, null, invitations, advanced);
        return allocated;
    }

    @Override
    public synchronized Realm allocateRealm(RealmFactory factory) {
        requireWritable();
        if (nextRealmId > RealmAllocator.MAX_REALM_ID) {
            throw new AllocationException("realm allocator is exhausted");
        }
        RealmId allocated = new RealmId(nextRealmId);
        Realm realm = java.util.Objects.requireNonNull(factory, "factory").create(allocated);
        if (!realm.id().equals(allocated)) {
            throw new IllegalArgumentException("realm factory must use allocated ID " + allocated);
        }
        if (realms.containsKey(allocated)) {
            throw new IllegalStateException("allocated realm ID already exists: " + allocated);
        }
        realms.values().stream()
                .filter(other -> other.owner().equals(realm.owner()))
                .filter(other -> other.state() == RealmLifecycleState.ACTIVE)
                .findAny()
                .ifPresent(other -> {
                    throw new IllegalArgumentException("owner already has realm " + other.id());
                });
        long advanced = Math.addExact(nextRealmId, 1L);
        persistWith(allocated, realm, invitations, advanced);
        return realm;
    }

    @Override
    public synchronized Realm updateLifecycle(
            RealmId id, RealmLifecycleState target, Optional<RealmFailure> failure) {
        requireWritable();
        Realm existing = Optional.ofNullable(realms.get(id))
                .orElseThrow(() -> new IllegalArgumentException("unknown realm ID " + id));
        Realm updated = lifecycle.transition(existing, target, failure);
        persistWith(id, updated, invitations, nextRealmId);
        return updated;
    }

    @Override
    public synchronized SchemaMetadata schemaMetadata() {
        return new SchemaMetadata(SchemaVersion.CURRENT, revision, nextRealmId, writable);
    }

    @Override
    public synchronized ValidationReport validate() {
        RealmStateDtoV1 snapshot = snapshot(revision, nextRealmId);
        ValidationReport current = new RealmStateValidator(mapper).validate(snapshot).report();
        return current.plus(startupIssues);
    }

    private void persistWith(
            RealmId replacedId,
            Realm replacement,
            List<RealmInvitation> proposedInvitations,
            long advancedNextId) {
        Map<RealmId, Realm> proposed = new LinkedHashMap<>(realms);
        if (replacedId != null) {
            proposed.put(replacedId, replacement);
        }
        persistSnapshot(proposed, proposedInvitations, ownershipTransfers, advancedNextId);
    }

    private void persistSnapshot(
            Map<RealmId, Realm> proposed, List<RealmInvitation> proposedInvitations, long advancedNextId) {
        persistSnapshot(proposed, proposedInvitations, ownershipTransfers, advancedNextId);
    }

    private void persistSnapshot(
            Map<RealmId, Realm> proposed,
            List<RealmInvitation> proposedInvitations,
            List<RealmOwnershipTransfer> proposedTransfers,
            long advancedNextId) {
        long proposedRevision = Math.addExact(revision, 1L);
        RealmStateDtoV1 dto = snapshot(
                proposed, proposedInvitations, proposedTransfers, proposedRevision, advancedNextId);
        ValidatedRealmState validation = new RealmStateValidator(mapper).validate(dto);
        if (!validation.report().isValid()) {
            throw new IllegalStateException("refusing to persist invalid realm state: " + validation.report().issues());
        }
        store.save(dto);
        realms.clear();
        realms.putAll(proposed);
        rebuildActiveOwners();
        invitations.clear();
        invitations.addAll(proposedInvitations);
        ownershipTransfers.clear();
        ownershipTransfers.addAll(proposedTransfers);
        revision = proposedRevision;
        nextRealmId = advancedNextId;
    }

    private RealmStateDtoV1 snapshot(long snapshotRevision, long snapshotNextId) {
        return snapshot(realms, snapshotRevision, snapshotNextId);
    }

    private RealmStateDtoV1 snapshot(
            Map<RealmId, Realm> source, long snapshotRevision, long snapshotNextId) {
        return snapshot(source, invitations, snapshotRevision, snapshotNextId);
    }

    private RealmStateDtoV1 snapshot(
            Map<RealmId, Realm> source,
            List<RealmInvitation> sourceInvitations,
            long snapshotRevision,
            long snapshotNextId) {
        return snapshot(source, sourceInvitations, ownershipTransfers, snapshotRevision, snapshotNextId);
    }

    private RealmStateDtoV1 snapshot(
            Map<RealmId, Realm> source,
            List<RealmInvitation> sourceInvitations,
            List<RealmOwnershipTransfer> sourceTransfers,
            long snapshotRevision,
            long snapshotNextId) {
        return new RealmStateDtoV1(
                SchemaVersion.CURRENT.value(), snapshotRevision, snapshotNextId,
                source.values().stream().sorted(Comparator.comparing(Realm::id)).map(mapper::toDto).toList(),
                sourceInvitations.stream()
                        .sorted(Comparator.comparing(RealmInvitation::realmId)
                                .thenComparing(RealmInvitation::invitedPlayerUuid))
                        .map(PersistentRealmRepository::toDto)
                        .toList(),
                sourceTransfers.stream().sorted(Comparator.comparing(RealmOwnershipTransfer::realmId))
                        .map(PersistentRealmRepository::toDto).toList());
    }

    private static RealmInvitationDtoV1 toDto(RealmInvitation invitation) {
        return new RealmInvitationDtoV1(
                invitation.schemaVersion().value(), invitation.realmId().value(),
                invitation.realmOwnerUuid().toString(), invitation.invitedPlayerUuid().toString(),
                invitation.ownerNameSnapshot(), invitation.invitedNameSnapshot(),
                invitation.createdAt().epochMillis(), invitation.expiresAt().epochMillis());
    }

    private static RealmOwnershipTransferDtoV1 toDto(RealmOwnershipTransfer transfer) {
        return new RealmOwnershipTransferDtoV1(
                transfer.operationId().toString(), transfer.realmId().value(),
                transfer.currentOwner().toString(), transfer.target().toString(),
                transfer.currentOwnerName(), transfer.targetName(),
                transfer.createdAt().epochMillis(), transfer.expiresAt().epochMillis());
    }

    private void requireWritable() {
        if (!writable) {
            throw new ReadOnlyStoreException();
        }
    }

    private void rebuildActiveOwners() {
        activeOwners.clear();
        for (Realm realm : realms.values()) {
            if (realm.state() == RealmLifecycleState.ACTIVE) {
                RealmId previous = activeOwners.putIfAbsent(realm.owner().uuid(), realm.id());
                if (previous != null) {
                    throw new IllegalStateException("duplicate active owner " + realm.owner().uuid());
                }
            }
        }
    }

    private static boolean conflictingOwnerRecord(Realm existing, Realm proposed) {
        if (archived(existing) || archived(proposed)) return false;
        return proposed.replacementOf().map(id -> !id.equals(existing.id())).orElse(true)
                && existing.replacementOf().map(id -> !id.equals(proposed.id())).orElse(true);
    }

    private static boolean archived(Realm realm) {
        return realm.state() == RealmLifecycleState.ARCHIVED || realm.state() == RealmLifecycleState.DELETED;
    }
}
