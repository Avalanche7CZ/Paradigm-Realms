package eu.avalanche7.paradigmrealms.platform.player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.platform.command.CommandSource;

public interface PlayerDirectory {
    List<String> onlineNames(CommandSource source);
    Optional<PlayerIdentity> onlineExact(CommandSource source, String name);
    Optional<PlayerIdentity> cached(CommandSource source, UUID uuid);
    Optional<CommandSource> onlineSource(CommandSource source, UUID uuid);
}
