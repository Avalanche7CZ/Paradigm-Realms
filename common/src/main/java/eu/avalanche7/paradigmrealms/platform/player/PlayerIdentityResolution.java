package eu.avalanche7.paradigmrealms.platform.player;

import java.util.Objects;
import java.util.Optional;

public record PlayerIdentityResolution(Status status, Optional<PlayerIdentity> identity) {
    public PlayerIdentityResolution {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(identity, "identity");
        if ((status == Status.FOUND) != identity.isPresent()) {
            throw new IllegalArgumentException("only a found identity resolution may carry an identity");
        }
    }

    public static PlayerIdentityResolution found(PlayerIdentity identity) {
        return new PlayerIdentityResolution(Status.FOUND, Optional.of(identity));
    }

    public static PlayerIdentityResolution unknown() {
        return new PlayerIdentityResolution(Status.UNKNOWN, Optional.empty());
    }

    public static PlayerIdentityResolution ambiguous() {
        return new PlayerIdentityResolution(Status.AMBIGUOUS, Optional.empty());
    }

    public enum Status {
        FOUND,
        UNKNOWN,
        AMBIGUOUS
    }
}
