package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.Locale;

public enum RealmSetting {
    PVP("pvp"),
    EXPLOSIONS("explosions"),
    MOB_GRIEFING("mob-griefing"),
    VISITOR_INTERACTION("visitor-interaction"),
    VISITOR_CONTAINERS("visitor-containers");

    private final String commandName;

    RealmSetting(String commandName) {
        this.commandName = commandName;
    }

    public String commandName() {
        return commandName;
    }

    public static RealmSetting parse(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (RealmSetting setting : values()) {
            if (setting.commandName.equals(normalized)) return setting;
        }
        throw new IllegalArgumentException("unknown realm setting");
    }
}
