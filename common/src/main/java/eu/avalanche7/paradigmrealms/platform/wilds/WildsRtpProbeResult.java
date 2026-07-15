package eu.avalanche7.paradigmrealms.platform.wilds;

import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.region.BlockPosition;

public record WildsRtpProbeResult(
        Status status,
        Optional<BlockPosition> destination,
        boolean generatedChunk) {
    public WildsRtpProbeResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(destination, "destination");
        if ((status == Status.SAFE) != destination.isPresent()) {
            throw new IllegalArgumentException("only a safe probe has a destination");
        }
        if (generatedChunk && status == Status.CHUNK_BUDGET) {
            throw new IllegalArgumentException("a rejected chunk budget cannot generate a chunk");
        }
    }

    public static WildsRtpProbeResult rejected(Status status) {
        if (status == Status.SAFE) throw new IllegalArgumentException("safe probe requires destination");
        return new WildsRtpProbeResult(status, Optional.empty(), false);
    }

    public static WildsRtpProbeResult safe(BlockPosition destination, boolean generatedChunk) {
        return new WildsRtpProbeResult(Status.SAFE, Optional.of(destination), generatedChunk);
    }

    public enum Status { SAFE, WORLD_UNAVAILABLE, WORLD_BORDER, CHUNK_BUDGET, UNSAFE_SURFACE }
}
