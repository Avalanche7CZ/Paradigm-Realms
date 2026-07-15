package eu.avalanche7.paradigmrealms.domain;

import java.time.Instant;

public record CreationTimestamp(long epochMillis) {
    public CreationTimestamp {
        if (epochMillis < 0) {
            throw new IllegalArgumentException("creation timestamp cannot be negative");
        }
    }

    public static CreationTimestamp from(Instant instant) {
        return new CreationTimestamp(instant.toEpochMilli());
    }

    public Instant toInstant() {
        return Instant.ofEpochMilli(epochMillis);
    }
}
