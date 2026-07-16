package eu.avalanche7.paradigmrealms.backup;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public record BackupId(String value) implements Comparable<BackupId> {
    public BackupId {
        Objects.requireNonNull(value, "value");
        UUID.fromString(value);
        value = value.toLowerCase(java.util.Locale.ROOT);
    }

    public static BackupId generate() {
        return new BackupId(UUID.randomUUID().toString());
    }

    public static BackupId generate(Supplier<UUID> source) {
        return new BackupId(Objects.requireNonNull(source, "source").get().toString());
    }

    @Override public int compareTo(BackupId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
