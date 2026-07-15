package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.Objects;
import java.util.Optional;

public record RealmBanDtoV1(
        String playerUuid,
        String playerNameSnapshot,
        String actorUuid,
        Optional<String> reason,
        long createdAtEpochMs) {
    public RealmBanDtoV1 {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(playerNameSnapshot, "playerNameSnapshot");
        Objects.requireNonNull(actorUuid, "actorUuid");
        Objects.requireNonNull(reason, "reason");
    }
}
