package eu.avalanche7.paradigmrealms.message;

import eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService;
import eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService;
import eu.avalanche7.paradigmrealms.membership.MembershipStatus;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.protection.ProtectionReason;

public final class PlayerMessageMappings {
    private PlayerMessageMappings() {}

    public static String membershipFailure(MembershipStatus status) {
        return switch (status) {
            case NO_REALM -> "No matching realm was found.";
            case REALM_NOT_ACTIVE -> "That realm is not active right now.";
            case NOT_OWNER -> "Only the realm owner can do that.";
            case OWNER_CANNOT_BE_TARGET -> "The realm owner cannot be selected for that action.";
            case ALREADY_MEMBER -> "That player already belongs to the realm.";
            case NOT_MEMBER -> "That player is not a member of the realm.";
            case INVITATION_NOT_FOUND -> "That realm invitation no longer exists.";
            case INVITATION_EXPIRED -> "That realm invitation has expired.";
            case MAXIMUM_MEMBERS -> "This realm has reached its member limit.";
            case MAXIMUM_PENDING_INVITATIONS -> "This realm has too many pending invitations.";
            case BANNED -> "That player is banned from the realm.";
            case FORBIDDEN_TARGET -> "You cannot manage that player from your current role.";
            case OPERATION_IN_PROGRESS -> "This realm is busy with another operation.";
            case NO_CHANGE -> "Nothing needed to change.";
            default -> "The realm membership change could not be completed.";
        };
    }

    public static String ownerMutationFailure(RealmOwnerManagementService.Status status) {
        return switch (status) {
            case NO_REALM -> "You do not own or manage a realm.";
            case FORBIDDEN -> "Your realm role does not allow that change.";
            case INVALID_TARGET -> "That player cannot be selected for this change.";
            case NOT_FOUND -> "The requested realm entry was not found.";
            case SERVER_LOCKED -> "That realm setting is locked by the server.";
            case OPERATION_IN_PROGRESS -> "This realm is busy with another operation.";
            case CHANGED -> "Realm updated.";
        };
    }

    public static String transferFailure(RealmOwnershipTransferService.Status status) {
        return switch (status) {
            case NO_REALM -> "You do not have an active realm to transfer.";
            case NOT_FOUND -> "That ownership transfer offer no longer exists.";
            case INVALID_TARGET -> "That player cannot receive this realm.";
            case TARGET_ALREADY_OWNS_REALM -> "That player already owns an active realm.";
            case TARGET_BANNED -> "Unban that player before transferring the realm.";
            case LIFECYCLE_CONFLICT -> "The realm is busy with another operation.";
            case TRANSFER_CONFLICT -> "Another ownership transfer is already pending.";
            default -> "The ownership transfer could not be completed.";
        };
    }

    public static String teleportFailure(TeleportResult result) {
        return switch (result) {
            case REALM_NOT_ACTIVE -> "That realm is not active right now.";
            case WORLD_UNAVAILABLE -> "The destination world is unavailable right now.";
            case OUTSIDE_BOUNDS -> "The saved destination is outside the realm boundary.";
            case OUTSIDE_WORLD_BORDER -> "The saved destination is outside the world border.";
            case UNSAFE_DESTINATION -> "The destination is not safe right now.";
            case RIDING_OR_HAS_PASSENGERS -> "Dismount before teleporting.";
            case SUCCESS -> "Teleport completed.";
        };
    }

    public static String protectionDenial(ProtectionReason reason) {
        return switch (reason) {
            case GUARD_REGION -> "The guard area between realms cannot be changed.";
            case UNALLOCATED_REALMS_SPACE -> "This space is not part of an active realm.";
            case NOT_A_MEMBER -> "You do not have permission to change this realm.";
            case PRIVATE_REALM -> "This realm is private.";
            case VISITOR_READ_ONLY -> "Visitors cannot change that here.";
            case REALM_NOT_ACTIVE -> "This realm is temporarily unavailable.";
            case ENVIRONMENTAL_BOUNDARY -> "That action would cross a realm boundary.";
            default -> "That action is protected in this realm.";
        };
    }
}
