package eu.avalanche7.paradigmrealms.wilds;

import java.util.Objects;
import java.util.regex.Pattern;

public record WildsProfileId(String value) implements Comparable<WildsProfileId> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public WildsProfileId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches() || value.contains("..") || value.endsWith("/")) {
            throw new IllegalArgumentException("invalid Wilds profile ID: " + value);
        }
    }

    @Override public int compareTo(WildsProfileId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
