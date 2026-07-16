package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.backup.json.Json;

public final class RestoreOperationManifestJsonCodec {
    public String encode(RestoreOperationManifest manifest) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("manifestVersion", manifest.manifestVersion());
        root.put("operationId", manifest.operationId().toString());
        root.put("backupId", manifest.backupId().value());
        root.put("realmId", manifest.realmId());
        root.put("expectedOwnerUuid", manifest.expectedOwnerUuid().toString());
        root.put("targetBounds", BackupManifestJsonCodec.bounds(manifest.targetBounds()));
        root.put("dimension", manifest.dimension());
        root.put("worldIdentity", manifest.worldIdentity());
        root.put("archiveRelativePath", manifest.archiveRelativePath());
        root.put("dimensionRelativePath", manifest.dimensionRelativePath());
        root.put("quarantineRelativePath", manifest.quarantineRelativePath());
        root.put("rollbackBackupId", manifest.rollbackBackupId().value());
        root.put("mode", manifest.mode().name());
        root.put("stage", manifest.stage().name());
        root.put("createdAt", manifest.createdAt().toString());
        root.put("updatedAt", manifest.updatedAt().toString());
        manifest.failureCode().ifPresent(value -> root.put("failureCode", value));
        manifest.failureDetail().ifPresent(value -> root.put("failureDetail", value));
        return Json.write(root);
    }

    public RestoreOperationManifest decode(String source) {
        Map<String, Object> root = BackupManifestJsonCodec.object(Json.parse(source), "root");
        Map<String, Object> bounds = BackupManifestJsonCodec.object(
                BackupManifestJsonCodec.required(root, "targetBounds", "root"),
                "root.targetBounds");

        return new RestoreOperationManifest(
                BackupManifestJsonCodec.integer(root, "manifestVersion", "root"),
                UUID.fromString(BackupManifestJsonCodec.string(root, "operationId", "root")),
                new BackupId(BackupManifestJsonCodec.string(root, "backupId", "root")),
                BackupManifestJsonCodec.longValue(root, "realmId", "root"),
                UUID.fromString(BackupManifestJsonCodec.string(root, "expectedOwnerUuid", "root")),
                BackupManifestJsonCodec.decodeBounds(bounds),
                BackupManifestJsonCodec.string(root, "dimension", "root"),
                BackupManifestJsonCodec.string(root, "worldIdentity", "root"),
                BackupManifestJsonCodec.string(root, "archiveRelativePath", "root"),
                BackupManifestJsonCodec.string(root, "dimensionRelativePath", "root"),
                BackupManifestJsonCodec.string(root, "quarantineRelativePath", "root"),
                new BackupId(BackupManifestJsonCodec.string(root, "rollbackBackupId", "root")),
                RestoreMode.valueOf(BackupManifestJsonCodec.string(root, "mode", "root")),
                RestoreManifestStage.valueOf(BackupManifestJsonCodec.string(root, "stage", "root")),
                Instant.parse(BackupManifestJsonCodec.string(root, "createdAt", "root")),
                Instant.parse(BackupManifestJsonCodec.string(root, "updatedAt", "root")),
                BackupManifestJsonCodec.optionalString(root, "failureCode"),
                BackupManifestJsonCodec.optionalString(root, "failureDetail"));
    }
}
