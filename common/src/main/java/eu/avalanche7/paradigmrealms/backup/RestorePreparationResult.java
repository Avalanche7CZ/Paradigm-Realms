package eu.avalanche7.paradigmrealms.backup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RestorePreparationResult(
        Status status,
        Optional<UUID> operationId,
        Optional<BackupId> rollbackBackupId,
        String message) {
    public RestorePreparationResult {
        Objects.requireNonNull(status, "status");
        operationId = Objects.requireNonNull(operationId, "operationId");
        rollbackBackupId = Objects.requireNonNull(rollbackBackupId, "rollbackBackupId");
        message = Objects.requireNonNull(message, "message");
    }

    public static RestorePreparationResult failed(Status status, String message) {
        return new RestorePreparationResult(
                status,
                Optional.empty(),
                Optional.empty(),
                message);
    }

    public static RestorePreparationResult prepared(
            UUID operationId,
            BackupId rollbackBackupId) {
        return new RestorePreparationResult(
                Status.PREPARED,
                Optional.of(operationId),
                Optional.of(rollbackBackupId),
                "Offline restore prepared. Stop the server before running the restore tool.");
    }

    public enum Status {
        PREPARED,
        BACKUP_NOT_FOUND,
        BACKUP_INVALID,
        TARGET_MISMATCH,
        TARGET_BUSY,
        ROLLBACK_BACKUP_FAILED,
        EVACUATION_FAILED,
        MANIFEST_WRITE_FAILED,
        UNSUPPORTED_MODE
    }
}
