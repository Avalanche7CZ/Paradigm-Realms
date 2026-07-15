package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.Objects;

public record RealmInvitationDtoV1(
        int recordSchema,
        long realmId,
        String realmOwnerUuid,
        String invitedPlayerUuid,
        String ownerNameSnapshot,
        String invitedNameSnapshot,
        long createdAtEpochMs,
        long expiresAtEpochMs) {
    public RealmInvitationDtoV1 {
        Objects.requireNonNull(realmOwnerUuid, "realmOwnerUuid");
        Objects.requireNonNull(invitedPlayerUuid, "invitedPlayerUuid");
        Objects.requireNonNull(ownerNameSnapshot, "ownerNameSnapshot");
        Objects.requireNonNull(invitedNameSnapshot, "invitedNameSnapshot");
    }
}
