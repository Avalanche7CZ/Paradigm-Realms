package eu.avalanche7.paradigmrealms.wilds;

import java.time.Duration;
import java.util.List;

public record WildsConfig(
        boolean enabled,
        WildsProfileId generationProfile,
        boolean rotateSeedOnReset,
        WildsEntryMode entryMode,
        int spawnProtectionRadius,
        WildsRtpConfig rtp,
        boolean scheduleEnabled,
        Duration scheduleInterval,
        List<Long> warningTimesSeconds,
        boolean shutdownWhenPrepared,
        int backupRetentionCount,
        boolean deleteOldBackupsAfterVerification) {
    public WildsConfig {
        if (spawnProtectionRadius < 0 || spawnProtectionRadius > 10_000) throw new IllegalArgumentException("invalid spawn protection radius");
        if (scheduleInterval.isZero() || scheduleInterval.isNegative()) throw new IllegalArgumentException("schedule interval must be positive");
        warningTimesSeconds = new WarningSchedule().normalize(warningTimesSeconds);
        if (backupRetentionCount < 1 || backupRetentionCount > 100) throw new IllegalArgumentException("invalid backup retention");
    }

    public WildsOperationSettings operationSettings() {
        return new WildsOperationSettings(rotateSeedOnReset, shutdownWhenPrepared,
                backupRetentionCount, deleteOldBackupsAfterVerification);
    }
}
