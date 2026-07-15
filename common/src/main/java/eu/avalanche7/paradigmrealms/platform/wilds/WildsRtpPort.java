package eu.avalanche7.paradigmrealms.platform.wilds;

import java.util.UUID;

import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.platform.world.StandingSafety;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import eu.avalanche7.paradigmrealms.wilds.RtpCandidate;

public interface WildsRtpPort {
    boolean online(UUID player);
    WildsRtpProbeResult probe(
            UUID player, RtpCandidate candidate, StandingSafety safety, boolean allowChunkGeneration);
    TeleportResult teleport(UUID player, BlockPosition destination);
    void notify(UUID player, String message);
}
