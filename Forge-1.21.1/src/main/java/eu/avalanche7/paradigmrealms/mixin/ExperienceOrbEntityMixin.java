package eu.avalanche7.paradigmrealms.mixin;

import eu.avalanche7.paradigmrealms.platform.protection.ForgeProtectionHooks;
import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExperienceOrbEntity.class)
abstract class ExperienceOrbEntityMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void paradigmRealms$protectPickup(PlayerEntity player, CallbackInfo callback) {
        if (!ForgeProtectionHooks.allowPickup(
                player, (ExperienceOrbEntity) (Object) this, ProtectionAction.EXPERIENCE_PICKUP)) {
            callback.cancel();
        }
    }
}
