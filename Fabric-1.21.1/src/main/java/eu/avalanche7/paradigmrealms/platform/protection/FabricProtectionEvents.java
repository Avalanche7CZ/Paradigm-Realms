package eu.avalanche7.paradigmrealms.platform.protection;

import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorStandItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BoatItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.DecorationItem;
import net.minecraft.item.FireChargeItem;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MinecartItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;

public final class FabricProtectionEvents {
    private FabricProtectionEvents() {}

    public static void register(FabricProtectionService protection) {
        PlayerBlockBreakEvents.BEFORE.register((world, player, position, state, blockEntity) ->
                !(player instanceof ServerPlayerEntity serverPlayer)
                        || protection.allowOrNotify(
                                serverPlayer, world, position, ProtectionAction.BLOCK_BREAK));

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            Item item = stack.getItem();
            BlockPos target = hit.getBlockPos();
            ProtectionAction action = ProtectionAction.BLOCK_USE;
            if (item instanceof BlockItem) {
                target = new ItemPlacementContext(player, hand, stack, hit).getBlockPos();
                action = ProtectionAction.BLOCK_PLACE;
            } else if (item instanceof BucketItem) {
                target = hit.getBlockPos().offset(hit.getSide());
                action = ProtectionAction.FLUID_EMPTY;
            } else if (item instanceof BoatItem || item instanceof MinecartItem) {
                target = hit.getBlockPos().offset(hit.getSide());
                action = ProtectionAction.VEHICLE_PLACE;
            } else if (item instanceof DecorationItem || item instanceof ArmorStandItem) {
                target = hit.getBlockPos().offset(hit.getSide());
                action = ProtectionAction.HANGING_ENTITY_PLACE;
            } else if (item instanceof SpawnEggItem) {
                target = hit.getBlockPos().offset(hit.getSide());
                action = ProtectionAction.ENTITY_INTERACT;
            } else if (item instanceof FlintAndSteelItem || item instanceof FireChargeItem) {
                target = hit.getBlockPos().offset(hit.getSide());
                action = ProtectionAction.ITEM_USE_ON_BLOCK;
            }
            return protection.allowOrNotify(serverPlayer, world, target, action)
                    ? ActionResult.PASS : ActionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !isMutationItem(stack.getItem())) {
                return TypedActionResult.pass(stack);
            }
            boolean allowed = protection.allowOrNotify(
                    serverPlayer, world, player.getBlockPos(), ProtectionAction.ITEM_USE_ON_BLOCK);
            return allowed ? TypedActionResult.pass(stack) : TypedActionResult.fail(stack);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            ProtectionAction action = entity.hasPassengers() || entity.hasVehicle()
                    ? ProtectionAction.VEHICLE_USE : ProtectionAction.ENTITY_INTERACT;
            return protection.allowOrNotify(serverPlayer, world, entity.getBlockPos(), action)
                    ? ActionResult.PASS : ActionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.PASS;
            return protection.allowOrNotify(
                    serverPlayer, world, entity.getBlockPos(), ProtectionAction.ENTITY_DAMAGE)
                    ? ActionResult.PASS : ActionResult.FAIL;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                protection.allowDamage(entity, source));
    }

    private static boolean isMutationItem(Item item) {
        return item instanceof BlockItem
                || item instanceof BucketItem
                || item instanceof BoatItem
                || item instanceof MinecartItem
                || item instanceof DecorationItem
                || item instanceof ArmorStandItem
                || item instanceof SpawnEggItem
                || item instanceof FlintAndSteelItem
                || item instanceof FireChargeItem;
    }
}
