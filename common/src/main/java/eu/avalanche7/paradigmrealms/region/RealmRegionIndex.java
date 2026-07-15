package eu.avalanche7.paradigmrealms.region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;

public final class RealmRegionIndex {
    private final Map<CellCoordinate, Realm> byCell;

    private RealmRegionIndex(Map<CellCoordinate, Realm> byCell) {
        this.byCell = Map.copyOf(byCell);
    }

    public static RealmRegionIndex empty() {
        return new RealmRegionIndex(Map.of());
    }

    public static RealmRegionIndex from(List<Realm> realms) {
        Objects.requireNonNull(realms, "realms");
        Map<CellCoordinate, Realm> result = new HashMap<>();
        for (Realm realm : realms) {
            Realm previous = result.put(realm.allocation().cell(), realm);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate allocation cell " + realm.allocation().cell());
            }
        }
        return new RealmRegionIndex(result);
    }

    public RealmRegionMatch resolve(BlockCoordinate position) {
        ChunkCoordinate chunk = position.chunk();
        CellCoordinate cell = new CellCoordinate(
                Math.floorDiv(chunk.x(), RealmAllocator.CELL_SIZE_CHUNKS),
                Math.floorDiv(chunk.z(), RealmAllocator.CELL_SIZE_CHUNKS));
        Realm realm = byCell.get(cell);
        if (realm == null) {
            return new RealmRegionMatch(RealmRegionKind.UNALLOCATED_REALMS_SPACE, java.util.Optional.empty());
        }
        RealmRegionKind kind = realm.allocation().buildableBounds().contains(chunk)
                ? RealmRegionKind.BUILDABLE_REALM_REGION
                : RealmRegionKind.GUARD_REGION;
        return new RealmRegionMatch(kind, java.util.Optional.of(realm));
    }

    public int allocatedCellCount() {
        return byCell.size();
    }
}
