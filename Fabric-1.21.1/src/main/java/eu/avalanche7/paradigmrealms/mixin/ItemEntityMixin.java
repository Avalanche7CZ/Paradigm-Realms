package eu.avalanche7.paradigmrealms.mixin;

import eu.avalanche7.paradigmrealms.platform.protection.FabricProtectionHooks;
import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void paradigmRealms$protectPickup(PlayerEntity player, CallbackInfo callback) {
        if (!FabricProtectionHooks.allowPickup(
                player, (ItemEntity) (Object) this, ProtectionAction.ITEM_PICKUP)) {
            callback.cancel();
        }
    }
}
