package eu.avalanche7.paradigmrealms.platform.protection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.protection.ProtectionAction;
import eu.avalanche7.paradigmrealms.protection.ProtectionDecision;
import eu.avalanche7.paradigmrealms.region.RealmRegionKind;
import eu.avalanche7.paradigmrealms.platform.FabricRealmRuntime;
import eu.avalanche7.paradigmrealms.application.RealmTeleportService;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
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
    private final FabricRealmRuntime runtime;
    private final FabricProtectionService protection;
    private final RealmTeleportService teleports;
    private final Map<UUID, SafeLocation> lastAllowed = new HashMap<>();
    private final Map<UUID, Long> lastEvacuationAttempt = new HashMap<>();
    private final Set<UUID> evacuating = new HashSet<>();

    public RealmPresenceService(
            MinecraftServer server,
            FabricRealmRuntime runtime,
            FabricProtectionService protection,
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
