package eu.avalanche7.paradigmrealms.allocation;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.region.CellCoordinate;
import eu.avalanche7.paradigmrealms.region.ChunkBounds;

public record RealmAllocation(
        CellCoordinate cell,
        ChunkBounds cellBounds,
        ChunkBounds buildableBounds) {

    public RealmAllocation {
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(cellBounds, "cellBounds");
        Objects.requireNonNull(buildableBounds, "buildableBounds");
        if (cellBounds.width() != RealmAllocator.CELL_SIZE_CHUNKS
                || cellBounds.depth() != RealmAllocator.CELL_SIZE_CHUNKS) {
            throw new IllegalArgumentException("allocation cell must be 16x16 chunks");
        }
        if (buildableBounds.width() != RealmAllocator.BUILDABLE_SIZE_CHUNKS
                || buildableBounds.depth() != RealmAllocator.BUILDABLE_SIZE_CHUNKS) {
            throw new IllegalArgumentException("buildable region must be 10x10 chunks");
        }
        if (!cellBounds.contains(buildableBounds)) {
            throw new IllegalArgumentException("buildable region must be inside its allocation cell");
        }
        if (buildableBounds.minX() - cellBounds.minX() != RealmAllocator.GUARD_INSET_CHUNKS
                || buildableBounds.minZ() - cellBounds.minZ() != RealmAllocator.GUARD_INSET_CHUNKS
                || cellBounds.maxX() - buildableBounds.maxX() != RealmAllocator.GUARD_INSET_CHUNKS
                || cellBounds.maxZ() - buildableBounds.maxZ() != RealmAllocator.GUARD_INSET_CHUNKS) {
            throw new IllegalArgumentException("buildable region must have a three-chunk guard inset");
        }
    }
}
