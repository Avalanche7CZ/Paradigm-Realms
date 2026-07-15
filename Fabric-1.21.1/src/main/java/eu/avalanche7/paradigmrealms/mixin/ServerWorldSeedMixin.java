package eu.avalanche7.paradigmrealms.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import eu.avalanche7.paradigmrealms.platform.wilds.WildsBootContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.GeneratorOptions;

@Mixin(ServerWorld.class)
abstract class ServerWorldSeedMixin {
    @Inject(method = "getSeed", at = @At("HEAD"), cancellable = true)
    private void paradigmRealms$wildsSeed(CallbackInfoReturnable<Long> callback) {
        ServerWorld self = (ServerWorld) (Object) this;
        if ("paradigm_realms:wilds".equals(self.getRegistryKey().getValue().toString())) {
            WildsBootContext.seed().ifPresent(callback::setReturnValue);
        }
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/gen/GeneratorOptions;getSeed()J"))
    private long paradigmRealms$wildsStructureSeed(GeneratorOptions options) {
        ServerWorld self = (ServerWorld) (Object) this;
        if ("paradigm_realms:wilds".equals(self.getRegistryKey().getValue().toString())) {
            return WildsBootContext.seed().orElseGet(options::getSeed);
        }
        return options.getSeed();
    }
}
