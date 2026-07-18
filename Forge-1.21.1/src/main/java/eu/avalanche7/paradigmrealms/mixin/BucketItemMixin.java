package eu.avalanche7.paradigmrealms.mixin;

import eu.avalanche7.paradigmrealms.platform.protection.ForgeProtectionHooks;
import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidDrainable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BucketItem.class)
abstract class BucketItemMixin {
    @Inject(method = "placeFluid", at = @At("HEAD"), cancellable = true)
    private void paradigmRealms$protectEmpty(
            PlayerEntity player,
            World world,
            BlockPos position,
            BlockHitResult hit,
            CallbackInfoReturnable<Boolean> callback) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && !ForgeProtectionHooks.allowPlayerAction(
                        serverPlayer, world, position, ProtectionAction.FLUID_EMPTY)) {
            callback.setReturnValue(false);
        }
    }

    @Redirect(
            method = "use",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/FluidDrainable;tryDrainFluid(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/world/WorldAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack paradigmRealms$protectFill(
            FluidDrainable drainable,
            PlayerEntity player,
            WorldAccess world,
            BlockPos position,
            BlockState state) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && world instanceof World actualWorld
                && !ForgeProtectionHooks.allowPlayerAction(
                        serverPlayer, actualWorld, position, ProtectionAction.FLUID_FILL)) {
            return ItemStack.EMPTY;
        }
        return drainable.tryDrainFluid(player, world, position, state);
    }
}
