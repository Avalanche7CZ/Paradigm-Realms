package eu.avalanche7.paradigmrealms.platform.backup;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.message.RealmMessageKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class RealmBackupMutationLocks {
    private final Map<Long, ActiveLock> locks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotification = new ConcurrentHashMap<>();
    private final long notificationCooldownMillis;

    public RealmBackupMutationLocks(long notificationCooldownMillis) {
        this.notificationCooldownMillis = notificationCooldownMillis;
    }

    public Optional<Handle> tryAcquire(long realmId, BackupCellBounds bounds, UUID operationId) {
        return tryAcquire(realmId, bounds, operationId, false);
    }

    public Optional<Handle> tryAcquireRestore(
            long realmId,
            BackupCellBounds bounds,
            UUID operationId) {
        return tryAcquire(realmId, bounds, operationId, true);
    }

    private Optional<Handle> tryAcquire(
            long realmId,
            BackupCellBounds bounds,
            UUID operationId,
            boolean entryBlocked) {
        ActiveLock lock = new ActiveLock(realmId, bounds, operationId, entryBlocked);
        if (locks.putIfAbsent(realmId, lock) != null) {
            return Optional.empty();
        }
        return Optional.of(new Handle(lock));
    }

    public boolean denies(World world, BlockPos position) {
        if (!DimensionId.REALMS.toString().equals(world.getRegistryKey().getValue().toString())) {
            return false;
        }
        int chunkX = Math.floorDiv(position.getX(), 16);
        int chunkZ = Math.floorDiv(position.getZ(), 16);
        return locks.values().stream().anyMatch(lock -> lock.bounds().contains(
                new eu.avalanche7.paradigmrealms.backup.ChunkCoordinate(chunkX, chunkZ)));
    }

    public boolean allowOrNotify(ServerPlayerEntity player, World world, BlockPos position) {
        if (!denies(world, position)) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long previous = lastNotification.put(player.getUuid(), now);
        if (previous == null || now - previous >= notificationCooldownMillis) {
            player.sendMessage(
                    Text.literal(RealmMessageKey.BACKUP_LOCKED.fallback(Map.of())),
                    true);
        }
        return false;
    }

    public boolean realmLocked(long realmId) {
        return locks.containsKey(realmId);
    }

    public Optional<Long> entryBlockedRealm(World world, BlockPos position) {
        if (!DimensionId.REALMS.toString().equals(world.getRegistryKey().getValue().toString())) {
            return Optional.empty();
        }
        int chunkX = Math.floorDiv(position.getX(), 16);
        int chunkZ = Math.floorDiv(position.getZ(), 16);
        var coordinate = new eu.avalanche7.paradigmrealms.backup.ChunkCoordinate(chunkX, chunkZ);
        return locks.values().stream()
                .filter(ActiveLock::entryBlocked)
                .filter(lock -> lock.bounds().contains(coordinate))
                .map(ActiveLock::realmId)
                .findFirst();
    }

    public int activeCount() {
        return locks.size();
    }

    public void clear() {
        locks.clear();
        lastNotification.clear();
    }

    private record ActiveLock(
            long realmId,
            BackupCellBounds bounds,
            UUID operationId,
            boolean entryBlocked) {}

    public final class Handle implements AutoCloseable {
        private final ActiveLock lock;
        private boolean closed;

        private Handle(ActiveLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            locks.remove(lock.realmId(), lock);
            closed = true;
        }
    }
}
