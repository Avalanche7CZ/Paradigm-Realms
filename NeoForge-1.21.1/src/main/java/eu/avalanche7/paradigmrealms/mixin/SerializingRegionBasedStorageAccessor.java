package eu.avalanche7.paradigmrealms.mixin;

import net.minecraft.world.storage.ChunkPosKeyedStorage;
import net.minecraft.world.storage.SerializingRegionBasedStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SerializingRegionBasedStorage.class)
public interface SerializingRegionBasedStorageAccessor {
    @Accessor("storageAccess")
    ChunkPosKeyedStorage paradigmRealms$storageAccess();
}
