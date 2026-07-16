package eu.avalanche7.paradigmrealms.backup;

public enum BackupFailure {
    REALM_NOT_FOUND, REALM_NOT_ACTIVE, REALM_BUSY, QUEUE_FULL, COOLDOWN_ACTIVE,
    INSUFFICIENT_SPACE, LOCK_TIMEOUT, SAVE_BARRIER_FAILED, CAPTURE_FAILED,
    PACKAGE_FAILED, VERIFICATION_FAILED, CATALOG_FAILED, CANCELLED, INTERNAL_ERROR;

    public String playerMessage() {
        return switch (this) {
            case REALM_NOT_FOUND -> "No realm was found for that backup request.";
            case REALM_NOT_ACTIVE -> "Only an active realm can be backed up right now.";
            case REALM_BUSY -> "This realm is currently busy with another operation. Try again when it is complete.";
            case QUEUE_FULL -> "The realm backup queue is full. Please try again later.";
            case COOLDOWN_ACTIVE -> "Your next realm backup is not available yet.";
            case INSUFFICIENT_SPACE -> "The server does not have enough free space for another realm backup.";
            case LOCK_TIMEOUT -> "The realm could not be made read-only in time. No data was changed.";
            case SAVE_BARRIER_FAILED, CAPTURE_FAILED, PACKAGE_FAILED, VERIFICATION_FAILED, CATALOG_FAILED, INTERNAL_ERROR ->
                    "The realm backup could not be completed. Your realm was unlocked and no existing data was changed.";
            case CANCELLED -> "The realm backup was cancelled. Your realm is available again.";
        };
    }
}
