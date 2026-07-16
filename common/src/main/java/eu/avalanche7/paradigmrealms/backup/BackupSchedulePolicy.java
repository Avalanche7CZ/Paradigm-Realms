package eu.avalanche7.paradigmrealms.backup;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class BackupSchedulePolicy {
    public Instant nextDue(long realmId, Instant enabledAt, Optional<Instant> lastCompleted,
            RealmBackupConfig.Automatic automatic, Duration spread) {
        Instant base = lastCompleted.map(value -> value.plus(automatic.interval()))
                .orElseGet(() -> enabledAt.plus(automatic.initialDelay()));
        if (spread.isZero()) {
            return base;
        }
        long maximum = spread.toMillis();
        long mixed = mix(realmId ^ base.getEpochSecond());
        return base.plusMillis(Math.floorMod(mixed, maximum + 1));
    }

    public Decision decide(long realmId, Instant now, Instant enabledAt, Optional<Instant> lastCompleted,
            boolean eligible, boolean queuedOrRunning, RealmBackupConfig config) {
        if (!config.enabled() || !config.automatic().enabled()) {
            return new Decision(false, Optional.empty(), "automatic backups disabled");
        }
        Instant due = nextDue(realmId, enabledAt, lastCompleted, config.automatic(), config.schedulingSpread());
        if (!eligible) {
            return new Decision(false, Optional.of(due), "realm is not eligible");
        }
        if (queuedOrRunning) {
            return new Decision(false, Optional.of(due), "realm is already queued or running");
        }
        if (lastCompleted.isPresent()
                && lastCompleted.orElseThrow()
                        .plus(config.automatic().minimumBetweenPerRealm())
                        .isAfter(now)) {
            return new Decision(false, Optional.of(due), "minimum interval has not elapsed");
        }
        return new Decision(!due.isAfter(now), Optional.of(due), due.isAfter(now) ? "not due" : "due");
    }

    private static long mix(long value) {
        ByteBuffer bytes = ByteBuffer.allocate(Long.BYTES).putLong(value);
        bytes.flip();

        long result = bytes.getLong();
        result ^= result >>> 33;
        result *= 0xff51afd7ed558ccdl;
        result ^= result >>> 33;
        result *= 0xc4ceb9fe1a85ec53l;
        return result ^ result >>> 33;
    }

    public record Decision(boolean due, Optional<Instant> nextDue, String reason) {}
}
