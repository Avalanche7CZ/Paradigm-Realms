package eu.avalanche7.paradigmrealms.application;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.platform.RealmsServerPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkAccessFailure;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLease;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadPurpose;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadRequest;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportRequest;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.platform.world.StandingSafety;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;

public final class RealmTeleportService {
    private final RealmsServerPlatformAdapter platform;

    public RealmTeleportService(RealmsServerPlatformAdapter platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    public TeleportResult teleportToRealm(UUID player, Realm realm) {
        Objects.requireNonNull(realm, "realm");
        if (realm.state() != RealmLifecycleState.ACTIVE) return TeleportResult.REALM_NOT_ACTIVE;
        if (!realm.allocation().buildableBounds().contains(realm.spawn().chunk())) {
            return TeleportResult.OUTSIDE_BOUNDS;
        }
        return teleportToLocation(player, DimensionId.REALMS, realm.spawn(), false);
    }

    public TeleportResult teleportToLocation(
            UUID player, DimensionId dimension, BlockPosition destination, boolean requireLoaded) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(destination, "destination");
        if (!platform.worlds().available(dimension)) return TeleportResult.WORLD_UNAVAILABLE;
        if (requireLoaded && !platform.chunks().loaded(dimension, destination.chunk())) {
            return TeleportResult.UNSAFE_DESTINATION;
        }
        BlockCoordinate feet = floor(destination);
        if (!platform.worlds().insideWorldBorder(dimension, feet)) {
            return TeleportResult.OUTSIDE_WORLD_BORDER;
        }
        try (ChunkLease ignored = platform.chunks().acquire(ChunkLoadRequest.one(
                dimension, destination.chunk(), ChunkLoadPurpose.TELEPORT, !requireLoaded))) {
            if (!platform.worlds().insideWorldBorder(dimension, feet)) {
                return TeleportResult.OUTSIDE_WORLD_BORDER;
            }
            if (!platform.worlds().safeStanding(dimension, feet, StandingSafety.STANDARD)) {
                return TeleportResult.UNSAFE_DESTINATION;
            }
            return platform.teleports().teleport(new TeleportRequest(player, dimension, destination));
        } catch (ChunkAccessFailure failure) {
            return failure.reason() == ChunkAccessFailure.Reason.WORLD_UNAVAILABLE
                    ? TeleportResult.WORLD_UNAVAILABLE : TeleportResult.UNSAFE_DESTINATION;
        }
    }

    public TeleportResult teleportToOverworldSpawn(UUID player) {
        return platform.worlds().spawnPosition(DimensionId.OVERWORLD, player)
                .map(position -> teleportToLocation(player, DimensionId.OVERWORLD, position, false))
                .orElse(TeleportResult.WORLD_UNAVAILABLE);
    }

    public boolean safeLoaded(DimensionId dimension, BlockCoordinate feet) {
        return platform.worlds().available(dimension)
                && platform.chunks().loaded(dimension, feet.chunk())
                && platform.worlds().insideWorldBorder(dimension, feet)
                && platform.worlds().safeStanding(dimension, feet, StandingSafety.STANDARD);
    }

    public TeleportResult validateRealmSpawn(Realm realm, BlockCoordinate feet) {
        if (!realm.allocation().buildableBounds().contains(feet.chunk())) {
            return TeleportResult.OUTSIDE_BOUNDS;
        }
        if (!platform.worlds().insideWorldBorder(DimensionId.REALMS, feet)) {
            return TeleportResult.OUTSIDE_WORLD_BORDER;
        }
        return platform.worlds().safeStanding(DimensionId.REALMS, feet, StandingSafety.STANDARD)
                ? TeleportResult.SUCCESS : TeleportResult.UNSAFE_DESTINATION;
    }

    private static BlockCoordinate floor(BlockPosition position) {
        return new BlockCoordinate(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z()));
    }
}
