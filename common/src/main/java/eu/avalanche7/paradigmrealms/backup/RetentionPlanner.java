package eu.avalanche7.paradigmrealms.backup;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RetentionPlanner {
    public Plan plan(List<BackupCatalogEntry> entries, RealmBackupConfig.Retention policy,
            Instant now, Set<Long> activeRealms, long maximumTotalBytes, long usableSpaceBytes,
            long minimumFreeBytes) {
        List<BackupCatalogEntry> verified = entries.stream()
                .filter(entry -> entry.integrityStatus() == BackupIntegrityStatus.VERIFIED)
                .sorted(Comparator.comparing(BackupCatalogEntry::createdAt).reversed())
                .toList();

        Protection protection = new Protection();
        protectPinnedAndInUse(verified, protection);

        Map<Long, List<BackupCatalogEntry>> byRealm = groupByRealm(verified);
        for (Map.Entry<Long, List<BackupCatalogEntry>> group : byRealm.entrySet()) {
            protectRealmBuckets(group, policy, now, activeRealms, protection);
        }

        Instant maximumAge = now.minus(java.time.Duration.ofDays(policy.maximumAgeDays()));
        List<BackupCatalogEntry> candidates = ordinaryCandidates(
                verified,
                protection,
                byRealm,
                policy,
                maximumAge);

        long currentSize = entries.stream().mapToLong(BackupCatalogEntry::sizeBytes).sum();
        long pressure = requiredReclaim(
                currentSize,
                maximumTotalBytes,
                usableSpaceBytes,
                minimumFreeBytes);
        if (pressure > totalSize(candidates)) {
            addSizePressureCandidates(verified, protection, candidates);
        }

        ArrayList<BackupCatalogEntry> delete = new ArrayList<>();
        long reclaimed = 0;
        for (BackupCatalogEntry candidate : candidates) {
            boolean policyDelete = candidate.createdAt().isBefore(maximumAge)
                    || exceedsRealmCount(candidate, byRealm, policy.maximumBackupsPerRealm());
            if (policyDelete || reclaimed < pressure) {
                delete.add(candidate);
                reclaimed += candidate.sizeBytes();
            }
        }

        boolean limitsSatisfied = currentSize - reclaimed <= maximumTotalBytes
                && usableSpaceBytes + reclaimed >= minimumFreeBytes;
        return new Plan(
                List.copyOf(delete),
                reclaimed,
                Map.copyOf(protection.reasons),
                limitsSatisfied);
    }

    private static void protectPinnedAndInUse(
            List<BackupCatalogEntry> entries,
            Protection protection) {
        for (BackupCatalogEntry entry : entries) {
            if (entry.pinned()) {
                protection.add(entry, "pinned");
            }
            if (entry.restoreInUse()) {
                protection.add(entry, "restore in use");
            }
        }
    }

    private static Map<Long, List<BackupCatalogEntry>> groupByRealm(
            List<BackupCatalogEntry> entries) {
        Map<Long, List<BackupCatalogEntry>> result = new HashMap<>();
        for (BackupCatalogEntry entry : entries) {
            result.computeIfAbsent(entry.realmId(), ignored -> new ArrayList<>()).add(entry);
        }
        return result;
    }

    private static void protectRealmBuckets(
            Map.Entry<Long, List<BackupCatalogEntry>> group,
            RealmBackupConfig.Retention policy,
            Instant now,
            Set<Long> activeRealms,
            Protection protection) {
        List<BackupCatalogEntry> backups = group.getValue();
        int latestCount = Math.min(policy.keepLatestPerRealm(), backups.size());
        for (int index = 0; index < latestCount; index++) {
            protection.add(backups.get(index), "latest");
        }

        if (activeRealms.contains(group.getKey()) && !backups.isEmpty()) {
            protection.add(backups.get(0), "newest active realm backup");
        }

        protectBucket(backups, protection, now, policy.keepDailyDays(), Bucket.DAY);
        protectBucket(backups, protection, now, policy.keepWeeklyWeeks(), Bucket.WEEK);
        protectBucket(backups, protection, now, policy.keepMonthlyMonths(), Bucket.MONTH);
    }

    private static void protectBucket(
            List<BackupCatalogEntry> values,
            Protection protection,
            Instant now,
            int count,
            Bucket bucket) {
        if (count <= 0) {
            return;
        }

        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        Set<String> used = new HashSet<>();
        for (BackupCatalogEntry entry : values) {
            LocalDate date = entry.createdAt().atZone(ZoneOffset.UTC).toLocalDate();
            long age = bucket.age(date, today);
            String key = bucket.key(date);
            if (age >= 0 && age < count && used.add(key)) {
                protection.add(entry, bucket.description());
            }
        }
    }

    private static List<BackupCatalogEntry> ordinaryCandidates(
            List<BackupCatalogEntry> verified,
            Protection protection,
            Map<Long, List<BackupCatalogEntry>> byRealm,
            RealmBackupConfig.Retention policy,
            Instant maximumAge) {
        return verified.stream()
                .filter(entry -> !protection.contains(entry))
                .filter(entry -> entry.createdAt().isBefore(maximumAge)
                        || exceedsRealmCount(entry, byRealm, policy.maximumBackupsPerRealm()))
                .sorted(Comparator.comparing(BackupCatalogEntry::createdAt))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static void addSizePressureCandidates(
            List<BackupCatalogEntry> verified,
            Protection protection,
            List<BackupCatalogEntry> candidates) {
        verified.stream()
                .filter(entry -> !protection.contains(entry))
                .filter(entry -> !candidates.contains(entry))
                .sorted(Comparator.comparing(BackupCatalogEntry::createdAt))
                .forEach(candidates::add);
    }

    private static boolean exceedsRealmCount(
            BackupCatalogEntry entry,
            Map<Long, List<BackupCatalogEntry>> byRealm,
            int maximumCount) {
        return byRealm.get(entry.realmId()).indexOf(entry) >= maximumCount;
    }

    private static long requiredReclaim(
            long currentSize,
            long maximumTotalBytes,
            long usableSpaceBytes,
            long minimumFreeBytes) {
        long sizePressure = Math.max(0, currentSize - maximumTotalBytes);
        long freeSpacePressure = Math.max(0, minimumFreeBytes - usableSpaceBytes);
        return Math.max(sizePressure, freeSpacePressure);
    }

    private static long totalSize(List<BackupCatalogEntry> entries) {
        return entries.stream().mapToLong(BackupCatalogEntry::sizeBytes).sum();
    }

    private static final class Protection {
        private final Set<BackupId> ids = new HashSet<>();
        private final Map<BackupId, String> reasons = new HashMap<>();

        void add(BackupCatalogEntry entry, String reason) {
            ids.add(entry.backupId());
            reasons.merge(entry.backupId(), reason, (first, second) -> first + ", " + second);
        }

        boolean contains(BackupCatalogEntry entry) {
            return ids.contains(entry.backupId());
        }
    }

    private enum Bucket {
        DAY("daily retention") {
            @Override long age(LocalDate date, LocalDate today) {
                return java.time.temporal.ChronoUnit.DAYS.between(date, today);
            }

            @Override String key(LocalDate date) {
                return date.toString();
            }
        },
        WEEK("weekly retention") {
            @Override long age(LocalDate date, LocalDate today) {
                return java.time.temporal.ChronoUnit.WEEKS.between(
                        weekStart(date),
                        weekStart(today));
            }

            @Override String key(LocalDate date) {
                return weekStart(date).toString();
            }
        },
        MONTH("monthly retention") {
            @Override long age(LocalDate date, LocalDate today) {
                return java.time.temporal.ChronoUnit.MONTHS.between(
                        java.time.YearMonth.from(date),
                        java.time.YearMonth.from(today));
            }

            @Override String key(LocalDate date) {
                return java.time.YearMonth.from(date).toString();
            }
        };

        private final String description;

        Bucket(String description) {
            this.description = description;
        }

        abstract long age(LocalDate date, LocalDate today);
        abstract String key(LocalDate date);

        String description() {
            return description;
        }

        static LocalDate weekStart(LocalDate date) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
    }

    public record Plan(List<BackupCatalogEntry> deletions, long reclaimableBytes,
            Map<BackupId, String> retentionReasons, boolean storageLimitsSatisfied) {}
}
