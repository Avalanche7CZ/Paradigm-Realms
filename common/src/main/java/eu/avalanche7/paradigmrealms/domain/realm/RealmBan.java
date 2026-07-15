package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;

public record RealmBan(
        UUID playerUuid,
        String playerNameSnapshot,
        UUID actorUuid,
        Optional<String> reason,
        CreationTimestamp createdAt) {
    public RealmBan {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(playerNameSnapshot, "playerNameSnapshot");
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        if (playerNameSnapshot.isBlank() || playerNameSnapshot.length() > 64) {
            throw new IllegalArgumentException("invalid banned player name snapshot");
        }
        reason = reason.map(RealmBan::validatedReason);
    }

    private static String validatedReason(String value) {
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > 240 || normalized.indexOf('\n') >= 0
                || normalized.indexOf('\r') >= 0 || hasControlCharacter(normalized)) {
            throw new IllegalArgumentException("invalid ban reason");
        }
        return normalized;
    }

    private static boolean hasControlCharacter(String value) {
        return value.codePoints().anyMatch(point -> Character.isISOControl(point));
    }
}
