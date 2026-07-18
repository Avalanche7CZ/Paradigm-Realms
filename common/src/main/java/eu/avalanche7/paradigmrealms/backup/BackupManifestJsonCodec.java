package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.backup.json.Json;

public final class BackupManifestJsonCodec {
    public String encode(BackupManifest manifest) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("formatVersion", manifest.formatVersion());
        root.put("backupId", manifest.backupId().value());
        root.put("createdAt", manifest.createdAt().toString());
        root.put("createdBy", actor(manifest.createdBy()));
        root.put("reason", manifest.reason().name());
        root.put("minecraftVersion", manifest.minecraftVersion());
        root.put("realmsVersion", manifest.realmsVersion());
        root.put("dimension", manifest.dimension());
        root.put("worldIdentity", manifest.worldIdentity());
        Map<String, Object> realm = new LinkedHashMap<>();
        realm.put("id", manifest.realmId());
        realm.put("ownerUuid", manifest.ownerUuid().toString());
        realm.put("ownerNameSnapshot", manifest.ownerNameSnapshot());
        realm.put("preset", manifest.preset());
        realm.put("lifecycleState", manifest.lifecycleState());
        realm.put("allocationProfile", manifest.allocationProfile());
        root.put("realm", realm);
        root.put("cellBounds", bounds(manifest.cellBounds()));
        root.put("strategy", manifest.strategy().name());
        Map<String, Object> included = new LinkedHashMap<>();
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            included.put(kind.directory(), manifest.chunks().get(kind).stream()
                    .map(coordinate -> List.of(coordinate.x(), coordinate.z())).toList());
        }
        root.put("included", included);
        root.put("regionFiles", manifest.regionFiles());
        root.put("metadataMode", manifest.metadataMode());
        root.put("pinned", manifest.pinned());
        return Json.write(root);
    }

    public BackupManifest decode(String json) {
        Map<String, Object> root = object(Json.parse(json), "root");
        int version = integer(root, "formatVersion", "root");
        if (version < 1 || version > BackupManifest.CURRENT_FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported backup format version " + version);
        }
        Map<String, Object> actor = object(required(root, "createdBy", "root"), "root.createdBy");
        Map<String, Object> realm = object(required(root, "realm", "root"), "root.realm");
        Map<String, Object> included = object(required(root, "included", "root"), "root.included");
        EnumMap<BackupStorageKind, List<ChunkCoordinate>> chunks = new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            List<Object> list = list(required(included, kind.directory(), "root.included"),
                    "root.included." + kind.directory());
            ArrayList<ChunkCoordinate> coordinates = new ArrayList<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                List<Object> pair = list(list.get(i), "root.included." + kind.directory() + '[' + i + ']');
                if (pair.size() != 2) throw new IllegalArgumentException("chunk coordinate must have two values");
                coordinates.add(new ChunkCoordinate(exactInt(pair.get(0), "chunk x"), exactInt(pair.get(1), "chunk z")));
            }
            chunks.put(kind, coordinates);
        }
        return new BackupManifest(version, new BackupId(string(root, "backupId", "root")),
                Instant.parse(string(root, "createdAt", "root")),
                new BackupActor(BackupActor.Type.valueOf(string(actor, "type", "root.createdBy")),
                        optionalString(actor, "uuid").map(UUID::fromString),
                        string(actor, "nameSnapshot", "root.createdBy")),
                BackupReason.valueOf(string(root, "reason", "root")),
                string(root, "minecraftVersion", "root"), string(root, "realmsVersion", "root"),
                string(root, "dimension", "root"), string(root, "worldIdentity", "root"),
                longValue(realm, "id", "root.realm"), UUID.fromString(string(realm, "ownerUuid", "root.realm")),
                string(realm, "ownerNameSnapshot", "root.realm"), string(realm, "preset", "root.realm"),
                string(realm, "lifecycleState", "root.realm"),
                version == 1 ? "custom-v1" : string(realm, "allocationProfile", "root.realm"),
                decodeBounds(object(required(root, "cellBounds", "root"), "root.cellBounds")),
                version == 1 ? BackupStrategy.CHUNK_EXTRACT
                        : BackupStrategy.valueOf(string(root, "strategy", "root")),
                chunks, version == 1 ? List.of() : stringList(root, "regionFiles", "root"),
                string(root, "metadataMode", "root"), bool(root, "pinned", "root"));
    }

    private static Map<String, Object> actor(BackupActor actor) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", actor.type().name());
        actor.uuid().ifPresent(uuid -> value.put("uuid", uuid.toString()));
        value.put("nameSnapshot", actor.nameSnapshot());
        return value;
    }

    static Map<String, Object> bounds(BackupCellBounds bounds) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("minimumChunkX", bounds.minimumChunkX()); value.put("minimumChunkZ", bounds.minimumChunkZ());
        value.put("maximumChunkX", bounds.maximumChunkX()); value.put("maximumChunkZ", bounds.maximumChunkZ());
        return value;
    }

    static BackupCellBounds decodeBounds(Map<String, Object> value) {
        return new BackupCellBounds(integer(value, "minimumChunkX", "cellBounds"),
                integer(value, "minimumChunkZ", "cellBounds"),
                integer(value, "maximumChunkX", "cellBounds"),
                integer(value, "maximumChunkZ", "cellBounds"));
    }

    static Object required(Map<String, Object> value, String key, String path) {
        if (!value.containsKey(key) || value.get(key) == null) throw new IllegalArgumentException("missing " + path + '.' + key);
        return value.get(key);
    }
    @SuppressWarnings("unchecked") static Map<String, Object> object(Object value, String path) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(path + " must be an object");
        return (Map<String, Object>) value;
    }
    @SuppressWarnings("unchecked") static List<Object> list(Object value, String path) {
        if (!(value instanceof List<?>)) throw new IllegalArgumentException(path + " must be a list");
        return (List<Object>) value;
    }
    static String string(Map<String, Object> value, String key, String path) {
        Object raw = required(value, key, path);
        if (!(raw instanceof String string)) throw new IllegalArgumentException(path + '.' + key + " must be a string");
        return string;
    }
    static Optional<String> optionalString(Map<String, Object> value, String key) {
        Object raw = value.get(key); if (raw == null) return Optional.empty();
        if (!(raw instanceof String string)) throw new IllegalArgumentException(key + " must be a string");
        return Optional.of(string);
    }
    static List<String> stringList(Map<String, Object> value, String key, String path) {
        List<Object> raw = list(required(value, key, path), path + '.' + key);
        ArrayList<String> result = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (!(item instanceof String string)) throw new IllegalArgumentException(path + '.' + key + " must contain strings");
            result.add(string);
        }
        return List.copyOf(result);
    }
    static int integer(Map<String, Object> value, String key, String path) {
        return exactInt(required(value, key, path), path + '.' + key);
    }
    static int exactInt(Object raw, String path) {
        long value = exactLong(raw, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException(path + " is out of range");
        return (int) value;
    }
    static long longValue(Map<String, Object> value, String key, String path) {
        return exactLong(required(value, key, path), path + '.' + key);
    }
    static long exactLong(Object raw, String path) {
        if (!(raw instanceof Number number)) throw new IllegalArgumentException(path + " must be a number");
        double decimal = number.doubleValue(); long value = number.longValue();
        if (!Double.isFinite(decimal) || decimal != value) throw new IllegalArgumentException(path + " must be an integer");
        return value;
    }
    static boolean bool(Map<String, Object> value, String key, String path) {
        Object raw = required(value, key, path);
        if (!(raw instanceof Boolean result)) throw new IllegalArgumentException(path + '.' + key + " must be boolean");
        return result;
    }
}
