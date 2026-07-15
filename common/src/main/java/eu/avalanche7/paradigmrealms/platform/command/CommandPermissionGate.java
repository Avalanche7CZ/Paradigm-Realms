package eu.avalanche7.paradigmrealms.platform.command;

import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;

@FunctionalInterface
public interface CommandPermissionGate {
    boolean allowed(CommandSource source, RealmPermissionNode permission);
}
