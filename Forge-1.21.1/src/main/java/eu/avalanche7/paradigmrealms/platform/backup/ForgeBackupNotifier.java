package eu.avalanche7.paradigmrealms.platform.backup;

import java.time.Duration;
import java.util.function.Consumer;

import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupReason;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.backup.RealmBackupConfig;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.message.RealmMessageKey;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessenger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

final class ForgeBackupNotifier {
    private final MinecraftServer server;
    private final RealmBackupConfig config;
    private final java.util.function.Supplier<ServerWorld> realmsWorld;
    private final CommandMessenger messages;

    ForgeBackupNotifier(
            MinecraftServer server,
            RealmBackupConfig config,
            CommandMessenger messages,
            java.util.function.Supplier<ServerWorld> realmsWorld) {
        this.server = server;
        this.config = config;
        this.messages = messages;
        this.realmsWorld = realmsWorld;
    }

    void captureStarted(Realm realm) {
        forEachOccupant(realm, player -> {
            sendChat(player, RealmMessageKey.BACKUP_CAPTURE_STARTED, realmValues(realm));
            player.sendMessage(Text.literal("Saving realm backup...")
                    .formatted(net.minecraft.util.Formatting.AQUA), true);
        });
    }

    void progress(Realm realm, int captured, int total) {
        java.util.Map<String, String> values = java.util.Map.of(
                "captured", Integer.toString(captured),
                "total", Integer.toString(total));
        server.execute(() -> forEachOccupant(realm, player -> player.sendMessage(
                Text.literal(RealmMessageKey.BACKUP_PROGRESS.fallback(values)),
                true)));
    }

    void completed(Realm realm, BackupCatalogEntry entry, Duration captureDuration) {
        if (entry.reason() == BackupReason.AUTOMATIC && !config.automatic().notifyOwners()) {
            return;
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(realm.owner().uuid());
        if (owner == null) {
            return;
        }

        int records = entry.payloadFileCount();
        if (entry.reason() == BackupReason.AUTOMATIC) {
            sendChat(owner, RealmMessageKey.BACKUP_AUTOMATIC_COMPLETED, java.util.Map.of());
            return;
        }
        sendChat(owner, RealmMessageKey.BACKUP_COMPLETED, java.util.Map.of(
                "realm_name", realm.displayName(),
                "realm_id", Long.toString(realm.id().value()),
                "records", Integer.toString(records),
                "size", humanBytes(entry.sizeBytes()),
                "duration", captureDuration.toMillis() + " ms"));
    }

    void failed(Realm realm, BackupReason reason) {
        if (reason == BackupReason.AUTOMATIC) {
            if (config.automatic().notifyAdministratorsOnFailure()) {
                server.getPlayerManager().getPlayerList().stream()
                        .filter(player -> player.hasPermissionLevel(3))
                        .forEach(player -> sendChat(
                                player,
                                RealmMessageKey.BACKUP_AUTOMATIC_FAILED,
                                java.util.Map.of(
                                        "realm_id", Long.toString(realm.id().value()))));
            }
            return;
        }

        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(realm.owner().uuid());
        if (owner == null) {
            return;
        }
        sendChat(owner, RealmMessageKey.BACKUP_FAILED, java.util.Map.of());
    }

    private void forEachOccupant(Realm realm, Consumer<ServerPlayerEntity> action) {
        BackupCellBounds bounds = BackupCellBounds.from(realm.allocation().cellBounds());
        for (ServerPlayerEntity player : realmsWorld.get().getPlayers()) {
            ChunkCoordinate chunk = new ChunkCoordinate(
                    player.getChunkPos().x,
                    player.getChunkPos().z);
            if (bounds.contains(chunk)) {
                action.accept(player);
            }
        }
    }

    private void sendChat(
            ServerPlayerEntity player,
            RealmMessageKey key,
            java.util.Map<String, String> values) {
        messages.send(
                player.getCommandSource(),
                key.template(),
                values,
                key.fallback(values));
    }

    private static java.util.Map<String, String> realmValues(Realm realm) {
        return java.util.Map.of(
                "realm_name", realm.displayName(),
                "realm_id", Long.toString(realm.id().value()));
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(
                java.util.Locale.ROOT,
                "%.1f MiB",
                bytes / (1024.0 * 1024.0));
    }
}
