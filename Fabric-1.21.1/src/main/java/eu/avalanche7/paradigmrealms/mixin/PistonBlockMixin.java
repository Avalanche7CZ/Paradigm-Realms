package eu.avalanche7.paradigmrealms.mixin;

import eu.avalanche7.paradigmrealms.platform.protection.FabricProtectionHooks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PistonBlock.class)
abstract class PistonBlockMixin {
    @Redirect(
            method = "move",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/block/piston/PistonHandler;calculatePush()Z"))
    private boolean paradigmRealms$protectBoundary(
            PistonHandler handler, World world, BlockPos piston, Direction direction, boolean retract) {
        if (!handler.calculatePush()) return false;
        return FabricProtectionHooks.allowPiston(
                world, piston, handler.getMovedBlocks(), handler.getBrokenBlocks(), handler.getMotionDirection());
    }
}
