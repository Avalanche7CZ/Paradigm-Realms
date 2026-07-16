package eu.avalanche7.paradigmrealms.backup;

public final class PreOperationBackupPolicy {
    public Decision decide(Operation operation, RealmBackupConfig.PreOperation config) {
        return switch (operation) {
            case RESET -> new Decision(config.beforeReset(), config.requireSuccessfulBackupForReset(), BackupReason.PRE_RESET, false);
            case DELETE -> new Decision(config.beforeDelete(), config.requireSuccessfulBackupForDelete(), BackupReason.PRE_DELETE, false);
            case ARCHIVE_RESTORE -> new Decision(config.beforeArchiveRestore(), config.requireSuccessfulBackupForRestore(), BackupReason.PRE_ARCHIVE_RESTORE, true);
            case MANUAL_RESTORE -> new Decision(true, config.requireSuccessfulBackupForRestore(), BackupReason.PRE_MANUAL_RESTORE, true);
            case OWNERSHIP_TRANSFER -> new Decision(config.beforeOwnershipTransfer(), false, BackupReason.PRE_OWNERSHIP_TRANSFER, false);
        };
    }
    public enum Operation { RESET, DELETE, ARCHIVE_RESTORE, MANUAL_RESTORE, OWNERSHIP_TRANSFER }
    public record Decision(boolean createBackup, boolean successRequired, BackupReason reason, boolean pinUntilVerified) {}
}
