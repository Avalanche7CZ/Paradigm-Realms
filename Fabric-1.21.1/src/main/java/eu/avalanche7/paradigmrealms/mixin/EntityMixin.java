package eu.avalanche7.paradigmrealms.mixin;

import eu.avalanche7.paradigmrealms.platform.protection.FabricProtectionHooks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "canModifyAt", at = @At("HEAD"), cancellable = true)
    private void paradigmRealms$applyMobGriefing(
            World world, BlockPos position, CallbackInfoReturnable<Boolean> callback) {
        if (!FabricProtectionHooks.allowMobGrief((Entity) (Object) this, world, position)) {
            callback.setReturnValue(false);
        }
    }
}
