package eu.avalanche7.paradigmrealms.application;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;
import eu.avalanche7.paradigmrealms.platform.player.PlayerStatePort;
import eu.avalanche7.paradigmrealms.platform.teleport.SetSpawnResult;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;

public final class RealmSpawnService {
    private final RealmRepository realms;
    private final RealmTeleportService teleports;
    private final PlayerStatePort players;

    public RealmSpawnService(
            RealmRepository realms, RealmTeleportService teleports, PlayerStatePort players) {
        this.realms = Objects.requireNonNull(realms, "realms");
        this.teleports = Objects.requireNonNull(teleports, "teleports");
        this.players = Objects.requireNonNull(players, "players");
    }

    public SetSpawnResult setSpawn(UUID player) {
        var owned = realms.findByOwner(player);
        if (owned.isEmpty()) return SetSpawnResult.NO_REALM;
        var realm = owned.orElseThrow();
        if (realm.state() != RealmLifecycleState.ACTIVE) return SetSpawnResult.REALM_NOT_ACTIVE;
        var current = players.position(player);
        if (current.isEmpty() || !current.orElseThrow().dimension().equals(DimensionId.REALMS)) {
            return SetSpawnResult.NOT_IN_REALMS;
        }
        var position = current.orElseThrow().position();
        BlockCoordinate feet = new BlockCoordinate(
                (int) Math.floor(position.x()),
                (int) Math.floor(position.y()),
                (int) Math.floor(position.z()));
        TeleportResult safety = teleports.validateRealmSpawn(realm, feet);
        if (safety == TeleportResult.OUTSIDE_BOUNDS) return SetSpawnResult.OUTSIDE_BOUNDS;
        if (safety != TeleportResult.SUCCESS) return SetSpawnResult.UNSAFE_DESTINATION;
        realms.save(realm.withSpawn(position));
        return SetSpawnResult.SUCCESS;
    }
}
