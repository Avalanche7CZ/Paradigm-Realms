package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.backup.json.Json;

public final class BackupCatalogJsonCodec {
    private static final int CATALOG_VERSION = 1;

    public String encode(List<BackupCatalogEntry> entries) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("catalogVersion", CATALOG_VERSION);
        root.put("entries", entries.stream().map(this::encodeEntry).toList());
        return Json.write(root);
    }

    public List<BackupCatalogEntry> decode(String source) {
        Map<String, Object> root = BackupManifestJsonCodec.object(Json.parse(source), "root");
        int version = BackupManifestJsonCodec.integer(root, "catalogVersion", "root");
        if (version != CATALOG_VERSION) {
            throw new IllegalArgumentException("unsupported backup catalog version " + version);
        }

        List<Object> values = BackupManifestJsonCodec.list(
                BackupManifestJsonCodec.required(root, "entries", "root"),
                "root.entries");
        List<BackupCatalogEntry> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            result.add(decodeEntry(BackupManifestJsonCodec.object(
                    values.get(index),
                    "root.entries[" + index + ']')));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> encodeEntry(BackupCatalogEntry entry) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("backupId", entry.backupId().value());
        value.put("realmId", entry.realmId());
        value.put("ownerUuid", entry.ownerUuid().toString());
        value.put("ownerNameSnapshot", entry.ownerNameSnapshot());
        value.put("createdAt", entry.createdAt().toString());
        value.put("reason", entry.reason().name());
        value.put("sizeBytes", entry.sizeBytes());
        value.put("archiveRelativePath", entry.archiveRelativePath());
        value.put("integrityStatus", entry.integrityStatus().name());
        value.put("pinned", entry.pinned());
        value.put("restoreInUse", entry.restoreInUse());
        value.put("formatVersion", entry.formatVersion());
        value.put("minecraftVersion", entry.minecraftVersion());
        value.put("realmsVersion", entry.realmsVersion());

        Map<String, Object> counts = new LinkedHashMap<>();
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            counts.put(kind.directory(), entry.chunkCounts().getOrDefault(kind, 0));
        }
        value.put("chunkCounts", counts);
        return value;
    }

    private BackupCatalogEntry decodeEntry(Map<String, Object> value) {
        Map<String, Object> rawCounts = BackupManifestJsonCodec.object(
                BackupManifestJsonCodec.required(value, "chunkCounts", "catalog entry"),
                "catalog entry.chunkCounts");
        EnumMap<BackupStorageKind, Integer> counts = new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            counts.put(kind, BackupManifestJsonCodec.integer(
                    rawCounts,
                    kind.directory(),
                    "catalog entry.chunkCounts"));
        }

        return new BackupCatalogEntry(
                new BackupId(BackupManifestJsonCodec.string(value, "backupId", "catalog entry")),
                BackupManifestJsonCodec.longValue(value, "realmId", "catalog entry"),
                UUID.fromString(BackupManifestJsonCodec.string(value, "ownerUuid", "catalog entry")),
                BackupManifestJsonCodec.string(value, "ownerNameSnapshot", "catalog entry"),
                Instant.parse(BackupManifestJsonCodec.string(value, "createdAt", "catalog entry")),
                BackupReason.valueOf(BackupManifestJsonCodec.string(value, "reason", "catalog entry")),
                BackupManifestJsonCodec.longValue(value, "sizeBytes", "catalog entry"),
                BackupManifestJsonCodec.string(value, "archiveRelativePath", "catalog entry"),
                BackupIntegrityStatus.valueOf(BackupManifestJsonCodec.string(
                        value,
                        "integrityStatus",
                        "catalog entry")),
                BackupManifestJsonCodec.bool(value, "pinned", "catalog entry"),
                BackupManifestJsonCodec.bool(value, "restoreInUse", "catalog entry"),
                BackupManifestJsonCodec.integer(value, "formatVersion", "catalog entry"),
                BackupManifestJsonCodec.string(value, "minecraftVersion", "catalog entry"),
                BackupManifestJsonCodec.string(value, "realmsVersion", "catalog entry"),
                counts);
    }
}
