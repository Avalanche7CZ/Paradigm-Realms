package eu.avalanche7.paradigmrealms.access;

import java.util.Objects;

public record AccessDecision(boolean allowed, AccessRole role, AccessDecisionReason reason) {
    public AccessDecision {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(reason, "reason");
    }
}
