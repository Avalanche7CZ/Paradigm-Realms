package eu.avalanche7.paradigmrealms.config;

import java.util.Objects;
import java.util.Optional;

public record RealmSettingPolicy(
        boolean defaultValue,
        boolean ownerMutable,
        boolean managerMutable,
        Optional<Boolean> forcedValue) {
    public RealmSettingPolicy {
        Objects.requireNonNull(forcedValue, "forcedValue");
    }

    public boolean effective(boolean stored) {
        return forcedValue.orElse(stored);
    }
}
