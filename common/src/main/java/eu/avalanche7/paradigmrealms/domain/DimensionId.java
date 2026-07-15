package eu.avalanche7.paradigmrealms.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record DimensionId(String namespace, String path) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9/._-]+");

    public static final DimensionId REALMS = new DimensionId("paradigm_realms", "realms");
    public static final DimensionId WILDS = new DimensionId("paradigm_realms", "wilds");
    public static final DimensionId OVERWORLD = new DimensionId("minecraft", "overworld");

    public DimensionId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid dimension namespace: " + namespace);
        }
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("invalid dimension path: " + path);
        }
    }

    public static DimensionId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException("dimension ID must be namespace:path");
        }
        return new DimensionId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }
}
