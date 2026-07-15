package eu.avalanche7.paradigmrealms.domain;

public record SchemaVersion(int value) implements Comparable<SchemaVersion> {
    public static final SchemaVersion V1 = new SchemaVersion(1);
    public static final SchemaVersion CURRENT = V1;

    public SchemaVersion {
        if (value < 1) {
            throw new IllegalArgumentException("schema version must be positive");
        }
    }

    @Override
    public int compareTo(SchemaVersion other) {
        return Integer.compare(value, other.value);
    }
}
