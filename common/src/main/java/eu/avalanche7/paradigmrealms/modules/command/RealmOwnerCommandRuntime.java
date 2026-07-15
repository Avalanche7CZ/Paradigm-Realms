package eu.avalanche7.paradigmrealms.modules.command;

import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.application.RealmDirectoryService;
import eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService;
import eu.avalanche7.paradigmrealms.application.RealmVisitService;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmMemberRole;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSetting;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.access.AccessRole;
import java.util.List;
import eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService;

public interface RealmOwnerCommandRuntime {
    RealmOwnerManagementService.Result setRealmName(UUID actor, String name);
    RealmOwnerManagementService.Result setRealmDescription(UUID actor, String description);
    RealmOwnerManagementService.Result setRealmListed(UUID actor, boolean listed);
    RealmOwnerManagementService.Result setRealmRole(UUID actor, UUID target, RealmMemberRole role);
    RealmOwnerManagementService.Result banFromRealm(
            UUID actor, UUID target, String targetName, Optional<String> reason);
    RealmOwnerManagementService.Result unbanFromRealm(UUID actor, UUID target);
    RealmOwnerManagementService.Result setRealmSetting(UUID actor, RealmSetting setting, boolean value);
    Optional<Realm> managedRealm(UUID actor);
    Optional<Realm> realmById(RealmId id);
    RealmDirectoryService.Page publicRealms(int page);
    RealmVisitService.Decision evaluateVisit(UUID visitor, UUID owner);
    KickResult kickFromRealm(UUID actor, UUID target);
    TeleportResult visit(UUID player, Realm realm);
    default List<Occupant> realmOccupants(UUID actor) { return List.of(); }
    default RealmOwnershipTransferService.Result offerTransfer(
            UUID owner, String ownerName, UUID target, String targetName) {
        return RealmOwnershipTransferService.Result.of(RealmOwnershipTransferService.Status.NOT_FOUND);
    }
    default RealmOwnershipTransferService.Result acceptTransfer(UUID target, UUID owner) {
        return RealmOwnershipTransferService.Result.of(RealmOwnershipTransferService.Status.NOT_FOUND);
    }
    default RealmOwnershipTransferService.Result declineTransfer(UUID target, UUID owner) {
        return RealmOwnershipTransferService.Result.of(RealmOwnershipTransferService.Status.NOT_FOUND);
    }
    default RealmOwnershipTransferService.Result cancelTransfer(UUID owner) {
        return RealmOwnershipTransferService.Result.of(RealmOwnershipTransferService.Status.NOT_FOUND);
    }

    record Occupant(UUID player, AccessRole role) {}

    enum KickResult {
        KICKED,
        NO_REALM,
        FORBIDDEN_TARGET,
        NOT_PRESENT,
        EVACUATION_FAILED
    }
}
