package eu.avalanche7.paradigmrealms.membership;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;

public record RealmInvitation(
        RealmId realmId,
        UUID realmOwnerUuid,
        UUID invitedPlayerUuid,
        String ownerNameSnapshot,
        String invitedNameSnapshot,
        CreationTimestamp createdAt,
        CreationTimestamp expiresAt,
        SchemaVersion schemaVersion) {

    public RealmInvitation {
        Objects.requireNonNull(realmId, "realmId");
        Objects.requireNonNull(realmOwnerUuid, "realmOwnerUuid");
        Objects.requireNonNull(invitedPlayerUuid, "invitedPlayerUuid");
        ownerNameSnapshot = validateName(ownerNameSnapshot, "ownerNameSnapshot");
        invitedNameSnapshot = validateName(invitedNameSnapshot, "invitedNameSnapshot");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (realmOwnerUuid.equals(invitedPlayerUuid)) {
            throw new IllegalArgumentException("realm owner cannot invite themselves");
        }
        if (expiresAt.epochMillis() <= createdAt.epochMillis()) {
            throw new IllegalArgumentException("invitation expiry must follow creation time");
        }
    }

    public boolean expiredAt(long epochMillis) {
        return expiresAt.epochMillis() <= epochMillis;
    }

    private static String validateName(String value, String field) {
        Objects.requireNonNull(value, field);
        String stripped = value.strip();
        if (stripped.isEmpty() || stripped.length() > 64 || stripped.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must be 1-64 printable characters");
        }
        return stripped;
    }
}
