package eu.avalanche7.paradigmrealms.application;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.RealmId;

public record RealmDirectoryEntry(
        RealmId realmId,
        UUID ownerUuid,
        String displayName,
        String description) {
    public RealmDirectoryEntry {
        Objects.requireNonNull(realmId, "realmId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
    }
}
