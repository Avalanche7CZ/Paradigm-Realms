package eu.avalanche7.paradigmrealms.platform.command;

import java.util.List;

import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;

public interface CommandContext {
    CommandSource source();
    String string(String name);
    int integer(String name);
    long longValue(String name);
    List<PlayerIdentity> playerProfiles(String name) throws Exception;
}
