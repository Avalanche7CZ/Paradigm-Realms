package eu.avalanche7.paradigmrealms.config;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.domain.realm.RealmSetting;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSettings;

public record RealmSettingsPolicy(Map<RealmSetting, RealmSettingPolicy> settings) {
    public RealmSettingsPolicy {
        Objects.requireNonNull(settings, "settings");
        EnumMap<RealmSetting, RealmSettingPolicy> copy = new EnumMap<>(RealmSetting.class);
        copy.putAll(settings);
        for (RealmSetting setting : RealmSetting.values()) {
            if (!copy.containsKey(setting)) throw new IllegalArgumentException("missing policy for " + setting);
        }
        settings = Map.copyOf(copy);
    }

    public static RealmSettingsPolicy secureDefaults() {
        EnumMap<RealmSetting, RealmSettingPolicy> values = new EnumMap<>(RealmSetting.class);
        values.put(RealmSetting.PVP, new RealmSettingPolicy(false, true, false, java.util.Optional.empty()));
        values.put(RealmSetting.EXPLOSIONS, new RealmSettingPolicy(false, false, false, java.util.Optional.of(false)));
        values.put(RealmSetting.MOB_GRIEFING, new RealmSettingPolicy(false, true, false, java.util.Optional.empty()));
        values.put(RealmSetting.VISITOR_INTERACTION,
                new RealmSettingPolicy(false, true, true, java.util.Optional.empty()));
        values.put(RealmSetting.VISITOR_CONTAINERS,
                new RealmSettingPolicy(false, true, false, java.util.Optional.empty()));
        return new RealmSettingsPolicy(values);
    }

    public RealmSettings defaults() {
        return new RealmSettings(
                settings.get(RealmSetting.PVP).defaultValue(),
                settings.get(RealmSetting.EXPLOSIONS).defaultValue(),
                settings.get(RealmSetting.MOB_GRIEFING).defaultValue(),
                settings.get(RealmSetting.VISITOR_INTERACTION).defaultValue(),
                settings.get(RealmSetting.VISITOR_CONTAINERS).defaultValue());
    }

    public RealmSettings effective(RealmSettings stored) {
        Objects.requireNonNull(stored, "stored");
        RealmSettings effective = stored;
        for (RealmSetting setting : RealmSetting.values()) {
            RealmSettingPolicy policy = settings.get(setting);
            if (policy.forcedValue().isPresent()) {
                effective = effective.with(setting, policy.forcedValue().orElseThrow());
            }
        }
        return effective;
    }
}
