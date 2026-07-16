package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.mixin.EntityChunkDataAccessAccessor;
import eu.avalanche7.paradigmrealms.mixin.SerializingRegionBasedStorageAccessor;
import eu.avalanche7.paradigmrealms.mixin.ServerChunkLoadingManagerAccessor;
import eu.avalanche7.paradigmrealms.mixin.ServerEntityManagerAccessor;
import eu.avalanche7.paradigmrealms.mixin.ServerWorldAccessor;
import eu.avalanche7.paradigmrealms.mixin.VersionedChunkStorageAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.storage.ChunkPosKeyedStorage;
import net.minecraft.world.storage.EntityChunkDataAccess;
import net.minecraft.world.storage.StorageIoWorker;

final class FabricBackupStorageAccess {
    private final MinecraftServer server;
    private final Executor fileExecutor;

    FabricBackupStorageAccess(MinecraftServer server, Executor fileExecutor) {
        this.server = server;
        this.fileExecutor = fileExecutor;
    }

    CompletableFuture<CapturedChunks> capture(
            ServerWorld world,
            BackupCellBounds bounds,
            Path stagingDirectory,
            Duration timeout,
            ProgressListener progress) {
        if (!server.isOnThread()) {
            throw new IllegalStateException("realm capture must start on the server thread");
        }

        Instant startedAt = Instant.now();
        ServerChunkLoadingManager chunkStorage = world.getChunkManager().chunkLoadingManager;
        PointOfInterestStorage poiStorage =
                ((ServerChunkLoadingManagerAccessor) chunkStorage)
                        .paradigmRealms$pointOfInterestStorage();

        List<ChunkCoordinate> coordinates = bounds.coordinates();
        for (ChunkCoordinate coordinate : coordinates) {
            poiStorage.saveChunk(new ChunkPos(coordinate.x(), coordinate.z()));
        }

        if (!server.saveAll(false, true, true)) {
            return CompletableFuture.failedFuture(
                    new IOException("Minecraft save barrier reported failure"));
        }

        ChunkPosKeyedStorage poiAccess =
                ((SerializingRegionBasedStorageAccessor) poiStorage)
                        .paradigmRealms$storageAccess();
        poiAccess.completeAll(true).join();

        StorageIoWorker terrainAccess =
                ((VersionedChunkStorageAccessor) chunkStorage)
                        .paradigmRealms$worker();
        ChunkPosKeyedStorage entityAccess = entityStorage(world);

        EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> captured =
                emptyCaptureMap();
        AtomicInteger completed = new AtomicInteger();
        int totalReads = coordinates.size() * BackupStorageKind.values().length;

        List<CompletableFuture<Void>> writes = coordinates.stream()
                .flatMap(coordinate -> List.of(
                        captureOne(
                                BackupStorageKind.TERRAIN,
                                coordinate,
                                terrainAccess.readChunkData(chunkPos(coordinate)),
                                stagingDirectory,
                                captured,
                                completed,
                                totalReads,
                                progress),
                        captureOne(
                                BackupStorageKind.ENTITIES,
                                coordinate,
                                entityAccess.read(chunkPos(coordinate)),
                                stagingDirectory,
                                captured,
                                completed,
                                totalReads,
                                progress),
                        captureOne(
                                BackupStorageKind.POI,
                                coordinate,
                                poiAccess.read(chunkPos(coordinate)),
                                stagingDirectory,
                                captured,
                                completed,
                                totalReads,
                                progress)).stream())
                .toList();

        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .thenApply(ignored -> new CapturedChunks(
                        immutable(captured),
                        Duration.between(startedAt, Instant.now())));
    }

    private CompletableFuture<Void> captureOne(
            BackupStorageKind kind,
            ChunkCoordinate coordinate,
            CompletableFuture<Optional<NbtCompound>> read,
            Path stagingDirectory,
            EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> captured,
            AtomicInteger completed,
            int totalReads,
            ProgressListener progress) {
        return read.thenAcceptAsync(optionalNbt -> {
            optionalNbt.ifPresent(nbt -> writeCapturedChunk(
                    kind,
                    coordinate,
                    nbt,
                    stagingDirectory,
                    captured));

            int current = completed.incrementAndGet();
            if (current == totalReads || current % 16 == 0) {
                progress.updated(current, totalReads);
            }
        }, fileExecutor);
    }

    private static void writeCapturedChunk(
            BackupStorageKind kind,
            ChunkCoordinate coordinate,
            NbtCompound nbt,
            Path stagingDirectory,
            EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> captured) {
        Path path = stagingDirectory
                .resolve(kind.directory())
                .resolve(coordinate.x() + "_" + coordinate.z() + ".nbt");
        try {
            Files.createDirectories(path.getParent());
            NbtIo.write(nbt, path);
            synchronized (captured) {
                captured.get(kind).put(coordinate, path);
            }
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static ChunkPosKeyedStorage entityStorage(ServerWorld world) {
        ServerEntityManager<Entity> manager =
                ((ServerWorldAccessor) world).paradigmRealms$entityManager();
        Object dataAccess =
                ((ServerEntityManagerAccessor<?>) manager).paradigmRealms$dataAccess();
        if (!(dataAccess instanceof EntityChunkDataAccess entityData)) {
            throw new IllegalStateException("unexpected entity storage implementation");
        }
        return ((EntityChunkDataAccessAccessor) entityData).paradigmRealms$storage();
    }

    private static ChunkPos chunkPos(ChunkCoordinate coordinate) {
        return new ChunkPos(coordinate.x(), coordinate.z());
    }

    private static EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> emptyCaptureMap() {
        EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> result =
                new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            result.put(kind, new HashMap<>());
        }
        return result;
    }

    private static Map<BackupStorageKind, Map<ChunkCoordinate, Path>> immutable(
            EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> captured) {
        EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> result =
                new EnumMap<>(BackupStorageKind.class);
        synchronized (captured) {
            captured.forEach((kind, chunks) -> result.put(kind, Map.copyOf(chunks)));
        }
        return Map.copyOf(result);
    }

    @FunctionalInterface
    interface ProgressListener {
        void updated(int captured, int total);
    }

    record CapturedChunks(
            Map<BackupStorageKind, Map<ChunkCoordinate, Path>> chunks,
            Duration captureDuration) {}
}
