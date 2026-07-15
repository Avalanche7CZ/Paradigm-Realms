package eu.avalanche7.paradigmrealms.platform;

import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.player.PlayerDirectory;

public interface RealmsPlatformAdapter {
    CommandPlatform commands();
    CommandPermissionGate permissions();
    CommandMessageService messages();
    PlayerDirectory players();
}
