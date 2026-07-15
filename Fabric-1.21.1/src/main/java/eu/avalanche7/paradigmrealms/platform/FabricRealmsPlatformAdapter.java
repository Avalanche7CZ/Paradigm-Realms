package eu.avalanche7.paradigmrealms.platform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.nio.file.Files;

import com.mojang.brigadier.CommandDispatcher;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.FabricCommandPlatform;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.message.MessageRouter;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;
import eu.avalanche7.paradigmrealms.platform.player.PlayerDirectory;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentityResolution;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import com.google.gson.JsonParser;
import eu.avalanche7.paradigmrealms.platform.integration.OptionalIntegrationBootstrap;

public final class FabricRealmsPlatformAdapter implements RealmsPlatformAdapter {
    private static volatile Map<String, List<PlayerIdentity>> cachedByName = Map.of();
    private static volatile Map<UUID, PlayerIdentity> cachedByUuid = Map.of();
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
    @Override public PlatformMetadata metadata() {
        FabricLoader loader = FabricLoader.getInstance();
        String mod = loader.getModContainer(ParadigmRealms.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        String fabric = loader.getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        return new PlatformMetadata(
                mod, SharedConstants.getGameVersion().getName(), "Fabric", fabric,
                OptionalIntegrationBootstrap.status(), "1");
    }

    public static void loadPlayerCache(MinecraftServer server) {
        LinkedHashMap<String, List<PlayerIdentity>> byName = new LinkedHashMap<>();
        LinkedHashMap<UUID, PlayerIdentity> byUuid = new LinkedHashMap<>();
        try {
            var path = server.getRunDirectory().resolve("usercache.json");
            if (Files.isRegularFile(path)) {
                try (var reader = Files.newBufferedReader(path)) {
                    for (var value : JsonParser.parseReader(reader).getAsJsonArray()) {
                        var object = value.getAsJsonObject();
                        UUID uuid = UUID.fromString(object.get("uuid").getAsString());
                        String name = object.get("name").getAsString();
                        PlayerIdentity identity = new PlayerIdentity(uuid, name);
                        byUuid.put(uuid, identity);
                        byName.computeIfAbsent(name.toLowerCase(Locale.ROOT), ignored -> new java.util.ArrayList<>())
                                .add(identity);
                    }
                }
            }
        } catch (Exception exception) {
            ParadigmRealms.LOGGER.warn("Could not read the local Minecraft player cache: {}",
                    exception.getClass().getSimpleName());
        }
        server.getPlayerManager().getPlayerList().forEach(player -> {
            PlayerIdentity identity = new PlayerIdentity(player.getUuid(), player.getGameProfile().getName());
            byUuid.put(identity.uuid(), identity);
            byName.computeIfAbsent(identity.name().toLowerCase(Locale.ROOT), ignored -> new java.util.ArrayList<>())
                    .removeIf(existing -> existing.uuid().equals(identity.uuid()));
            byName.get(identity.name().toLowerCase(Locale.ROOT)).add(identity);
        });
        cachedByUuid = Map.copyOf(byUuid);
        LinkedHashMap<String, List<PlayerIdentity>> immutableNames = new LinkedHashMap<>();
        byName.forEach((name, identities) -> immutableNames.put(name, List.copyOf(identities)));
        cachedByName = Map.copyOf(immutableNames);
    }

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
            PlayerIdentity snapshot = cachedByUuid.get(uuid);
            if (snapshot != null) return Optional.of(snapshot);
            return server(source).getUserCache().getByUuid(uuid)
                    .map(profile -> new PlayerIdentity(uuid, profile.getName()));
        }

        @Override public Optional<CommandSource> onlineSource(CommandSource source, UUID uuid) {
            return Optional.ofNullable(server(source).getPlayerManager().getPlayer(uuid))
                    .map(player -> new FabricCommandPlatform.Source(player.getCommandSource()));
        }

        @Override public PlayerIdentityResolution resolveCached(CommandSource source, String name) {
            Optional<PlayerIdentity> online = onlineExact(source, name);
            if (online.isPresent()) return PlayerIdentityResolution.found(online.orElseThrow());
            List<PlayerIdentity> matches = cachedByName.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
            if (matches.isEmpty()) return PlayerIdentityResolution.unknown();
            if (matches.size() > 1) return PlayerIdentityResolution.ambiguous();
            return PlayerIdentityResolution.found(matches.getFirst());
        }

        @Override public List<String> cachedNames(CommandSource source) {
            return cachedByUuid.values().stream().map(PlayerIdentity::name)
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }

        private static MinecraftServer server(CommandSource source) {
            if (!(source instanceof FabricCommandPlatform.Source fabric)) {
                throw new IllegalArgumentException("command source belongs to another platform");
            }
            return fabric.original().getServer();
        }
    }
}
