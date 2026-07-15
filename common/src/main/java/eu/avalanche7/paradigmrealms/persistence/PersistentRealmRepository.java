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

public final class PersistentRealmRepository implements RealmRepository {
    private final RealmStateStore store;
    private final RealmDtoMapper mapper;
    private final RealmLifecycle lifecycle;
    private final Map<RealmId, Realm> realms = new LinkedHashMap<>();
    private final List<RealmInvitation> invitations = new ArrayList<>();
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
        invitations.addAll(validated.invitations());
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
        return realms.values().stream().filter(realm -> realm.owner().uuid().equals(ownerUuid)).findFirst();
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
        long proposedRevision = Math.addExact(revision, 1L);
        RealmStateDtoV1 dto = snapshot(proposed, proposedInvitations, proposedRevision, advancedNextId);
        ValidatedRealmState validation = new RealmStateValidator(mapper).validate(dto);
        if (!validation.report().isValid()) {
            throw new IllegalStateException("refusing to persist invalid realm state: " + validation.report().issues());
        }
        store.save(dto);
        realms.clear();
        realms.putAll(proposed);
        invitations.clear();
        invitations.addAll(proposedInvitations);
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
        return new RealmStateDtoV1(
                SchemaVersion.CURRENT.value(), snapshotRevision, snapshotNextId,
                source.values().stream().sorted(Comparator.comparing(Realm::id)).map(mapper::toDto).toList(),
                sourceInvitations.stream()
                        .sorted(Comparator.comparing(RealmInvitation::realmId)
                                .thenComparing(RealmInvitation::invitedPlayerUuid))
                        .map(PersistentRealmRepository::toDto)
                        .toList());
    }

    private static RealmInvitationDtoV1 toDto(RealmInvitation invitation) {
        return new RealmInvitationDtoV1(
                invitation.schemaVersion().value(), invitation.realmId().value(),
                invitation.realmOwnerUuid().toString(), invitation.invitedPlayerUuid().toString(),
                invitation.ownerNameSnapshot(), invitation.invitedNameSnapshot(),
                invitation.createdAt().epochMillis(), invitation.expiresAt().epochMillis());
    }

    private void requireWritable() {
        if (!writable) {
            throw new ReadOnlyStoreException();
        }
    }
}
