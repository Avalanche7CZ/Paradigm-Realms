package eu.avalanche7.paradigmrealms.modules.command;

import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;

final class RealmAdminCommandAccess {
    private static final RealmPermissionNode[] ADMIN_PERMISSIONS = {
            RealmPermissionNodes.ADMIN_INSPECT,
            RealmPermissionNodes.ADMIN_PRESETS,
            RealmPermissionNodes.ADMIN_BYPASS,
            RealmPermissionNodes.ADMIN_WILDS_STATUS,
            RealmPermissionNodes.ADMIN_WILDS_VALIDATE,
            RealmPermissionNodes.ADMIN_WILDS_MANAGE,
            RealmPermissionNodes.ADMIN_WILDS_RESET,
            RealmPermissionNodes.ADMIN_WILDS_BACKUPS
    };

    private RealmAdminCommandAccess() {}

    static boolean allowedAny(CommandSource source, CommandPermissionGate permissions) {
        for (RealmPermissionNode permission : ADMIN_PERMISSIONS) {
            if (permissions.allowed(source, permission)) return true;
        }
        return false;
    }
}
