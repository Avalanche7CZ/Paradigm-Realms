package eu.avalanche7.paradigmrealms.domain;

public record RealmId(long value) implements Comparable<RealmId> {
    public RealmId {
        if (value < 1) {
            throw new IllegalArgumentException("realm ID must be positive");
        }
    }

    @Override
    public int compareTo(RealmId other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
