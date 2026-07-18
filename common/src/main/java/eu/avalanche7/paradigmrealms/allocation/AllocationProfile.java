package eu.avalanche7.paradigmrealms.allocation;

import java.util.Objects;

public record AllocationProfile(String value) {
    public static final AllocationProfile REGION_ALIGNED_32_V1 =
            new AllocationProfile("region-aligned-32-v1");
    public static final AllocationProfile CUSTOM_V1 =
            new AllocationProfile("custom-v1");

    public AllocationProfile {
        Objects.requireNonNull(value, "value");
        if (!value.matches("[a-z0-9][a-z0-9-]{0,63}")) {
            throw new IllegalArgumentException("invalid allocation profile " + value);
        }
    }

    public boolean regionAligned32() {
        return equals(REGION_ALIGNED_32_V1);
    }

    @Override
    public String toString() {
        return value;
    }
}
