package eu.avalanche7.paradigmrealms.platform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkAccessFailure;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkAccessPort;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLease;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadPurpose;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadRequest;
import eu.avalanche7.paradigmrealms.platform.player.PlayerPosition;
import eu.avalanche7.paradigmrealms.platform.player.PlayerStatePort;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportEffectPort;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportRequest;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.platform.world.StandingSafety;
import eu.avalanche7.paradigmrealms.platform.world.WorldQueryPort;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import eu.avalanche7.paradigmrealms.region.ChunkCoordinate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

public final class FabricServerPlatformAdapter implements RealmsServerPlatformAdapter {
    private final ChunkAccessPort chunks;
    private final WorldQueryPort worlds;
    private final TeleportEffectPort teleports;
    private final PlayerStatePort players;

    public FabricServerPlatformAdapter(MinecraftServer server) {
        this.chunks = new ChunkAccess(server);
        this.worlds = new WorldQueries(server);
        this.teleports = new TeleportEffects(server);
        this.players = new PlayerState(server);
    }

    @Override public ChunkAccessPort chunks() { return chunks; }
    @Override public WorldQueryPort worlds() { return worlds; }
    @Override public TeleportEffectPort teleports() { return teleports; }
    @Override public PlayerStatePort players() { return players; }

    private static ServerWorld world(MinecraftServer server, DimensionId dimension) {
        return server.getWorld(RegistryKey.of(
                RegistryKeys.WORLD, Identifier.of(dimension.namespace(), dimension.path())));
    }

    private static final class ChunkAccess implements ChunkAccessPort {
        private static final int TICKET_LEVEL = 2;
        private static final Map<ChunkLoadPurpose, ChunkTicketType<ChunkPos>> TICKETS = tickets();
        private final MinecraftServer server;

        private ChunkAccess(MinecraftServer server) { this.server = java.util.Objects.requireNonNull(server); }

        @Override public boolean loaded(DimensionId dimension, ChunkCoordinate chunk) {
            ServerWorld world = world(server, dimension);
            return world != null && world.isChunkLoaded(ChunkPos.toLong(chunk.x(), chunk.z()));
        }

        @Override public ChunkLease acquire(ChunkLoadRequest request) throws ChunkAccessFailure {
            ServerWorld world = world(server, request.dimension());
            if (world == null) throw new ChunkAccessFailure(
                    ChunkAccessFailure.Reason.WORLD_UNAVAILABLE,
                    "dimension is unavailable: " + request.dimension());
            ChunkTicketType<ChunkPos> ticket = TICKETS.get(request.purpose());
            List<ChunkPos> acquired = new ArrayList<>(request.chunks().size());
            try {
                request.chunks().stream()
                        .sorted(Comparator.comparingInt(ChunkCoordinate::x)
                                .thenComparingInt(ChunkCoordinate::z))
                        .forEach(coordinate -> acquire(world, ticket, request, acquired, coordinate));
            } catch (MissingChunkException exception) {
                release(world, ticket, acquired);
                throw new ChunkAccessFailure(ChunkAccessFailure.Reason.CHUNK_UNAVAILABLE,
                        "chunk is unavailable: " + exception.chunk, exception);
            } catch (RuntimeException exception) {
                release(world, ticket, acquired);
                throw exception;
            }
            return new TicketLease(world, ticket, acquired);
        }

        private static void acquire(
                ServerWorld world, ChunkTicketType<ChunkPos> ticket, ChunkLoadRequest request,
                List<ChunkPos> acquired, ChunkCoordinate coordinate) {
            ChunkPos chunk = new ChunkPos(coordinate.x(), coordinate.z());
            world.getChunkManager().addTicket(ticket, chunk, TICKET_LEVEL, chunk);
            acquired.add(chunk);
            if (world.getChunkManager().getChunk(
                    chunk.x, chunk.z, ChunkStatus.FULL, request.generateMissing()) == null) {
                throw new MissingChunkException(chunk);
            }
        }

        private static Map<ChunkLoadPurpose, ChunkTicketType<ChunkPos>> tickets() {
            EnumMap<ChunkLoadPurpose, ChunkTicketType<ChunkPos>> result =
                    new EnumMap<>(ChunkLoadPurpose.class);
            result.put(ChunkLoadPurpose.TELEPORT, ticket("teleport", 100));
            result.put(ChunkLoadPurpose.REALM_GENERATION, ticket("realm_generation", 300));
            result.put(ChunkLoadPurpose.WILDS_SEARCH, ticket("wilds_search", 100));
            result.put(ChunkLoadPurpose.WILDS_SPAWN, ticket("wilds_spawn", 100));
            return Map.copyOf(result);
        }

        private static ChunkTicketType<ChunkPos> ticket(String name, int expiry) {
            return ChunkTicketType.create(
                    "paradigm_realms:" + name, Comparator.comparingLong(ChunkPos::toLong), expiry);
        }

        private static void release(
                ServerWorld world, ChunkTicketType<ChunkPos> ticket, List<ChunkPos> chunks) {
            chunks.forEach(chunk -> world.getChunkManager().removeTicket(
                    ticket, chunk, TICKET_LEVEL, chunk));
        }

        private static final class TicketLease implements ChunkLease {
            private final ServerWorld world;
            private final ChunkTicketType<ChunkPos> ticket;
            private final List<ChunkPos> chunks;
            private boolean closed;

            private TicketLease(
                    ServerWorld world, ChunkTicketType<ChunkPos> ticket, List<ChunkPos> chunks) {
                this.world = world;
                this.ticket = ticket;
                this.chunks = List.copyOf(chunks);
            }

            @Override public void close() {
                if (closed) return;
                closed = true;
                release(world, ticket, chunks);
            }
        }

        private static final class MissingChunkException extends RuntimeException {
            private final ChunkPos chunk;
            private MissingChunkException(ChunkPos chunk) { this.chunk = chunk; }
        }
    }

    private static final class WorldQueries implements WorldQueryPort {
        private final MinecraftServer server;
        private WorldQueries(MinecraftServer server) { this.server = java.util.Objects.requireNonNull(server); }

        @Override public boolean available(DimensionId dimension) { return world(server, dimension) != null; }

        @Override public boolean insideWorldBorder(DimensionId dimension, BlockCoordinate position) {
            ServerWorld world = world(server, dimension);
            return world != null && world.getWorldBorder().contains(pos(position));
        }

        @Override public boolean safeStanding(
                DimensionId dimension, BlockCoordinate feetValue, StandingSafety safety) {
            ServerWorld world = world(server, dimension);
            if (world == null) return false;
            BlockPos feet = pos(feetValue);
            if (feet.getY() <= world.getBottomY() || feet.getY() + 1 >= world.getTopY()) return false;
            if (!world.getWorldBorder().contains(feet)) return false;
            BlockPos floorPosition = feet.down();
            BlockState floor = world.getBlockState(floorPosition);
            if (!floor.isSolidBlock(world, floorPosition)
                    || !world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                    || !world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()) return false;
            if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()) return false;
            if (safety.avoidFloorFluids() && !world.getFluidState(floorPosition).isEmpty()) return false;
            if (safety.avoidLeaves() && floor.isIn(BlockTags.LEAVES)) return false;
            if (safety.avoidPowderSnow() && floor.isOf(Blocks.POWDER_SNOW)) return false;
            return !safety.avoidHazardousFloor()
                    || !(floor.isOf(Blocks.LAVA) || floor.isOf(Blocks.FIRE)
                            || floor.isOf(Blocks.SOUL_FIRE) || floor.isOf(Blocks.CACTUS)
                            || floor.isOf(Blocks.MAGMA_BLOCK));
        }

        @Override public Optional<BlockPosition> spawnPosition(DimensionId dimension, UUID player) {
            ServerWorld world = world(server, dimension);
            if (world == null) return Optional.empty();
            BlockPos feet = world.getTopPosition(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, world.getSpawnPos());
            var online = server.getPlayerManager().getPlayer(player);
            return Optional.of(new BlockPosition(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                    online == null ? 0 : online.getYaw(), online == null ? 0 : online.getPitch()));
        }

        private static BlockPos pos(BlockCoordinate value) {
            return new BlockPos(value.x(), value.y(), value.z());
        }
    }

    private static final class TeleportEffects implements TeleportEffectPort {
        private final MinecraftServer server;
        private TeleportEffects(MinecraftServer server) { this.server = java.util.Objects.requireNonNull(server); }

        @Override public TeleportResult teleport(TeleportRequest request) {
            var player = server.getPlayerManager().getPlayer(request.player());
            if (player == null) return TeleportResult.WORLD_UNAVAILABLE;
            if (player.hasVehicle() || player.hasPassengers()) return TeleportResult.RIDING_OR_HAS_PASSENGERS;
            ServerWorld world = world(server, request.dimension());
            if (world == null) return TeleportResult.WORLD_UNAVAILABLE;
            var destination = request.destination();
            var targetBox = player.getBoundingBox().offset(destination.x() - player.getX(),
                    destination.y() - player.getY(), destination.z() - player.getZ());
            if (!world.isSpaceEmpty(player, targetBox)) return TeleportResult.UNSAFE_DESTINATION;
            boolean teleported = player.teleport(world, destination.x(), destination.y(), destination.z(),
                    Set.<PositionFlag>of(), destination.yaw(), destination.pitch());
            return teleported ? TeleportResult.SUCCESS : TeleportResult.UNSAFE_DESTINATION;
        }
    }

    private static final class PlayerState implements PlayerStatePort {
        private final MinecraftServer server;
        private PlayerState(MinecraftServer server) { this.server = java.util.Objects.requireNonNull(server); }

        @Override public Optional<PlayerPosition> position(UUID player) {
            return Optional.ofNullable(server.getPlayerManager().getPlayer(player)).map(online ->
                    new PlayerPosition(
                            DimensionId.parse(online.getServerWorld().getRegistryKey().getValue().toString()),
                            new BlockPosition(online.getX(), online.getY(), online.getZ(),
                                    online.getYaw(), online.getPitch())));
        }
    }
}
