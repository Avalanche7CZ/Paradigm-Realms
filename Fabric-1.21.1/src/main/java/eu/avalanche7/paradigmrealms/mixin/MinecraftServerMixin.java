package eu.avalanche7.paradigmrealms.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import eu.avalanche7.paradigmrealms.platform.wilds.WildsBootContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.Registry;
import net.minecraft.world.dimension.DimensionOptions;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.world.biome.source.BiomeAccess;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    private static final String SERVER_WORLD_CTOR = "Lnet/minecraft/server/world/ServerWorld;<init>("
            + "Lnet/minecraft/server/MinecraftServer;Ljava/util/concurrent/Executor;"
            + "Lnet/minecraft/world/level/storage/LevelStorage$Session;"
            + "Lnet/minecraft/world/level/ServerWorldProperties;Lnet/minecraft/registry/RegistryKey;"
            + "Lnet/minecraft/world/dimension/DimensionOptions;"
            + "Lnet/minecraft/server/WorldGenerationProgressListener;ZJLjava/util/List;Z"
            + "Lnet/minecraft/util/math/random/RandomSequencesState;)V";

    @Inject(method = "createWorlds", at = @At("HEAD"))
    private void paradigmRealms$bootstrapBeforeWorlds(
            WorldGenerationProgressListener listener, CallbackInfo callback) {
        WildsBootContext.beforeWorlds((MinecraftServer) (Object) this);
    }

    @Redirect(method = "createWorlds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/registry/Registry;getEntrySet()Ljava/util/Set;"))
    private Set<Map.Entry<RegistryKey<DimensionOptions>, DimensionOptions>> paradigmRealms$enabledDimensions(
            Registry<DimensionOptions> dimensions) {
        var entries = dimensions.getEntrySet();
        if (WildsBootContext.enabled()) return entries;
        var enabled = new LinkedHashSet<>(entries);
        enabled.removeIf(entry -> "paradigm_realms:wilds".equals(entry.getKey().getValue().toString()));
        return enabled;
    }

    @ModifyArgs(method = "createWorlds", at = @At(value = "INVOKE", target = SERVER_WORLD_CTOR))
    private void paradigmRealms$seedWildsBiomeAccess(Args args) {
        Object raw = args.get(4);
        if (raw instanceof RegistryKey<?> key
                && "paradigm_realms:wilds".equals(key.getValue().toString())) {
            WildsBootContext.seed().ifPresent(seed -> args.set(8, BiomeAccess.hashSeed(seed)));
        }
    }
}
