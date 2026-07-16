package eu.avalanche7.paradigmrealms.backup;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

public record RealmBackupConfig(boolean enabled, Automatic automatic, Manual manual,
        Compression compression, PreOperation preOperation, Storage storage, Retention retention,
        String filenameTemplate, ZoneId filenameZone, int maximumQueueLength,
        int maximumPackagingJobs, Duration captureTimeout, int maximumRetries,
        Duration schedulingSpread) {

    public static final RealmBackupConfig DEFAULTS = new RealmBackupConfig(true,
            new Automatic(true, Duration.ofHours(12), Duration.ofMinutes(15), 1,
                    Duration.ofMinutes(60), true, false, false, true),
            new Manual(false, Duration.ofMinutes(120), 10), new Compression(6),
            new PreOperation(true, true, true, false, true, true, true),
            new Storage(5, 25), new Retention(5, 7, 4, 3, 20, 90, true),
            BackupFilenamePolicy.DEFAULT_TEMPLATE, ZoneId.of("Europe/Prague"), 64, 1,
            Duration.ofSeconds(45), 3, Duration.ofMinutes(5));

    public RealmBackupConfig {
        Objects.requireNonNull(automatic, "automatic");
        Objects.requireNonNull(manual, "manual");
        Objects.requireNonNull(compression, "compression");
        Objects.requireNonNull(preOperation, "preOperation");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(retention, "retention");
        Objects.requireNonNull(filenameTemplate, "filenameTemplate");
        Objects.requireNonNull(filenameZone, "filenameZone");
        range(maximumQueueLength, 1, 10_000, "maximumQueueLength");
        range(maximumPackagingJobs, 1, 16, "maximumPackagingJobs");
        positive(captureTimeout, "captureTimeout");
        range(maximumRetries, 0, 20, "maximumRetries");
        if (schedulingSpread.isNegative() || schedulingSpread.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("schedulingSpread must be between zero and 24 hours");
        }
        new BackupFilenamePolicy(filenameTemplate, filenameZone, 180);
    }

    public record Automatic(boolean enabled, Duration interval, Duration initialDelay,
            int maximumConcurrentBackups, Duration minimumBetweenPerRealm, boolean activeRealmsOnly,
            boolean includeArchivedRealms, boolean notifyOwners, boolean notifyAdministratorsOnFailure) {
        public Automatic {
            positive(interval, "automatic.interval");
            nonNegative(initialDelay, "automatic.initialDelay");
            range(maximumConcurrentBackups, 1, 16, "maximumConcurrentBackups");
            nonNegative(minimumBetweenPerRealm, "minimumBetweenPerRealm");
        }
    }

    public record Manual(boolean allowPlayerSelfBackup, Duration playerCooldown, int maximumQueuedPlayerRequests) {
        public Manual {
            nonNegative(playerCooldown, "playerCooldown");
            range(maximumQueuedPlayerRequests, 1, 1000, "maximumQueuedPlayerRequests");
        }
    }

    public record Compression(int level) {
        public Compression {
            range(level, 0, 9, "compression.level");
        }
    }

    public record PreOperation(boolean beforeReset, boolean beforeDelete, boolean beforeArchiveRestore,
            boolean beforeOwnershipTransfer, boolean requireSuccessfulBackupForReset,
            boolean requireSuccessfulBackupForDelete, boolean requireSuccessfulBackupForRestore) {}

    public record Storage(long minimumFreeSpaceGiB, long maximumTotalSizeGiB) {
        public Storage {
            if (minimumFreeSpaceGiB < 0 || maximumTotalSizeGiB < 1) {
                throw new IllegalArgumentException("invalid backup storage limits");
            }
        }
    }

    public record Retention(int keepLatestPerRealm, int keepDailyDays, int keepWeeklyWeeks,
            int keepMonthlyMonths, int maximumBackupsPerRealm, int maximumAgeDays,
            boolean pinnedBackupsNeverExpire) {
        public Retention {
            range(keepLatestPerRealm, 0, 1000, "keepLatestPerRealm");
            range(keepDailyDays, 0, 3650, "keepDailyDays");
            range(keepWeeklyWeeks, 0, 520, "keepWeeklyWeeks");
            range(keepMonthlyMonths, 0, 120, "keepMonthlyMonths");
            range(maximumBackupsPerRealm, 1, 10_000, "maximumBackupsPerRealm");
            range(maximumAgeDays, 1, 36500, "maximumAgeDays");
        }
    }

    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void nonNegative(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void range(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
