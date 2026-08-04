package eu.avalanche7.paradigmrealms.mixin;

import net.minecraft.world.storage.ChunkPosKeyedStorage;
import net.minecraft.world.storage.EntityChunkDataAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityChunkDataAccess.class)
public interface EntityChunkDataAccessAccessor {
    @Accessor("storage")
    ChunkPosKeyedStorage paradigmRealms$storage();
}
