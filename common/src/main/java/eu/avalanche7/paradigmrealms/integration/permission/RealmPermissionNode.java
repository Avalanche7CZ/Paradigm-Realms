package eu.avalanche7.paradigmrealms.integration.permission;

import java.util.Objects;

public record RealmPermissionNode(
        String node,
        String description,
        int fallbackOpLevel,
        String category,
        String featureIdentifier) {
    public RealmPermissionNode {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(featureIdentifier, "featureIdentifier");
        if (fallbackOpLevel < 0 || fallbackOpLevel > 4) {
            throw new IllegalArgumentException("fallback OP level must be between 0 and 4");
        }
    }
}
