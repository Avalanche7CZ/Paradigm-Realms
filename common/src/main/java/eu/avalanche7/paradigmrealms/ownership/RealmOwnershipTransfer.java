package eu.avalanche7.paradigmrealms.ownership;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.RealmId;

public record RealmOwnershipTransfer(
        UUID operationId,
        RealmId realmId,
        UUID currentOwner,
        UUID target,
        String currentOwnerName,
        String targetName,
        CreationTimestamp createdAt,
        CreationTimestamp expiresAt) {
    public RealmOwnershipTransfer {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(realmId, "realmId");
        Objects.requireNonNull(currentOwner, "currentOwner");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        currentOwnerName = validateName(currentOwnerName);
        targetName = validateName(targetName);
        if (currentOwner.equals(target)) throw new IllegalArgumentException("transfer target must differ from owner");
        if (expiresAt.epochMillis() <= createdAt.epochMillis()) {
            throw new IllegalArgumentException("transfer expiry must follow creation");
        }
    }

    public boolean expired(CreationTimestamp now) {
        return now.epochMillis() >= expiresAt.epochMillis();
    }

    private static String validateName(String value) {
        String normalized = Objects.requireNonNull(value, "name").strip();
        if (normalized.isEmpty() || normalized.length() > 64
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid player name snapshot");
        }
        return normalized;
    }
}
