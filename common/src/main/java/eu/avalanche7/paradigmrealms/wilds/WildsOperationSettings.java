package eu.avalanche7.paradigmrealms.wilds;

public record WildsOperationSettings(
        boolean rotateSeed,
        boolean shutdownWhenPrepared,
        int backupRetentionCount,
        boolean deleteOldBackupsAfterVerification) {
    public WildsOperationSettings {
        if (backupRetentionCount < 1 || backupRetentionCount > 100) {
            throw new IllegalArgumentException("backupRetentionCount must be between 1 and 100");
        }
    }
}
