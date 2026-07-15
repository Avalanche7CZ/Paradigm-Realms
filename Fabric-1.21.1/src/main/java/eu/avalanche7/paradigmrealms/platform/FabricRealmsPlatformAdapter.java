package eu.avalanche7.paradigmrealms.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;

import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.FabricCommandPlatform;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.message.MessageRouter;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;
import eu.avalanche7.paradigmrealms.platform.player.PlayerDirectory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;

public final class FabricRealmsPlatformAdapter implements RealmsPlatformAdapter {
    private final CommandPlatform commands;
    private final FabricPermissionGate permissions;
    private final MessageRouter messages;
    private final PlayerDirectory players = new Players();

    public FabricRealmsPlatformAdapter(
            CommandDispatcher<ServerCommandSource> dispatcher,
            FabricPermissionGate permissions,
            MessageRouter messages) {
        this.commands = new FabricCommandPlatform(dispatcher);
        this.permissions = java.util.Objects.requireNonNull(permissions, "permissions");
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
    }

    @Override public CommandPlatform commands() { return commands; }
    @Override public CommandPermissionGate permissions() { return permissions; }
    @Override public CommandMessageService messages() { return messages; }
    @Override public PlayerDirectory players() { return players; }

    private static final class Players implements PlayerDirectory {
        @Override public List<String> onlineNames(CommandSource source) {
            return server(source).getPlayerManager().getPlayerList().stream()
                    .map(player -> player.getGameProfile().getName()).toList();
        }

        @Override public Optional<PlayerIdentity> onlineExact(CommandSource source, String name) {
            return server(source).getPlayerManager().getPlayerList().stream()
                    .filter(player -> player.getGameProfile().getName().equalsIgnoreCase(name))
                    .findFirst().map(player -> new PlayerIdentity(
                            player.getUuid(), player.getGameProfile().getName()));
        }

        @Override public Optional<PlayerIdentity> cached(CommandSource source, UUID uuid) {
            return server(source).getUserCache().getByUuid(uuid)
                    .map(profile -> new PlayerIdentity(uuid, profile.getName()));
        }

        @Override public Optional<CommandSource> onlineSource(CommandSource source, UUID uuid) {
            return Optional.ofNullable(server(source).getPlayerManager().getPlayer(uuid))
                    .map(player -> new FabricCommandPlatform.Source(player.getCommandSource()));
        }

        private static MinecraftServer server(CommandSource source) {
            if (!(source instanceof FabricCommandPlatform.Source fabric)) {
                throw new IllegalArgumentException("command source belongs to another platform");
            }
            return fabric.original().getServer();
        }
    }
}
