package eu.avalanche7.paradigmrealms.platform.protection;

import java.util.List;

import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import eu.avalanche7.paradigmrealms.domain.DimensionId;

public final class FabricProtectionHooks {
    private static volatile FabricProtectionService service;

    private FabricProtectionHooks() {}

    public static void install(FabricProtectionService replacement) {
        service = replacement;
    }

    public static void clear() {
        service = null;
    }

    public static boolean allowPickup(PlayerEntity player, Entity entity, ProtectionAction action) {
        FabricProtectionService current = service;
        if (current == null || !(player instanceof ServerPlayerEntity serverPlayer)) return true;
        return current.allowOrNotify(serverPlayer, entity.getWorld(), entity.getBlockPos(), action);
    }

    public static boolean allowFarmland(World world, BlockPos position, Entity entity) {
        FabricProtectionService current = service;
        if (current == null || !(entity instanceof ServerPlayerEntity player)) return true;
        return current.allowOrNotify(player, world, position, ProtectionAction.FARMLAND_TRAMPLE);
    }

    public static boolean allowMobGrief(Entity entity, World world, BlockPos position) {
        FabricProtectionService current = service;
        if (current == null || entity instanceof PlayerEntity) return true;
        if (FabricProtectionService.responsiblePlayer(entity).isPresent()) return true;
        return current.mobGriefingAllowed(world, position);
    }

    public static boolean allowPlayerAction(
            ServerPlayerEntity player, World world, BlockPos position, ProtectionAction action) {
        FabricProtectionService current = service;
        return current == null || current.allowOrNotify(player, world, position, action);
    }

    public static boolean allowContainerMutation(ServerPlayerEntity player) {
        FabricProtectionService current = service;
        return current == null || current.backupMutationAllowed(
                player.getWorld(),
                player.getBlockPos());
    }

    public static void filterExplosion(Explosion explosion, World world, BlockPos origin) {
        FabricProtectionService current = service;
        if (current == null) return;
        List<BlockPos> affected = explosion.getAffectedBlocks();
        if (isWilds(world)) {
            affected.removeIf(target -> !current.environmentalMutationAllowed(world, target));
            java.util.Optional<java.util.UUID> wildsActor = FabricProtectionService.responsiblePlayer(explosion.getEntity());
            if (wildsActor.isPresent()) {
                ServerPlayerEntity player = world.getServer() == null ? null
                        : world.getServer().getPlayerManager().getPlayer(wildsActor.orElseThrow());
                if (player != null && !current.allowOrNotify(player, world, origin, ProtectionAction.BLOCK_BREAK)) {
                    affected.clear();
                }
            }
            return;
        }
        if (!isRealms(world)) return;
        if (!current.backupMutationAllowed(world, origin)) {
            affected.clear();
            return;
        }
        boolean actorMayModifyOrigin = true;
        java.util.Optional<java.util.UUID> actor = FabricProtectionService.responsiblePlayer(explosion.getEntity());
        if (actor.isPresent()) {
            ServerPlayerEntity player = world.getServer() == null
                    ? null : world.getServer().getPlayerManager().getPlayer(actor.orElseThrow());
            actorMayModifyOrigin = current.evaluate(
                    java.util.Optional.empty(), actor, world, origin,
                    ProtectionAction.BLOCK_BREAK,
                    player != null && current.bypassEnabled(player.getUuid())).allowed();
        }
        boolean allowedOrigin = actorMayModifyOrigin;
        affected.removeIf(target -> !allowedOrigin
                || !current.backupMutationAllowed(world, target)
                || !current.allowsExplosion(origin, target));
    }

    public static boolean allowPiston(
            World world, BlockPos piston, List<BlockPos> moved, List<BlockPos> broken,
            net.minecraft.util.math.Direction motionDirection) {
        FabricProtectionService current = service;
        if (current == null) return true;
        if (isWilds(world)) {
            if (!current.environmentalMutationAllowed(world, piston)) return false;
            for (BlockPos source : moved) {
                if (!current.environmentalMutationAllowed(world, source)
                        || !current.environmentalMutationAllowed(world, source.offset(motionDirection))) return false;
            }
            for (BlockPos target : broken) if (!current.environmentalMutationAllowed(world, target)) return false;
            return true;
        }
        if (!isRealms(world)) return true;
        if (!current.backupMutationAllowed(world, piston)) return false;
        for (BlockPos source : moved) {
            if (!current.backupMutationAllowed(world, source)
                    || !current.backupMutationAllowed(world, source.offset(motionDirection))
                    || !current.allowsEnvironmental(piston, source)
                    || !current.allowsEnvironmental(piston, source.offset(motionDirection))) {
                return false;
            }
        }
        for (BlockPos destroyed : broken) {
            if (!current.backupMutationAllowed(world, destroyed)
                    || !current.allowsEnvironmental(piston, destroyed)) return false;
        }
        return true;
    }

    private static boolean isRealms(World world) {
        return world.getRegistryKey().getValue().toString().equals(DimensionId.REALMS.toString());
    }

    private static boolean isWilds(World world) {
        return world.getRegistryKey().getValue().toString().equals(DimensionId.WILDS.toString());
    }
}
