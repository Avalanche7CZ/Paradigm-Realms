package eu.avalanche7.paradigmrealms.integration.permission;

import java.util.Objects;
import java.util.UUID;

public record PlayerReference(UUID uuid, String name, int vanillaPermissionLevel) {
    public PlayerReference {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("player name cannot be blank");
        }
        if (vanillaPermissionLevel < 0 || vanillaPermissionLevel > 4) {
            throw new IllegalArgumentException("vanilla permission level must be between 0 and 4");
        }
    }
}
