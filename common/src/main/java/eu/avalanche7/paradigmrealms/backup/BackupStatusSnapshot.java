package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record BackupStatusSnapshot(
        int queueLength,
        Optional<BackupOperation> activeOperation,
        Optional<Instant> nextDue,
        int catalogSize,
        int activeLocks,
        int runningOperations) {
    public BackupStatusSnapshot(int queueLength, Optional<BackupOperation> activeOperation,
            Optional<Instant> nextScheduledBackup, int catalogSize, int activeLocks) {
        this(queueLength, activeOperation, nextScheduledBackup, catalogSize, activeLocks,
                activeOperation.isPresent() ? 1 : 0);
    }
    public BackupStatusSnapshot {
        if (queueLength < 0 || catalogSize < 0 || activeLocks < 0 || runningOperations < 0) {
            throw new IllegalArgumentException("backup status counts cannot be negative");
        }
        activeOperation = Objects.requireNonNull(activeOperation, "activeOperation");
        nextDue = Objects.requireNonNull(nextDue, "nextDue");
    }
}
