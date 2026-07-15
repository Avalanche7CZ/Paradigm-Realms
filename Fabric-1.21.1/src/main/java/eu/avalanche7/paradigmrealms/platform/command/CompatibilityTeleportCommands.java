package eu.avalanche7.paradigmrealms.platform.command;

import static net.minecraft.server.command.CommandManager.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public final class CompatibilityTeleportCommands {
    public static final String ENABLE_PROPERTY = "paradigmrealms.devCompatibilityCommands";
    private static final String PERMISSION = RealmPermissionNodes.COMPATIBILITY.node();
    private static final int FALLBACK_OP_LEVEL = RealmPermissionNodes.COMPATIBILITY.fallbackOpLevel();
    private static final RegistryKey<World> REALMS = worldKey("realms");
    private static final RegistryKey<World> WILDS = worldKey("wilds");

    private CompatibilityTeleportCommands() {}

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher, FabricPermissionGate permissions) {
        dispatcher.register(literal("realms")
                .then(literal("dev")
                        .requires(source -> permissions.hasPermission(
                                source, PERMISSION, FALLBACK_OP_LEVEL))
                        .then(literal("realms").executes(context -> teleport(context.getSource(), REALMS, true)))
                        .then(literal("wilds").executes(context -> teleport(context.getSource(), WILDS, false)))
                        .then(literal("overworld").executes(context -> teleport(
                                context.getSource(), World.OVERWORLD, false)))));
    }

    private static int teleport(
            ServerCommandSource source, RegistryKey<World> worldKey, boolean createTestPlatform)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = source.getServer().getWorld(worldKey);
        if (world == null) {
            source.sendError(Text.literal("Dimension is not loaded: " + worldKey.getValue()));
            return 0;
        }

        BlockPos destination = createTestPlatform
                ? ensureRealmsTestPlatform(world)
                : safeSurfaceSpawn(world);
        if (!world.getWorldBorder().contains(destination)) {
            source.sendError(Text.literal("Compatibility destination is outside the world border"));
            return 0;
        }
        player.stopRiding();
        player.teleport(world, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYaw(), player.getPitch());
        source.sendFeedback(() -> Text.literal("Teleported to " + worldKey.getValue()
                + " for compatibility testing"), false);
        return 1;
    }

    private static BlockPos safeSurfaceSpawn(ServerWorld world) {
        BlockPos spawn = world.getSpawnPos();
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        return new BlockPos(spawn.getX(), y, spawn.getZ());
    }

    private static BlockPos ensureRealmsTestPlatform(ServerWorld world) {
        BlockPos feet = new BlockPos(0, 80, 0);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(new BlockPos(x, feet.getY() - 1, z), Blocks.BEDROCK.getDefaultState(),
                        Block.NOTIFY_ALL);
            }
        }
        world.setBlockState(feet, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        world.setBlockState(feet.up(), Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        return feet;
    }

    private static RegistryKey<World> worldKey(String path) {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(ParadigmRealms.MOD_ID, path));
    }
}
