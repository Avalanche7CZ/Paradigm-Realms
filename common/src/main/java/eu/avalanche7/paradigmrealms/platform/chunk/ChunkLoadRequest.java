package eu.avalanche7.paradigmrealms.platform.chunk;

import java.util.Objects;
import java.util.Set;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.region.ChunkCoordinate;

public record ChunkLoadRequest(
        DimensionId dimension,
        Set<ChunkCoordinate> chunks,
        ChunkLoadPurpose purpose,
        boolean generateMissing) {
    private static final int MAX_CHUNKS_PER_LEASE = 256;

    public ChunkLoadRequest {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(purpose, "purpose");
        chunks = Set.copyOf(chunks);
        if (chunks.isEmpty() || chunks.size() > MAX_CHUNKS_PER_LEASE) {
            throw new IllegalArgumentException("chunk lease must contain 1-256 chunks");
        }
    }

    public static ChunkLoadRequest one(
            DimensionId dimension, ChunkCoordinate chunk,
            ChunkLoadPurpose purpose, boolean generateMissing) {
        return new ChunkLoadRequest(dimension, Set.of(chunk), purpose, generateMissing);
    }
}
