package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;
import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.RealmOwner;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

public record Realm(
        RealmId id,
        RealmOwner owner,
        RealmLifecycleState state,
        DimensionId dimension,
        RealmAllocation allocation,
        BlockPosition spawn,
        RealmPresetId preset,
        Set<UUID> members,
        Set<UUID> invitedVisitors,
        RealmAccessPolicy accessPolicy,
        CreationTimestamp createdAt,
        SchemaVersion schemaVersion,
        Optional<RealmOperation> operation,
        Optional<RealmFailure> failure) {

    public Realm {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(allocation, "allocation");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(invitedVisitors, "invitedVisitors");
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(failure, "failure");
        members = Set.copyOf(members);
        invitedVisitors = Set.copyOf(invitedVisitors);
        if (members.contains(owner.uuid()) || invitedVisitors.contains(owner.uuid())) {
            throw new IllegalArgumentException("owner must not be duplicated in membership sets");
        }
        Set<UUID> overlap = new HashSet<>(members);
        overlap.retainAll(invitedVisitors);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("members and invited visitors must be disjoint");
        }
        if (!allocation.buildableBounds().contains(spawn.chunk())) {
            throw new IllegalArgumentException("realm spawn must be inside buildable chunk bounds");
        }
        if (state == RealmLifecycleState.FAILED && failure.isEmpty()) {
            throw new IllegalArgumentException("FAILED realm requires failure metadata");
        }
        if (state != RealmLifecycleState.FAILED && failure.isPresent()) {
            throw new IllegalArgumentException("failure metadata is only valid for FAILED realms");
        }
        if ((state == RealmLifecycleState.ALLOCATED || state == RealmLifecycleState.GENERATING)
                && operation.isEmpty()) {
            throw new IllegalArgumentException(state + " realm requires operation metadata");
        }
        if (failure.isPresent() && operation.isPresent()
                && !failure.orElseThrow().operationId().equals(operation.orElseThrow().operationId())) {
            throw new IllegalArgumentException("failure and operation IDs must match");
        }
    }

    public boolean hasSameIdentityAndAllocation(Realm other) {
        return id.equals(other.id)
                && owner.equals(other.owner)
                && dimension.equals(other.dimension)
                && allocation.equals(other.allocation)
                && preset.equals(other.preset)
                && createdAt.equals(other.createdAt)
                && schemaVersion.equals(other.schemaVersion);
    }

    public Realm withLifecycle(RealmLifecycleState nextState, Optional<RealmFailure> nextFailure) {
        return new Realm(id, owner, nextState, dimension, allocation, spawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, nextFailure);
    }

    public Realm withSpawn(BlockPosition nextSpawn) {
        return new Realm(id, owner, state, dimension, allocation, nextSpawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, failure);
    }

    public Realm withOperation(RealmOperation nextOperation) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion,
                Optional.of(nextOperation), failure);
    }

    public Realm withMembers(Set<UUID> nextMembers) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset,
                nextMembers, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, failure);
    }

    public Realm withAccessPolicy(RealmAccessPolicy nextPolicy) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset,
                members, invitedVisitors, Objects.requireNonNull(nextPolicy, "nextPolicy"),
                createdAt, schemaVersion, operation, failure);
    }
}
