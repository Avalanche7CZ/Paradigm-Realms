package eu.avalanche7.paradigmrealms.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record RealmPresetId(String value) {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern PATH = Pattern.compile("[a-z0-9][a-z0-9/._-]{0,127}");

    public RealmPresetId {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        boolean namespaced = separator > 0 && separator == value.lastIndexOf(':')
                && NAMESPACE.matcher(value.substring(0, separator)).matches()
                && PATH.matcher(value.substring(separator + 1)).matches();

        boolean legacy = separator < 0 && PATH.matcher(value).matches();
        if (!namespaced && !legacy) {
            throw new IllegalArgumentException("invalid realm preset ID: " + value);
        }
    }

    public boolean isNamespaced() {
        return value.indexOf(':') > 0;
    }

    public String namespace() {
        requireNamespaced();
        return value.substring(0, value.indexOf(':'));
    }

    public String path() {
        return isNamespaced() ? value.substring(value.indexOf(':') + 1) : value;
    }

    public RealmPresetId requireNamespaced() {
        if (!isNamespaced()) {
            throw new IllegalArgumentException("new preset IDs must be namespaced: " + value);
        }
        return this;
    }

    @Override
    public String toString() {
        return value;
    }
}
