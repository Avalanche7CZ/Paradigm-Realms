package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.backup.json.Json;

public final class RealmMetadataJsonCodec {
    public String encode(RealmMetadataSnapshot snapshot) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("schemaVersion", snapshot.schemaVersion()); value.put("realmId", snapshot.realmId());
        value.put("ownerUuid", snapshot.ownerUuid().toString()); value.put("ownerNameSnapshot", snapshot.ownerNameSnapshot());
        value.put("displayName", snapshot.displayName()); value.put("description", snapshot.description());
        value.put("presetId", snapshot.presetId()); value.put("lifecycleState", snapshot.lifecycleState());
        value.put("dimension", snapshot.dimension()); value.put("allocationProfile", snapshot.allocationProfile());
        value.put("allocation", BackupManifestJsonCodec.bounds(snapshot.allocation()));
        Map<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("x", snapshot.spawnX()); spawn.put("y", snapshot.spawnY()); spawn.put("z", snapshot.spawnZ());
        spawn.put("yaw", snapshot.spawnYaw()); spawn.put("pitch", snapshot.spawnPitch()); value.put("spawn", spawn);
        value.put("accessPolicy", snapshot.accessPolicy()); value.put("members", strings(snapshot.members()));
        value.put("managers", strings(snapshot.managers())); value.put("invitedVisitors", strings(snapshot.invitedVisitors()));
        Map<String, Object> bans = new LinkedHashMap<>(); snapshot.bans().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> bans.put(entry.getKey().toString(), entry.getValue()));
        value.put("bans", bans); value.put("settings", new LinkedHashMap<>(snapshot.settings()));
        value.put("listed", snapshot.listed()); value.put("createdAt", snapshot.createdAt().toString());
        return Json.write(value);
    }

    public RealmMetadataSnapshot decode(String json) {
        Map<String, Object> value = BackupManifestJsonCodec.object(Json.parse(json), "root");
        Map<String, Object> spawn = BackupManifestJsonCodec.object(
                BackupManifestJsonCodec.required(value, "spawn", "root"), "root.spawn");
        Map<String, Object> bansValue = BackupManifestJsonCodec.object(
                BackupManifestJsonCodec.required(value, "bans", "root"), "root.bans");
        Map<UUID, String> bans = new LinkedHashMap<>();
        bansValue.forEach((key, raw) -> {
            if (!(raw instanceof String reason)) throw new IllegalArgumentException("ban reason must be a string");
            bans.put(UUID.fromString(key), reason);
        });
        Map<String, Object> settingsValue = BackupManifestJsonCodec.object(
                BackupManifestJsonCodec.required(value, "settings", "root"), "root.settings");
        Map<String, Boolean> settings = new LinkedHashMap<>();
        settingsValue.forEach((key, raw) -> {
            if (!(raw instanceof Boolean setting)) throw new IllegalArgumentException("setting must be boolean");
            settings.put(key, setting);
        });
        return new RealmMetadataSnapshot(BackupManifestJsonCodec.integer(value, "schemaVersion", "root"),
                BackupManifestJsonCodec.longValue(value, "realmId", "root"),
                UUID.fromString(BackupManifestJsonCodec.string(value, "ownerUuid", "root")),
                BackupManifestJsonCodec.string(value, "ownerNameSnapshot", "root"),
                BackupManifestJsonCodec.string(value, "displayName", "root"),
                BackupManifestJsonCodec.string(value, "description", "root"),
                BackupManifestJsonCodec.string(value, "presetId", "root"),
                BackupManifestJsonCodec.string(value, "lifecycleState", "root"),
                BackupManifestJsonCodec.string(value, "dimension", "root"),
                BackupManifestJsonCodec.optionalString(value, "allocationProfile").orElse("custom-v1"),
                BackupManifestJsonCodec.decodeBounds(BackupManifestJsonCodec.object(
                        BackupManifestJsonCodec.required(value, "allocation", "root"), "root.allocation")),
                decimal(spawn, "x"), decimal(spawn, "y"), decimal(spawn, "z"),
                (float) decimal(spawn, "yaw"), (float) decimal(spawn, "pitch"),
                BackupManifestJsonCodec.string(value, "accessPolicy", "root"), uuids(value, "members"),
                uuids(value, "managers"), uuids(value, "invitedVisitors"), bans, settings,
                BackupManifestJsonCodec.bool(value, "listed", "root"),
                Instant.parse(BackupManifestJsonCodec.string(value, "createdAt", "root")));
    }

    private static List<String> strings(List<UUID> values) { return values.stream().map(UUID::toString).toList(); }
    private static List<UUID> uuids(Map<String, Object> value, String key) {
        List<Object> raw = BackupManifestJsonCodec.list(
                BackupManifestJsonCodec.required(value, key, "root"), "root." + key);
        ArrayList<UUID> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof String string)) throw new IllegalArgumentException(key + " must contain UUID strings");
            result.add(UUID.fromString(string));
        }
        return List.copyOf(result);
    }
    private static double decimal(Map<String, Object> value, String key) {
        Object raw = BackupManifestJsonCodec.required(value, key, "spawn");
        if (!(raw instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("spawn coordinate must be finite");
        }
        return number.doubleValue();
    }
}
