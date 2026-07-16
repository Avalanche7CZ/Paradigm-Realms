package eu.avalanche7.paradigmrealms.backup;

import java.util.Objects;
import java.util.Optional;

public record BackupDeletionResult(
        boolean successful,
        String message,
        Optional<String> confirmationToken) {
    public BackupDeletionResult {
        Objects.requireNonNull(message, "message");
        confirmationToken = Objects.requireNonNull(confirmationToken, "confirmationToken");
    }

    public static BackupDeletionResult confirmation(String token) {
        return new BackupDeletionResult(
                false,
                "Confirm this backup deletion with the scoped token below.",
                Optional.of(token));
    }

    public static BackupDeletionResult completed() {
        return new BackupDeletionResult(true, "Backup deleted.", Optional.empty());
    }

    public static BackupDeletionResult failed(String message) {
        return new BackupDeletionResult(false, message, Optional.empty());
    }
}
