package eu.avalanche7.paradigmrealms.platform.chunk;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.region.ChunkCoordinate;

public interface ChunkAccessPort {
    boolean loaded(DimensionId dimension, ChunkCoordinate chunk);
    ChunkLease acquire(ChunkLoadRequest request) throws ChunkAccessFailure;
}
