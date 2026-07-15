package eu.avalanche7.paradigmrealms.platform;

import eu.avalanche7.paradigmrealms.platform.chunk.ChunkAccessPort;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportEffectPort;
import eu.avalanche7.paradigmrealms.platform.player.PlayerStatePort;
import eu.avalanche7.paradigmrealms.platform.world.WorldQueryPort;

public interface RealmsServerPlatformAdapter {
    ChunkAccessPort chunks();
    WorldQueryPort worlds();
    TeleportEffectPort teleports();
    PlayerStatePort players();
}
