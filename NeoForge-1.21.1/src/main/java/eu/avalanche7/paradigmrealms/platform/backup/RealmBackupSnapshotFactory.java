package eu.avalanche7.paradigmrealms.platform.backup;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.RealmMetadataSnapshot;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSetting;
import net.minecraft.server.MinecraftServer;

final class RealmBackupSnapshotFactory {
    private final MinecraftServer server;

    RealmBackupSnapshotFactory(MinecraftServer server) {
        this.server = server;
    }

    String ownerName(Realm realm) {
        var online = server.getPlayerManager().getPlayer(realm.owner().uuid());
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getUserCache()
                .getByUuid(realm.owner().uuid())
                .map(profile -> profile.getName())
                .orElse("UnknownOwner");
    }

    RealmMetadataSnapshot create(Realm realm, String ownerName) {
        Map<UUID, String> bans = new LinkedHashMap<>();
        realm.bans().forEach((uuid, ban) -> bans.put(
                uuid,
                ban.reason().orElse("Banned by " + ban.actorUuid())));

        Map<String, Boolean> settings = new LinkedHashMap<>();
        for (RealmSetting setting : RealmSetting.values()) {
            settings.put(setting.name(), realm.settings().value(setting));
        }

        return new RealmMetadataSnapshot(
                realm.schemaVersion().value(),
                realm.id().value(),
                realm.owner().uuid(),
                ownerName,
                realm.displayName(),
                realm.description(),
                realm.preset().value(),
                realm.state().name(),
                realm.dimension().toString(),
                realm.allocation().profile().value(),
                BackupCellBounds.from(realm.allocation().cellBounds()),
                realm.spawn().x(),
                realm.spawn().y(),
                realm.spawn().z(),
                realm.spawn().yaw(),
                realm.spawn().pitch(),
                realm.accessPolicy().name(),
                sorted(realm.members()),
                sorted(realm.managers()),
                sorted(realm.invitedVisitors()),
                bans,
                settings,
                realm.listed(),
                realm.createdAt().toInstant());
    }

    private static List<UUID> sorted(java.util.Set<UUID> values) {
        return values.stream().sorted().toList();
    }
}
