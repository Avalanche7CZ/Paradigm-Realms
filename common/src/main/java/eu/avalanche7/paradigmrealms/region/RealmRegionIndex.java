package eu.avalanche7.paradigmrealms.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;

public final class RealmRegionIndex {
    private final Map<Long, List<Realm>> byRegion;
    private final int realmCount;

    private RealmRegionIndex(Map<Long, List<Realm>> byRegion, int realmCount) {
        HashMap<Long, List<Realm>> copy = new HashMap<>();
        byRegion.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        this.byRegion = Map.copyOf(copy);
        this.realmCount = realmCount;
    }

    public static RealmRegionIndex empty() {
        return new RealmRegionIndex(Map.of(), 0);
    }

    public static RealmRegionIndex from(List<Realm> realms) {
        Objects.requireNonNull(realms, "realms");
        HashMap<Long, List<Realm>> regions = new HashMap<>();
        for (Realm candidate : realms) {
            ChunkBounds bounds = candidate.allocation().cellBounds();
            HashSet<Realm> nearby = new HashSet<>();
            for (int regionX = Math.floorDiv(bounds.minX(), 32);
                    regionX <= Math.floorDiv(bounds.maxX(), 32); regionX++) {
                for (int regionZ = Math.floorDiv(bounds.minZ(), 32);
                        regionZ <= Math.floorDiv(bounds.maxZ(), 32); regionZ++) {
                    nearby.addAll(regions.getOrDefault(key(regionX, regionZ), List.of()));
                }
            }
            for (Realm existing : nearby) {
                if (candidate.allocation().cellBounds().overlaps(existing.allocation().cellBounds())) {
                    throw new IllegalArgumentException("overlapping allocation cells "
                            + candidate.id() + " and " + existing.id());
                }
            }
            for (int regionX = Math.floorDiv(bounds.minX(), 32);
                    regionX <= Math.floorDiv(bounds.maxX(), 32); regionX++) {
                for (int regionZ = Math.floorDiv(bounds.minZ(), 32);
                        regionZ <= Math.floorDiv(bounds.maxZ(), 32); regionZ++) {
                    regions.computeIfAbsent(key(regionX, regionZ), ignored -> new ArrayList<>()).add(candidate);
                }
            }
        }
        return new RealmRegionIndex(regions, realms.size());
    }

    public RealmRegionMatch resolve(BlockCoordinate position) {
        ChunkCoordinate chunk = position.chunk();
        Realm realm = byRegion.getOrDefault(
                        key(Math.floorDiv(chunk.x(), 32), Math.floorDiv(chunk.z(), 32)), List.of()).stream()
                .filter(candidate -> candidate.allocation().cellBounds().contains(chunk))
                .findFirst().orElse(null);
        if (realm == null) {
            return new RealmRegionMatch(RealmRegionKind.UNALLOCATED_REALMS_SPACE, java.util.Optional.empty());
        }
        RealmRegionKind kind = realm.allocation().buildableBounds().contains(chunk)
                ? RealmRegionKind.BUILDABLE_REALM_REGION
                : RealmRegionKind.GUARD_REGION;
        return new RealmRegionMatch(kind, java.util.Optional.of(realm));
    }

    public int allocatedCellCount() {
        return realmCount;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
