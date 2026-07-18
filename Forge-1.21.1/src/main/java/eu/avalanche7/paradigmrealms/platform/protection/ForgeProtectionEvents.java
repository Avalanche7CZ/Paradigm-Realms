package eu.avalanche7.paradigmrealms.platform.protection;

import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Converts Forge mutation callbacks into the existing protection policy calls. */
public final class ForgeProtectionEvents {
    private final ForgeProtectionService protection;

    private ForgeProtectionEvents(ForgeProtectionService protection) {
        this.protection = protection;
    }

    public static void register(ForgeProtectionService protection) {
        MinecraftForge.EVENT_BUS.register(new ForgeProtectionEvents(protection));
    }

    @SubscribeEvent
    public void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity player)) return;
        if (!(event.getLevel() instanceof World world)) return;
        if (!protection.allowOrNotify(player, world, event.getPos(), ProtectionAction.BLOCK_BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        ItemStack stack = player.getStackInHand(event.getHand());
        Item item = stack.getItem();
        BlockPos target = event.getPos();
        ProtectionAction action = ProtectionAction.BLOCK_USE;
        if (item instanceof BlockItem) {
            target = new ItemPlacementContext(player, event.getHand(), stack, event.getHitVec()).getBlockPos();
            action = ProtectionAction.BLOCK_PLACE;
        } else if (item instanceof BucketItem) {
            target = event.getPos().offset(event.getFace());
            action = ProtectionAction.FLUID_EMPTY;
        } else if (item instanceof BoatItem || item instanceof MinecartItem) {
            target = event.getPos().offset(event.getFace());
            action = ProtectionAction.VEHICLE_PLACE;
        } else if (item instanceof DecorationItem || item instanceof ArmorStandItem) {
            target = event.getPos().offset(event.getFace());
            action = ProtectionAction.HANGING_ENTITY_PLACE;
        } else if (item instanceof SpawnEggItem) {
            target = event.getPos().offset(event.getFace());
            action = ProtectionAction.ENTITY_INTERACT;
        } else if (item instanceof FlintAndSteelItem || item instanceof FireChargeItem) {
            target = event.getPos().offset(event.getFace());
            action = ProtectionAction.ITEM_USE_ON_BLOCK;
        }
        if (!protection.allowOrNotify(player, event.getLevel(), target, action)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void useItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        ItemStack stack = player.getStackInHand(event.getHand());
        if (isMutationItem(stack.getItem()) && !protection.allowOrNotify(
                player, event.getLevel(), player.getBlockPos(), ProtectionAction.ITEM_USE_ON_BLOCK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void useEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        var target = event.getTarget();
        ProtectionAction action = target.hasPassengers() || target.hasVehicle()
                ? ProtectionAction.VEHICLE_USE : ProtectionAction.ENTITY_INTERACT;
        if (!protection.allowOrNotify(player, event.getLevel(), target.getBlockPos(), action)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void attackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayerEntity player)) return;
        if (!protection.allowOrNotify(player, player.getWorld(), event.getTarget().getBlockPos(),
                ProtectionAction.ENTITY_DAMAGE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void livingAttack(LivingAttackEvent event) {
        if (!protection.allowDamage(event.getEntity(), event.getSource())) event.setCanceled(true);
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
