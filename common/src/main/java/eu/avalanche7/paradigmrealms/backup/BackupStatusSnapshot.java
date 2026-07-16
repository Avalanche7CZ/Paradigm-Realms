package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record BackupStatusSnapshot(
        int queueLength,
        Optional<BackupOperation> activeOperation,
        Optional<Instant> nextDue,
        int catalogSize,
        int activeLocks) {
    public BackupStatusSnapshot {
        if (queueLength < 0 || catalogSize < 0 || activeLocks < 0) {
            throw new IllegalArgumentException("backup status counts cannot be negative");
        }
        activeOperation = Objects.requireNonNull(activeOperation, "activeOperation");
        nextDue = Objects.requireNonNull(nextDue, "nextDue");
    }
}
