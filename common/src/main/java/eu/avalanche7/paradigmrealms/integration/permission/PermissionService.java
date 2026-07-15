package eu.avalanche7.paradigmrealms.integration.permission;

@FunctionalInterface
public interface PermissionService {
    boolean hasPermission(PlayerReference player, String permission, int fallbackOpLevel);
}
