package eu.avalanche7.paradigmrealms.backup;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BackupDeleteConfirmationService {
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(5);

    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Confirmation> confirmations = new HashMap<>();

    public BackupDeleteConfirmationService(Clock clock) {
        this(clock, new SecureRandom());
    }

    BackupDeleteConfirmationService(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized String issue(UUID actorUuid, BackupId backupId) {
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(backupId, "backupId");
        removeExpired();

        byte[] bytes = new byte[6];
        String token;
        do {
            random.nextBytes(bytes);
            token = HexFormat.of().formatHex(bytes);
        } while (confirmations.containsKey(token));

        confirmations.put(token, new Confirmation(
                actorUuid,
                backupId,
                clock.instant().plus(TOKEN_LIFETIME)));
        return token;
    }

    public synchronized Optional<BackupId> consume(UUID actorUuid, String token) {
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(token, "token");
        removeExpired();

        Confirmation confirmation = confirmations.remove(token);
        if (confirmation == null || !confirmation.actorUuid().equals(actorUuid)) {
            return Optional.empty();
        }
        return Optional.of(confirmation.backupId());
    }

    private void removeExpired() {
        Instant now = clock.instant();
        confirmations.values().removeIf(value -> !value.expiresAt().isAfter(now));
    }

    private record Confirmation(UUID actorUuid, BackupId backupId, Instant expiresAt) {}
}
