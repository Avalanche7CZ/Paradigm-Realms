package eu.avalanche7.paradigmrealms.backup;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record BackupRequestResult(
        boolean accepted,
        Optional<BackupId> backupId,
        int queuePosition,
        Optional<BackupFailure> failure,
        String message,
        Optional<Duration> cooldownRemaining) {
    public BackupRequestResult {
        backupId = Objects.requireNonNull(backupId, "backupId");
        failure = Objects.requireNonNull(failure, "failure");
        message = Objects.requireNonNull(message, "message");
        cooldownRemaining = Objects.requireNonNull(cooldownRemaining, "cooldownRemaining");
        if (accepted != backupId.isPresent() || accepted == failure.isPresent()) {
            throw new IllegalArgumentException("invalid backup request result");
        }
    }

    public static BackupRequestResult queued(BackupId backupId, int position) {
        return new BackupRequestResult(
                true,
                Optional.of(backupId),
                position,
                Optional.empty(),
                "Your realm backup has been queued.",
                Optional.empty());
    }

    public static BackupRequestResult rejected(BackupFailure failure, String message) {
        return new BackupRequestResult(
                false,
                Optional.empty(),
                -1,
                Optional.of(failure),
                message,
                Optional.empty());
    }
}
