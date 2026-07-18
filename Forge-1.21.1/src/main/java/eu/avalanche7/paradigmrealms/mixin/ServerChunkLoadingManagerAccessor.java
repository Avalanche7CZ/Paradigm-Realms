package eu.avalanche7.paradigmrealms.mixin;

import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor {
    @Accessor("pointOfInterestStorage")
    PointOfInterestStorage paradigmRealms$pointOfInterestStorage();
}
