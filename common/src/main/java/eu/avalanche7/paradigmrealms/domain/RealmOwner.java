package eu.avalanche7.paradigmrealms.domain;

import java.util.Objects;
import java.util.UUID;

public record RealmOwner(UUID uuid) {
    public RealmOwner {
        Objects.requireNonNull(uuid, "uuid");
    }
}
