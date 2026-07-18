package eu.avalanche7.paradigmrealms.allocation;

import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.region.CellCoordinate;
import eu.avalanche7.paradigmrealms.region.ChunkBounds;

public final class RealmAllocator {
    public static final int CELL_SIZE_CHUNKS = 32;
    public static final int BUILDABLE_SIZE_CHUNKS = 16;
    public static final int GUARD_INSET_CHUNKS = 8;
    public static final int TOTAL_SEPARATION_CHUNKS = 16;
    public static final int MAX_RING = 113_280;
    public static final long MAX_REALM_ID = 51_329_886_721L;

    public RealmAllocation preview(RealmId realmId) {
        if (realmId.value() > MAX_REALM_ID) {
            throw new AllocationException("realm ID exceeds the configured safe allocation envelope: " + realmId);
        }

        CellCoordinate cell = mapToCell(realmId);
        int minX = checkedCellOrigin(cell.x());
        int minZ = checkedCellOrigin(cell.z());
        int maxX = Math.addExact(minX, CELL_SIZE_CHUNKS - 1);
        int maxZ = Math.addExact(minZ, CELL_SIZE_CHUNKS - 1);
        ChunkBounds cellBounds = new ChunkBounds(minX, minZ, maxX, maxZ);
        ChunkBounds buildable = new ChunkBounds(
                Math.addExact(minX, GUARD_INSET_CHUNKS),
                Math.addExact(minZ, GUARD_INSET_CHUNKS),
                Math.addExact(minX, GUARD_INSET_CHUNKS + BUILDABLE_SIZE_CHUNKS - 1),
                Math.addExact(minZ, GUARD_INSET_CHUNKS + BUILDABLE_SIZE_CHUNKS - 1));
        return new RealmAllocation(AllocationProfile.REGION_ALIGNED_32_V1, cell, cellBounds, buildable);
    }

    public CellCoordinate mapToCell(RealmId realmId) {
        long n = Math.subtractExact(realmId.value(), 1L);
        if (n == 0) {
            return new CellCoordinate(0, 0);
        }
        if (realmId.value() > MAX_REALM_ID) {
            throw new AllocationException("realm ID exceeds maximum supported ID: " + realmId);
        }

        long ring = findRing(n);
        long side = Math.multiplyExact(2L, ring);
        long diameter = Math.addExact(side, 1L);
        long ringLast = Math.subtractExact(Math.multiplyExact(diameter, diameter), 1L);
        long distance = Math.subtractExact(ringLast, n);

        long gridX;
        long gridZ;
        if (distance < side) {
            gridX = Math.subtractExact(ring, distance);
            gridZ = -ring;
        } else if (distance < Math.multiplyExact(2L, side)) {
            gridX = -ring;
            gridZ = Math.addExact(-ring, Math.subtractExact(distance, side));
        } else if (distance < Math.multiplyExact(3L, side)) {
            gridX = Math.addExact(-ring, Math.subtractExact(distance, Math.multiplyExact(2L, side)));
            gridZ = ring;
        } else {
            gridX = ring;
            gridZ = Math.subtractExact(ring, Math.subtractExact(distance, Math.multiplyExact(3L, side)));
        }
        return new CellCoordinate(Math.toIntExact(gridX), Math.toIntExact(gridZ));
    }

    private static long findRing(long n) {
        long low = 1;
        long high = MAX_RING;
        while (low < high) {
            long middle = low + (high - low) / 2;
            if (n <= lastIndexInRing(middle)) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static long lastIndexInRing(long ring) {
        return Math.multiplyExact(4L, Math.multiplyExact(ring, Math.addExact(ring, 1L)));
    }

    private static int checkedCellOrigin(int gridCoordinate) {
        return checkedCellOrigin(gridCoordinate, CELL_SIZE_CHUNKS);
    }

    private static int checkedCellOrigin(int gridCoordinate, int size) {
        return Math.toIntExact(Math.multiplyExact((long) gridCoordinate, size));
    }
}
