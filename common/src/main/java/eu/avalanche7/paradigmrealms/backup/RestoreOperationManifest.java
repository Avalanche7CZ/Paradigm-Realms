package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RestoreOperationManifest(int manifestVersion, UUID operationId, BackupId backupId,
        long realmId, UUID expectedOwnerUuid, BackupCellBounds targetBounds, String dimension,
        String allocationProfile, BackupStrategy strategy, String worldIdentity,
        String realmStateSha256,
        String archiveRelativePath, String dimensionRelativePath,
        String quarantineRelativePath, BackupId rollbackBackupId, RestoreMode mode,
        RestoreManifestStage stage, Instant createdAt, Instant updatedAt,
        Optional<String> failureCode, Optional<String> failureDetail) {
    public static final int CURRENT_VERSION = 2;

    public RestoreOperationManifest {
        if (manifestVersion < 1 || manifestVersion > CURRENT_VERSION || realmId < 1) {
            throw new IllegalArgumentException("invalid restore manifest identity");
        }
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(backupId, "backupId");
        Objects.requireNonNull(expectedOwnerUuid, "expectedOwnerUuid");
        Objects.requireNonNull(targetBounds, "targetBounds");
        dimension = safe(dimension, false);
        allocationProfile = safe(allocationProfile, false);
        Objects.requireNonNull(strategy, "strategy");
        worldIdentity = safe(worldIdentity, false);
        realmStateSha256 = digest(realmStateSha256);
        archiveRelativePath = safe(archiveRelativePath, true);
        dimensionRelativePath = safe(dimensionRelativePath, true);
        quarantineRelativePath = safe(quarantineRelativePath, true);
        Objects.requireNonNull(rollbackBackupId, "rollbackBackupId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail");
        if (stage == RestoreManifestStage.FAILED && failureCode.isEmpty()) {
            throw new IllegalArgumentException("failed restore requires a failure code");
        }
    }

    public RestoreOperationManifest withStage(RestoreManifestStage value, Instant at) {
        return new RestoreOperationManifest(manifestVersion, operationId, backupId, realmId, expectedOwnerUuid,
                targetBounds, dimension, allocationProfile, strategy, worldIdentity, realmStateSha256, archiveRelativePath,
                dimensionRelativePath, quarantineRelativePath, rollbackBackupId, mode, value,
                createdAt, at, failureCode, failureDetail);
    }

    public RestoreOperationManifest failed(String code, String detail, Instant at) {
        return new RestoreOperationManifest(manifestVersion, operationId, backupId, realmId, expectedOwnerUuid,
                targetBounds, dimension, allocationProfile, strategy, worldIdentity, realmStateSha256, archiveRelativePath,
                dimensionRelativePath, quarantineRelativePath, rollbackBackupId, mode,
                RestoreManifestStage.FAILED, createdAt, at,
                Optional.of(code), Optional.ofNullable(detail));
    }

    public RestoreOperationManifest(int manifestVersion, UUID operationId, BackupId backupId,
            long realmId, UUID expectedOwnerUuid, BackupCellBounds targetBounds, String dimension,
            String worldIdentity, String archiveRelativePath, String dimensionRelativePath,
            String quarantineRelativePath, BackupId rollbackBackupId, RestoreMode mode,
            RestoreManifestStage stage, Instant createdAt, Instant updatedAt,
            Optional<String> failureCode, Optional<String> failureDetail) {
        this(manifestVersion, operationId, backupId, realmId, expectedOwnerUuid, targetBounds, dimension,
                "custom-v1", BackupStrategy.CHUNK_EXTRACT, worldIdentity,
                "0000000000000000000000000000000000000000000000000000000000000000", archiveRelativePath,
                dimensionRelativePath, quarantineRelativePath, rollbackBackupId, mode, stage,
                createdAt, updatedAt, failureCode, failureDetail);
    }

    private static String safe(String value, boolean path) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.length() > 512) {
            throw new IllegalArgumentException("invalid restore string");
        }
        if (path && (value.startsWith("/")
                || value.contains("..")
                || value.contains("\\")
                || value.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("unsafe restore path");
        }
        return value;
    }

    private static String digest(String value) {
        Objects.requireNonNull(value, "realmStateSha256");
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid realm-state checksum");
        }
        return value;
    }
}
