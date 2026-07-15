package eu.avalanche7.paradigmrealms.protection;

import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.access.AccessRole;
import eu.avalanche7.paradigmrealms.access.RealmAccessService;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperationStage;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.RealmRegionIndex;
import eu.avalanche7.paradigmrealms.region.RealmRegionKind;
import eu.avalanche7.paradigmrealms.region.RealmRegionMatch;
import eu.avalanche7.paradigmrealms.config.RealmSettingsPolicy;

public final class ProtectionPolicyService {
    private final RealmAccessService access = new RealmAccessService();
    private final RealmSettingsPolicy settingsPolicy;

    public ProtectionPolicyService() {
        this(RealmSettingsPolicy.secureDefaults());
    }

    public ProtectionPolicyService(RealmSettingsPolicy settingsPolicy) {
        this.settingsPolicy = java.util.Objects.requireNonNull(settingsPolicy, "settingsPolicy");
    }

    public ProtectionDecision evaluate(RealmRegionIndex index, ProtectionRequest request) {
        if (!request.dimension().equals(DimensionId.REALMS)) {
            return decision(true, ProtectionReason.OUTSIDE_REALMS_DIMENSION, null,
                    AccessRole.UNAUTHORIZED, RealmRegionKind.UNALLOCATED_REALMS_SPACE, false);
        }
        RealmRegionMatch match = index.resolve(request.target());
        if (match.kind() == RealmRegionKind.UNALLOCATED_REALMS_SPACE) {
            return nonBuildable(request, match, ProtectionReason.UNALLOCATED_REALMS_SPACE);
        }
        Realm realm = match.realm().orElseThrow();
        if (match.kind() == RealmRegionKind.GUARD_REGION) {
            return nonBuildable(request, match, ProtectionReason.GUARD_REGION);
        }
        if (realm.state() != RealmLifecycleState.ACTIVE) {
            return decision(false, ProtectionReason.REALM_NOT_ACTIVE, realm,
                    role(realm, request.effectiveActor()), match.kind(), false);
        }
        if (realm.lifecycleOperation().map(operation -> operation.stage() == RealmLifecycleOperationStage.ENTRY_BLOCKED
                || operation.stage() == RealmLifecycleOperationStage.OCCUPANTS_EVACUATED).orElse(false)) {
            return decision(false, ProtectionReason.REALM_NOT_ACTIVE, realm,
                    role(realm, request.effectiveActor()), match.kind(), false);
        }

        AccessRole role = role(realm, request.effectiveActor());
        if (request.action() == ProtectionAction.REALM_ENTRY) {
            if (role == AccessRole.OWNER || role == AccessRole.MANAGER
                    || role == AccessRole.MEMBER || role == AccessRole.VISITOR) {
                return decision(true, ProtectionReason.ALLOWED, realm, role, match.kind(), false);
            }
            if (request.adminBypassActive()) {
                return decision(true, ProtectionReason.ALLOWED_ADMIN_BYPASS, realm, role, match.kind(), true);
            }
            ProtectionReason reason = realm.accessPolicy() == RealmAccessPolicy.PUBLIC_VISIT
                    ? ProtectionReason.NOT_A_MEMBER : ProtectionReason.PRIVATE_REALM;
            return decision(false, reason, realm, role, match.kind(), false);
        }

        if (role == AccessRole.OWNER || role == AccessRole.MANAGER || role == AccessRole.MEMBER) {
            return decision(true, ProtectionReason.ALLOWED, realm, role, match.kind(), false);
        }
        if (role == AccessRole.VISITOR && visitorSettingAllows(realm, request.action())) {
            return decision(true, ProtectionReason.ALLOWED, realm, role, match.kind(), false);
        }
        if (request.adminBypassActive()) {
            return decision(true, ProtectionReason.ALLOWED_ADMIN_BYPASS, realm, role, match.kind(), true);
        }
        ProtectionReason reason = role == AccessRole.VISITOR
                ? ProtectionReason.VISITOR_READ_ONLY : ProtectionReason.NOT_A_MEMBER;
        return decision(false, reason, realm, role, match.kind(), false);
    }

    public boolean explosionsAllowed(RealmRegionIndex index, BlockCoordinate origin) {
        RealmRegionMatch match = index.resolve(origin);
        return match.kind() == RealmRegionKind.BUILDABLE_REALM_REGION
                && match.realm().filter(realm -> realm.state() == RealmLifecycleState.ACTIVE)
                        .map(realm -> settingsPolicy.effective(realm.settings()).explosions()).orElse(false);
    }

    public boolean pvpAllowed(RealmRegionIndex index, BlockCoordinate target) {
        RealmRegionMatch match = index.resolve(target);
        return match.kind() != RealmRegionKind.BUILDABLE_REALM_REGION
                || match.realm().filter(realm -> realm.state() == RealmLifecycleState.ACTIVE)
                        .map(realm -> settingsPolicy.effective(realm.settings()).pvp()).orElse(false);
    }

    public boolean mobGriefingAllowed(RealmRegionIndex index, BlockCoordinate target) {
        RealmRegionMatch match = index.resolve(target);
        return match.kind() != RealmRegionKind.BUILDABLE_REALM_REGION
                || match.realm().filter(realm -> realm.state() == RealmLifecycleState.ACTIVE)
                        .map(realm -> settingsPolicy.effective(realm.settings()).mobGriefing()).orElse(false);
    }

    private boolean visitorSettingAllows(Realm realm, ProtectionAction action) {
        var settings = settingsPolicy.effective(realm.settings());
        if (action == ProtectionAction.CONTAINER_OPEN) return settings.visitorContainers();
        return settings.visitorInteraction() && (action == ProtectionAction.BLOCK_USE
                || action == ProtectionAction.ENTITY_INTERACT || action == ProtectionAction.VEHICLE_USE);
    }

    public boolean allowsEnvironmentalMutation(
            RealmRegionIndex index, BlockCoordinate source, BlockCoordinate destination) {
        RealmRegionMatch from = index.resolve(source);
        RealmRegionMatch to = index.resolve(destination);
        if (from.kind() != RealmRegionKind.BUILDABLE_REALM_REGION
                || to.kind() != RealmRegionKind.BUILDABLE_REALM_REGION) {
            return false;
        }
        Realm sourceRealm = from.realm().orElseThrow();
        Realm targetRealm = to.realm().orElseThrow();
        return sourceRealm.state() == RealmLifecycleState.ACTIVE
                && targetRealm.state() == RealmLifecycleState.ACTIVE
                && sourceRealm.id().equals(targetRealm.id());
    }

    private ProtectionDecision nonBuildable(
            ProtectionRequest request, RealmRegionMatch match, ProtectionReason deniedReason) {
        if (request.action() == ProtectionAction.REALM_ENTRY) {
            return decision(true, ProtectionReason.ALLOWED, match.realm().orElse(null),
                    AccessRole.UNAUTHORIZED, match.kind(), false);
        }
        if (request.adminBypassActive()) {
            return decision(true, ProtectionReason.ALLOWED_ADMIN_BYPASS, match.realm().orElse(null),
                    AccessRole.UNAUTHORIZED, match.kind(), true);
        }
        return decision(false, deniedReason, match.realm().orElse(null),
                AccessRole.UNAUTHORIZED, match.kind(), false);
    }

    private AccessRole role(Realm realm, Optional<UUID> actor) {
        return actor.map(uuid -> access.roleOf(realm, uuid)).orElse(AccessRole.UNAUTHORIZED);
    }

    private static ProtectionDecision decision(
            boolean allowed,
            ProtectionReason reason,
            Realm realm,
            AccessRole role,
            RealmRegionKind kind,
            boolean bypass) {
        return new ProtectionDecision(allowed, reason,
                Optional.ofNullable(realm).map(Realm::id), role, kind, bypass);
    }
}
