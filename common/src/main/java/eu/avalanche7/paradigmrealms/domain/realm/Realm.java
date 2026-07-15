package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
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
        Optional<RealmFailure> failure,
        String displayName,
        String description,
        boolean listed,
        Set<UUID> managers,
        Map<UUID, RealmBan> bans,
        RealmSettings settings,
        Optional<RealmId> replacementOf,
        Optional<RealmId> replacedBy,
        Optional<CreationTimestamp> archivedAt,
        Optional<RealmLifecycleOperation> lifecycleOperation) {

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
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(managers, "managers");
        Objects.requireNonNull(bans, "bans");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(replacementOf, "replacementOf");
        Objects.requireNonNull(replacedBy, "replacedBy");
        Objects.requireNonNull(archivedAt, "archivedAt");
        Objects.requireNonNull(lifecycleOperation, "lifecycleOperation");
        members = Set.copyOf(members);
        invitedVisitors = Set.copyOf(invitedVisitors);
        managers = Set.copyOf(managers);
        bans = Map.copyOf(new HashMap<>(bans));
        displayName = validateDisplayName(displayName);
        description = validateDescription(description);
        if (members.contains(owner.uuid()) || managers.contains(owner.uuid()) || invitedVisitors.contains(owner.uuid())) {
            throw new IllegalArgumentException("owner must not be duplicated in membership sets");
        }
        Set<UUID> overlap = new HashSet<>(members);
        overlap.retainAll(invitedVisitors);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("members and invited visitors must be disjoint");
        }
        overlap = new HashSet<>(managers);
        overlap.retainAll(members);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("members and managers must be disjoint");
        }
        overlap = new HashSet<>(managers);
        overlap.retainAll(invitedVisitors);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("managers and invited visitors must be disjoint");
        }
        if (bans.containsKey(owner.uuid())) {
            throw new IllegalArgumentException("owner must not be banned");
        }
        for (Map.Entry<UUID, RealmBan> entry : bans.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().playerUuid())) {
                throw new IllegalArgumentException("ban map key must match banned player UUID");
            }
            if (members.contains(entry.getKey()) || managers.contains(entry.getKey())
                    || invitedVisitors.contains(entry.getKey())) {
                throw new IllegalArgumentException("banned player must not retain realm access state");
            }
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
        if ((state == RealmLifecycleState.ALLOCATED || state == RealmLifecycleState.GENERATING
                || state == RealmLifecycleState.READY)
                && operation.isEmpty()) {
            throw new IllegalArgumentException(state + " realm requires operation metadata");
        }
        if (failure.isPresent() && operation.isPresent()
                && !failure.orElseThrow().operationId().equals(operation.orElseThrow().operationId())) {
            throw new IllegalArgumentException("failure and operation IDs must match");
        }
        if (state == RealmLifecycleState.ARCHIVED && archivedAt.isEmpty()) {
            throw new IllegalArgumentException("archived realm requires archive timestamp");
        }
        if (state != RealmLifecycleState.ARCHIVED && archivedAt.isPresent()) {
            throw new IllegalArgumentException("only archived realm may have archive timestamp");
        }
        if (replacementOf.isPresent() && replacementOf.equals(replacedBy)) {
            throw new IllegalArgumentException("realm cannot replace itself");
        }
    }

    public Realm(
            RealmId id, RealmOwner owner, RealmLifecycleState state, DimensionId dimension,
            RealmAllocation allocation, BlockPosition spawn, RealmPresetId preset, Set<UUID> members,
            Set<UUID> invitedVisitors, RealmAccessPolicy accessPolicy, CreationTimestamp createdAt,
            SchemaVersion schemaVersion, Optional<RealmOperation> operation, Optional<RealmFailure> failure) {
        this(id, owner, state, dimension, allocation, spawn, preset, members, invitedVisitors, accessPolicy,
                createdAt, schemaVersion, operation, failure, defaultDisplayName(id), "", false, Set.of(), Map.of(),
                RealmSettings.SECURE_DEFAULTS, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
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

    public boolean hasSameStableIdentityAndAllocation(Realm other) {
        return id.equals(other.id)
                && dimension.equals(other.dimension)
                && allocation.equals(other.allocation)
                && preset.equals(other.preset)
                && createdAt.equals(other.createdAt)
                && schemaVersion.equals(other.schemaVersion);
    }

    public Realm transferOwnership(
            RealmOwner nextOwner, Set<UUID> nextMembers, Set<UUID> nextManagers, Set<UUID> nextVisitors) {
        return new Realm(id, nextOwner, state, dimension, allocation, spawn, preset,
                nextMembers, nextVisitors, accessPolicy, createdAt, schemaVersion, operation, failure,
                displayName, description, listed, nextManagers, bans, settings, replacementOf, replacedBy,
                archivedAt, lifecycleOperation);
    }

    public Realm withLifecycle(RealmLifecycleState nextState, Optional<RealmFailure> nextFailure) {
        return new Realm(id, owner, nextState, dimension, allocation, spawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, nextFailure,
                displayName, description, listed, managers, bans, settings, replacementOf, replacedBy, archivedAt,
                lifecycleOperation);
    }

    public Realm withSpawn(BlockPosition nextSpawn) {
        return new Realm(id, owner, state, dimension, allocation, nextSpawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, failure,
                displayName, description, listed, managers, bans, settings, replacementOf, replacedBy, archivedAt,
                lifecycleOperation);
    }

    public Realm withOperation(RealmOperation nextOperation) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion,
                Optional.of(nextOperation), failure, displayName, description, listed, managers, bans, settings,
                replacementOf, replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm withMembers(Set<UUID> nextMembers) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset,
                nextMembers, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, failure,
                displayName, description, listed, managers, bans, settings, replacementOf, replacedBy, archivedAt,
                lifecycleOperation);
    }

    public Realm withAccessPolicy(RealmAccessPolicy nextPolicy) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset,
                members, invitedVisitors, Objects.requireNonNull(nextPolicy, "nextPolicy"),
                createdAt, schemaVersion, operation, failure, displayName, description, listed, managers, bans,
                settings, replacementOf, replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm withIdentity(String nextName, String nextDescription, boolean nextListed) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset, members, invitedVisitors,
                accessPolicy, createdAt, schemaVersion, operation, failure, nextName, nextDescription, nextListed,
                managers, bans, settings, replacementOf, replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm withRoles(Set<UUID> nextMembers, Set<UUID> nextManagers) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset, nextMembers, invitedVisitors,
                accessPolicy, createdAt, schemaVersion, operation, failure, displayName, description, listed,
                nextManagers, bans, settings, replacementOf, replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm withBans(Map<UUID, RealmBan> nextBans) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset, members, invitedVisitors,
                accessPolicy, createdAt, schemaVersion, operation, failure, displayName, description, listed,
                managers, nextBans, settings, replacementOf, replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm withSettings(RealmSettings nextSettings) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset, members, invitedVisitors,
                accessPolicy, createdAt, schemaVersion, operation, failure, displayName, description, listed,
                managers, bans, nextSettings, replacementOf, replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm withLifecycleOperation(Optional<RealmLifecycleOperation> nextOperation) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset, members, invitedVisitors,
                accessPolicy, createdAt, schemaVersion, operation, failure, displayName, description, listed,
                managers, bans, settings, replacementOf, replacedBy, archivedAt, nextOperation);
    }

    public Realm asReplacementOf(RealmId source) {
        return new Realm(id, owner, state, dimension, allocation, spawn, preset, members, invitedVisitors,
                accessPolicy, createdAt, schemaVersion, operation, failure, displayName, description, listed,
                managers, bans, settings, Optional.of(source), replacedBy, archivedAt, lifecycleOperation);
    }

    public Realm archive(CreationTimestamp timestamp, Optional<RealmId> replacement) {
        return new Realm(id, owner, RealmLifecycleState.ARCHIVED, dimension, allocation, spawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, Optional.empty(),
                displayName, description, false, managers, bans, settings, replacementOf, replacement,
                Optional.of(timestamp), Optional.empty());
    }

    public Realm restore() {
        if (state != RealmLifecycleState.ARCHIVED) {
            throw new IllegalStateException("only an archived realm may be restored");
        }
        return new Realm(id, owner, RealmLifecycleState.ACTIVE, dimension, allocation, spawn, preset,
                members, invitedVisitors, accessPolicy, createdAt, schemaVersion, operation, Optional.empty(),
                displayName, description, false, managers, bans, settings, replacementOf, replacedBy,
                Optional.empty(), Optional.empty());
    }

    private static String defaultDisplayName(RealmId id) {
        return "Realm #" + id.value();
    }

    private static String validateDisplayName(String value) {
        String normalized = value.strip();
        if (normalized.length() < 1 || normalized.length() > 48 || hasUnsafeText(normalized)) {
            throw new IllegalArgumentException("invalid realm display name");
        }
        return normalized;
    }

    private static String validateDescription(String value) {
        String normalized = value.strip();
        if (normalized.length() > 240 || hasUnsafeText(normalized)) {
            throw new IllegalArgumentException("invalid realm description");
        }
        return normalized;
    }

    private static boolean hasUnsafeText(String value) {
        return value.codePoints().anyMatch(point -> Character.isISOControl(point)
                || point == '\n' || point == '\r' || point == '<' || point == '>');
    }
}
