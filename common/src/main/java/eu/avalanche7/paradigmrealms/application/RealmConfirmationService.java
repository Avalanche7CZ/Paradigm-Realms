package eu.avalanche7.paradigmrealms.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.RealmId;

public final class RealmConfirmationService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final long EXPIRY_MILLIS = 120_000L;
    private static final int MAX_PENDING = 4_096;
    private final Clock clock;
    private final SecureRandom random;
    private final Map<String, Confirmation> pending = new HashMap<>();

    public RealmConfirmationService(Clock clock) {
        this(clock, new SecureRandom());
    }

    RealmConfirmationService(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public synchronized String issue(UUID player, RealmId realm, Kind kind, Optional<String> preset) {
        expire();
        pending.entrySet().removeIf(entry -> entry.getValue().player().equals(player)
                && entry.getValue().realm().equals(realm) && entry.getValue().kind() == kind);
        if (pending.size() >= MAX_PENDING) {
            String oldest = pending.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
                    .map(Map.Entry::getKey).orElseThrow();
            pending.remove(oldest);
        }
        String token;
        do {
            char[] value = new char[10];
            for (int index = 0; index < value.length; index++) value[index] = ALPHABET[random.nextInt(ALPHABET.length)];
            token = new String(value);
        } while (pending.containsKey(token));
        pending.put(token, new Confirmation(player, realm, kind, preset, Math.addExact(clock.millis(), EXPIRY_MILLIS)));
        return token;
    }

    public synchronized Optional<Confirmation> consume(UUID player, RealmId realm, Kind kind, String token) {
        expire();
        Confirmation confirmation = pending.get(token);
        if (confirmation == null || !confirmation.player().equals(player) || !confirmation.realm().equals(realm)
                || confirmation.kind() != kind) return Optional.empty();
        pending.remove(token);
        return Optional.of(confirmation);
    }

    public synchronized void cancel(UUID player, RealmId realm, Kind kind) {
        pending.entrySet().removeIf(entry -> entry.getValue().player().equals(player)
                && entry.getValue().realm().equals(realm) && entry.getValue().kind() == kind);
    }

    private void expire() {
        long now = clock.millis();
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    public synchronized int pendingCount() {
        expire();
        return pending.size();
    }

    public enum Kind { RESET, DELETE }
    public record Confirmation(UUID player, RealmId realm, Kind kind, Optional<String> preset, long expiresAtMillis) {}
}
