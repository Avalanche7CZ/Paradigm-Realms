package eu.avalanche7.paradigmrealms.backup;

public enum BackupFailure {
    REALM_NOT_FOUND, REALM_NOT_ACTIVE, REALM_BUSY, QUEUE_FULL, COOLDOWN_ACTIVE,
    INSUFFICIENT_SPACE, LOCK_TIMEOUT, SAVE_BARRIER_FAILED, CAPTURE_FAILED,
    PACKAGE_FAILED, VERIFICATION_FAILED, CATALOG_FAILED, CANCELLED, INTERNAL_ERROR;

    public String playerMessage() {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                "backup_failures." + name().toLowerCase(java.util.Locale.ROOT));
    }
}
