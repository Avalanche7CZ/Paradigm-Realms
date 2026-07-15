package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.Objects;

public record WildsFailure(String code, String detail, Instant occurredAt) {
    public WildsFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!code.matches("[A-Z0-9_]{1,64}")) throw new IllegalArgumentException("invalid failure code");
        if (detail.length() > 512) detail = detail.substring(0, 512);
    }
}
