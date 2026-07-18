package eu.avalanche7.paradigmrealms.platform.protection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import eu.avalanche7.paradigmrealms.protection.ProtectionDecision;
import eu.avalanche7.paradigmrealms.region.RealmRegionKind;
import eu.avalanche7.paradigmrealms.platform.ForgeRealmRuntime;
import eu.avalanche7.paradigmrealms.application.RealmTeleportService;
import eu.avalanche7.paradigmrealms.application.RealmLifecycleEffects;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.access.RealmAccessService;
import eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class RealmPresenceService {
    private final MinecraftServer server;
    private final ForgeRealmRuntime runtime;
    private final ForgeProtectionService protection;
    private final RealmTeleportService teleports;
    private final Map<UUID, SafeLocation> lastAllowed = new HashMap<>();
    private final Map<UUID, Long> lastEvacuationAttempt = new HashMap<>();
    private final Set<UUID> evacuating = new HashSet<>();
    private final Map<RealmId, Integer> lifecycleEvacuationAttempts = new HashMap<>();

    public RealmPresenceService(
            MinecraftServer server,
            ForgeRealmRuntime runtime,
            ForgeProtectionService protection,
            RealmTeleportService teleports) {
        this.server = server;
        this.runtime = runtime;
        this.protection = protection;
        this.teleports = teleports;
    }

    public void validateAll() {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            validate(player);
        }
    }

    public boolean validate(ServerPlayerEntity player) {
        if (evacuating.contains(player.getUuid())) return false;
        if (!isRealms(player.getWorld())) {
            rememberIfSafe(player);
            return true;
        }
        ProtectionDecision decision = protection.evaluate(
                player, player.getWorld(), player.getBlockPos(), ProtectionAction.REALM_ENTRY);
        if (decision.allowed()) {
            lastEvacuationAttempt.remove(player.getUuid());
            rememberIfSafe(player);
            return true;
        }
        long now = System.currentTimeMillis();
        Long previousAttempt = lastEvacuationAttempt.get(player.getUuid());
        if (previousAttempt != null && now - previousAttempt < 5_000L) return false;
        lastEvacuationAttempt.put(player.getUuid(), now);
        evacuate(player, decision.reason().name());
        return false;
    }

    public void revalidateRealm(RealmId realmId) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!isRealms(player.getWorld())) continue;
            var match = protection.index().resolve(new eu.avalanche7.paradigmrealms.region.BlockCoordinate(
                    player.getBlockX(), player.getBlockY(), player.getBlockZ()));
            if (match.realm().map(realm -> realm.id().equals(realmId)).orElse(false)) {
                validate(player);
            }
        }
    }

    public void disconnect(UUID player) {
        lastAllowed.remove(player);
        lastEvacuationAttempt.remove(player);
        evacuating.remove(player);
    }

    public void clear() {
        lastAllowed.clear();
        lastEvacuationAttempt.clear();
        evacuating.clear();
        lifecycleEvacuationAttempts.clear();
    }

    public int pruneStaleSessions() {
        Set<UUID> online = server.getPlayerManager().getPlayerList().stream()
                .map(ServerPlayerEntity::getUuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
        int before = lastAllowed.size() + lastEvacuationAttempt.size() + evacuating.size();
        lastAllowed.keySet().removeIf(player -> !online.contains(player));
        lastEvacuationAttempt.keySet().removeIf(player -> !online.contains(player));
        evacuating.removeIf(player -> !online.contains(player));
        return before - lastAllowed.size() - lastEvacuationAttempt.size() - evacuating.size();
    }

    public RealmLifecycleEffects.EvacuationResult evacuateAndVerify(Realm source) {
        ArrayList<ServerPlayerEntity> occupants = occupants(source);
        if (occupants.isEmpty()) {
            lifecycleEvacuationAttempts.remove(source.id());
            return RealmLifecycleEffects.EvacuationResult.COMPLETE;
        }
        int attempt = lifecycleEvacuationAttempts.merge(source.id(), 1, Integer::sum);
        for (ServerPlayerEntity player : occupants) {
            player.stopRiding();
            List.copyOf(player.getPassengerList()).forEach(net.minecraft.entity.Entity::stopRiding);
            player.removeAllPassengers();
            Realm own = runtime.repository().findByOwner(player.getUuid()).orElse(null);
            TeleportResult result = own != null && !own.id().equals(source.id())
                    ? teleports.teleportToRealm(player.getUuid(), own)
                    : teleports.teleportToOverworldSpawn(player.getUuid());
            if (result != TeleportResult.SUCCESS) {
                ParadigmRealms.LOGGER.warn("Lifecycle evacuation attempt {} failed for {} in realm {}: {}",
                        attempt, player.getUuid(), source.id(), result);
            }
        }
        if (attempt >= 3 && !occupants(source).isEmpty()) {
            lifecycleEvacuationAttempts.remove(source.id());
            return RealmLifecycleEffects.EvacuationResult.FAILED;
        }
        return RealmLifecycleEffects.EvacuationResult.RETRY;
    }

    public eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult kick(
            Realm realm, UUID target) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(target);
        if (player == null || !occupants(realm).contains(player)) {
            return eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult.NOT_PRESENT;
        }
        evacuate(player, "KICKED");
        return occupants(realm).contains(player)
                ? eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult.EVACUATION_FAILED
                : eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult.KICKED;
    }

    public void rememberReturn(ServerPlayerEntity player) {
        rememberIfSafe(player);
    }

    public TeleportResult leaveForeignRealm(UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) return TeleportResult.WORLD_UNAVAILABLE;
        Realm owned = runtime.repository().findByOwner(playerId).orElse(null);
        if (owned != null) {
            TeleportResult home = teleports.teleportToRealm(playerId, owned);
            if (home == TeleportResult.SUCCESS) return home;
        }
        SafeLocation previous = lastAllowed.remove(playerId);
        if (previous != null && tryLastAllowed(player, previous)) return TeleportResult.SUCCESS;
        return teleports.teleportToOverworldSpawn(playerId);
    }

    public List<RealmOwnerCommandRuntime.Occupant> occupantsFor(UUID actor) {
        Realm realm = runtime.commonManagedRealm(actor).orElse(null);
        if (realm == null) return List.of();
        RealmAccessService access = new RealmAccessService();
        return occupants(realm).stream()
                .map(player -> new RealmOwnerCommandRuntime.Occupant(
                        player.getUuid(), access.roleOf(realm, player.getUuid())))
                .toList();
    }

    private ArrayList<ServerPlayerEntity> occupants(Realm source) {
        ArrayList<ServerPlayerEntity> result = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!isRealms(player.getWorld())) continue;
            var match = protection.index().resolve(new BlockCoordinate(
                    player.getBlockX(), player.getBlockY(), player.getBlockZ()));
            if (match.realm().map(realm -> realm.id().equals(source.id())).orElse(false)) result.add(player);
        }
        return result;
    }

    private void evacuate(ServerPlayerEntity player, String reason) {
        if (!evacuating.add(player.getUuid())) return;
        try {
            player.stopRiding();
            player.getPassengerList().forEach(net.minecraft.entity.Entity::stopRiding);
            SafeLocation previous = lastAllowed.get(player.getUuid());
            if (previous != null && tryLastAllowed(player, previous)) {
                player.sendMessage(Text.literal("You cannot enter that realm (" + reason + ")."), false);
                return;
            }
            var owned = runtime.repository().findByOwner(player.getUuid());
            if (owned.isPresent() && owned.orElseThrow().state() == RealmLifecycleState.ACTIVE
                    && teleports.teleportToRealm(player.getUuid(), owned.orElseThrow()) == TeleportResult.SUCCESS) {
                player.sendMessage(Text.literal("You were returned to your realm (" + reason + ")."), false);
                return;
            }
            TeleportResult fallback = teleports.teleportToOverworldSpawn(player.getUuid());
            if (fallback == TeleportResult.SUCCESS) {
                player.sendMessage(Text.literal("You were returned to the Overworld (" + reason + ")."), false);
            } else {
                ParadigmRealms.LOGGER.error("Could not evacuate {} from protected realm: {}", player.getUuid(), fallback);
            }
        } finally {
            evacuating.remove(player.getUuid());
        }
    }

    private boolean tryLastAllowed(ServerPlayerEntity player, SafeLocation location) {
        DimensionId dimension = DimensionId.parse(location.dimension());
        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(location.dimension()));
        ServerWorld world = server.getWorld(key);
        if (world == null) return false;
        BlockPos feet = BlockPos.ofFloored(location.x(), location.y(), location.z());
        if (!teleports.safeLoaded(dimension, new BlockCoordinate(
                feet.getX(), feet.getY(), feet.getZ()))) return false;
        if (isRealms(world)) {
            ProtectionDecision decision = protection.evaluate(
                    player, world, feet, ProtectionAction.REALM_ENTRY);
            if (!decision.allowed()) return false;
        }
        return teleports.teleportToLocation(player.getUuid(), dimension,
                new BlockPosition(location.x(), location.y(), location.z(),
                        location.yaw(), location.pitch()), true)
                == TeleportResult.SUCCESS;
    }

    private void rememberIfSafe(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        DimensionId dimension = DimensionId.parse(world.getRegistryKey().getValue().toString());
        if (!teleports.safeLoaded(dimension, new BlockCoordinate(
                player.getBlockX(), player.getBlockY(), player.getBlockZ()))) return;
        if (isRealms(world)) {
            var match = protection.index().resolve(new eu.avalanche7.paradigmrealms.region.BlockCoordinate(
                    player.getBlockX(), player.getBlockY(), player.getBlockZ()));
            if (match.kind() != RealmRegionKind.BUILDABLE_REALM_REGION) {

                return;
            }
        }
        lastAllowed.put(player.getUuid(), new SafeLocation(
                world.getRegistryKey().getValue().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()));
    }

    private static boolean isRealms(World world) {
        return world.getRegistryKey().getValue().toString().equals(DimensionId.REALMS.toString());
    }

    private record SafeLocation(String dimension, double x, double y, double z, float yaw, float pitch) {}
}
