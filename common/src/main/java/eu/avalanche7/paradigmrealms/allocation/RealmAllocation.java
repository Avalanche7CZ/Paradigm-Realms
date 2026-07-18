package eu.avalanche7.paradigmrealms.allocation;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.region.CellCoordinate;
import eu.avalanche7.paradigmrealms.region.ChunkBounds;

public record RealmAllocation(
        AllocationProfile profile,
        CellCoordinate cell,
        ChunkBounds cellBounds,
        ChunkBounds buildableBounds) {

    public RealmAllocation {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(cellBounds, "cellBounds");
        Objects.requireNonNull(buildableBounds, "buildableBounds");
        if (!cellBounds.contains(buildableBounds)) {
            throw new IllegalArgumentException("buildable region must be inside its allocation cell");
        }
        if (profile.equals(AllocationProfile.REGION_ALIGNED_32_V1)) {
            validateGeometry(cellBounds, buildableBounds, 32, 16, 8);
            if (Math.floorMod(cellBounds.minX(), 32) != 0
                    || Math.floorMod(cellBounds.minZ(), 32) != 0) {
                throw new IllegalArgumentException("region-aligned allocation must start on a region boundary");
            }
        }
    }

    public RealmAllocation(CellCoordinate cell, ChunkBounds cellBounds, ChunkBounds buildableBounds) {
        this(inferProfile(cellBounds, buildableBounds), cell, cellBounds, buildableBounds);
    }

    public int regionX() {
        if (!profile.regionAligned32()) throw new IllegalStateException("allocation is not region-aligned");
        return Math.floorDiv(cellBounds.minX(), 32);
    }

    public int regionZ() {
        if (!profile.regionAligned32()) throw new IllegalStateException("allocation is not region-aligned");
        return Math.floorDiv(cellBounds.minZ(), 32);
    }

    private static void validateGeometry(
            ChunkBounds cell, ChunkBounds buildable, int cellSize, int buildableSize, int inset) {
        if (cell.width() != cellSize || cell.depth() != cellSize) {
            throw new IllegalArgumentException("allocation cell must be " + cellSize + 'x' + cellSize + " chunks");
        }
        if (buildable.width() != buildableSize || buildable.depth() != buildableSize) {
            throw new IllegalArgumentException(
                    "buildable region must be " + buildableSize + 'x' + buildableSize + " chunks");
        }
        if (buildable.minX() - cell.minX() != inset || buildable.minZ() - cell.minZ() != inset
                || cell.maxX() - buildable.maxX() != inset
                || cell.maxZ() - buildable.maxZ() != inset) {
            throw new IllegalArgumentException("buildable region has the wrong guard inset");
        }
    }

    private static AllocationProfile inferProfile(ChunkBounds cell, ChunkBounds buildable) {
        if (cell.width() == 32 && cell.depth() == 32
                && buildable.width() == 16 && buildable.depth() == 16) {
            return AllocationProfile.REGION_ALIGNED_32_V1;
        }
        return AllocationProfile.CUSTOM_V1;
    }
}
