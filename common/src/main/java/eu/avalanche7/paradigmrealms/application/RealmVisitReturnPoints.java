package eu.avalanche7.paradigmrealms.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

public final class RealmVisitReturnPoints {
    private final Map<UUID, ReturnPoint> points = new HashMap<>();

    public void remember(UUID player, ReturnPoint point, Realm destination) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(destination, "destination");
        if (point.dimension().equals(DimensionId.REALMS)
                && destination.allocation().buildableBounds().contains(point.position().chunk())) return;
        points.putIfAbsent(player, point);
    }

    public Optional<ReturnPoint> remove(UUID player) {
        return Optional.ofNullable(points.remove(player));
    }

    public int prune(Set<UUID> online) {
        int before = points.size();
        points.keySet().removeIf(player -> !online.contains(player));
        return before - points.size();
    }

    public void clear() {
        points.clear();
    }

    public record ReturnPoint(DimensionId dimension, BlockPosition position) {
        public ReturnPoint {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(position, "position");
        }
    }
}
