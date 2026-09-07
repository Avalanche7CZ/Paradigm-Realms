package eu.avalanche7.paradigmrealms.message;

import eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService;
import eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService;
import eu.avalanche7.paradigmrealms.membership.MembershipStatus;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.protection.ProtectionReason;

public final class PlayerMessageMappings {
    private PlayerMessageMappings() {}

    public static String membershipFailure(MembershipStatus status) {
        String key = switch (status) {
            case NO_REALM -> "no_realm";
            case REALM_NOT_ACTIVE -> "realm_not_active";
            case NOT_OWNER -> "not_owner";
            case OWNER_CANNOT_BE_TARGET -> "owner_target";
            case ALREADY_MEMBER -> "already_member";
            case NOT_MEMBER -> "not_member";
            case INVITATION_NOT_FOUND -> "invitation_not_found";
            case INVITATION_EXPIRED -> "invitation_expired";
            case MAXIMUM_MEMBERS -> "maximum_members";
            case MAXIMUM_PENDING_INVITATIONS -> "maximum_invitations";
            case BANNED -> "banned";
            case FORBIDDEN_TARGET -> "forbidden_target";
            case OPERATION_IN_PROGRESS -> "operation_in_progress";
            case NO_CHANGE -> "no_change";
            default -> "generic";
        };
        return PlayerMessages.text("errors.membership." + key);
    }

    public static String ownerMutationFailure(RealmOwnerManagementService.Status status) {
        String key = switch (status) {
            case NO_REALM -> "no_realm";
            case FORBIDDEN -> "forbidden";
            case INVALID_TARGET -> "invalid_target";
            case NOT_FOUND -> "not_found";
            case SERVER_LOCKED -> "server_locked";
            case OPERATION_IN_PROGRESS -> "operation_in_progress";
            case CHANGED -> "changed";
        };
        return PlayerMessages.text("errors.owner_mutation." + key);
    }

    public static String transferFailure(RealmOwnershipTransferService.Status status) {
        String key = switch (status) {
            case NO_REALM -> "no_realm";
            case NOT_FOUND -> "not_found";
            case INVALID_TARGET -> "invalid_target";
            case TARGET_ALREADY_OWNS_REALM -> "target_owns_realm";
            case TARGET_BANNED -> "target_banned";
            case LIFECYCLE_CONFLICT -> "lifecycle_conflict";
            case TRANSFER_CONFLICT -> "transfer_conflict";
            default -> "generic";
        };
        return PlayerMessages.text("errors.transfer." + key);
    }

    public static String teleportFailure(TeleportResult result) {
        String key = switch (result) {
            case REALM_NOT_ACTIVE -> "realm_not_active";
            case WORLD_UNAVAILABLE -> "world_unavailable";
            case OUTSIDE_BOUNDS -> "outside_bounds";
            case OUTSIDE_WORLD_BORDER -> "outside_border";
            case UNSAFE_DESTINATION -> "unsafe";
            case RIDING_OR_HAS_PASSENGERS -> "riding";
            case SUCCESS -> "success";
        };
        return PlayerMessages.text("errors.teleport." + key);
    }

    public static String protectionDenial(ProtectionReason reason) {
        String key = switch (reason) {
            case GUARD_REGION -> "guard_region";
            case UNALLOCATED_REALMS_SPACE -> "unallocated";
            case NOT_A_MEMBER -> "not_member";
            case PRIVATE_REALM -> "private";
            case VISITOR_READ_ONLY -> "visitor_read_only";
            case REALM_NOT_ACTIVE -> "realm_not_active";
            case ENVIRONMENTAL_BOUNDARY -> "boundary";
            default -> "generic";
        };
        return PlayerMessages.text("errors.protection." + key);
    }
}
