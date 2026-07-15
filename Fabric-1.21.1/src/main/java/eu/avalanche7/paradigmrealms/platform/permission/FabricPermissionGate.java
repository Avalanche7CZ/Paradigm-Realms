package eu.avalanche7.paradigmrealms.platform.permission;

import eu.avalanche7.paradigmrealms.integration.permission.PermissionService;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

public final class FabricPermissionGate implements CommandPermissionGate {
    private final PermissionService fallback;
    private volatile PermissionService permissions;

    public FabricPermissionGate() {
        this(new Vanilla());
    }

    public FabricPermissionGate(PermissionService permissions) {
        this.fallback = permissions;
        this.permissions = permissions;
    }

    public void install(PermissionService integration) {
        this.permissions = java.util.Objects.requireNonNull(integration, "integration");
    }

    public void reset() {
        this.permissions = fallback;
    }

    public boolean hasPermission(ServerCommandSource source, String permission, int fallbackOpLevel) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            return source.hasPermissionLevel(fallbackOpLevel);
        }
        int vanillaLevel = source.getServer().getPermissionLevel(player.getGameProfile());
        PlayerReference reference = new PlayerReference(
                player.getUuid(), player.getGameProfile().getName(), vanillaLevel);
        return permissions.hasPermission(reference, permission, fallbackOpLevel);
    }

    @Override
    public boolean allowed(CommandSource source, RealmPermissionNode permission) {
        return source.player()
                .map(player -> permissions.hasPermission(
                        player, permission.node(), permission.fallbackOpLevel()))
                .orElseGet(() -> source.hasPermissionLevel(permission.fallbackOpLevel()));
    }

    public static final class Vanilla implements PermissionService {
        @Override public boolean hasPermission(
                PlayerReference player, String permission, int fallbackOpLevel) {
            java.util.Objects.requireNonNull(player, "player");
            if (permission == null || permission.isBlank()) {
                throw new IllegalArgumentException("permission cannot be blank");
            }
            if (fallbackOpLevel < 0 || fallbackOpLevel > 4) {
                throw new IllegalArgumentException(
                        "fallback operator level must be between 0 and 4");
            }
            return player.vanillaPermissionLevel() >= fallbackOpLevel;
        }
    }
}
