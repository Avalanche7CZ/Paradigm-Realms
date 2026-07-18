package eu.avalanche7.paradigmrealms.mixin;

import net.minecraft.world.storage.StorageIoWorker;
import net.minecraft.world.storage.VersionedChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(VersionedChunkStorage.class)
public interface VersionedChunkStorageAccessor {
    @Accessor("worker")
    StorageIoWorker paradigmRealms$worker();
}
