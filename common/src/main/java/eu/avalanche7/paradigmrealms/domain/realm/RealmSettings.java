package eu.avalanche7.paradigmrealms.domain.realm;

public record RealmSettings(
        boolean pvp,
        boolean explosions,
        boolean mobGriefing,
        boolean visitorInteraction,
        boolean visitorContainers) {
    public static final RealmSettings SECURE_DEFAULTS = new RealmSettings(false, false, false, false, false);

    public boolean value(RealmSetting setting) {
        return switch (setting) {
            case PVP -> pvp;
            case EXPLOSIONS -> explosions;
            case MOB_GRIEFING -> mobGriefing;
            case VISITOR_INTERACTION -> visitorInteraction;
            case VISITOR_CONTAINERS -> visitorContainers;
        };
    }

    public RealmSettings with(RealmSetting setting, boolean value) {
        return switch (setting) {
            case PVP -> new RealmSettings(value, explosions, mobGriefing, visitorInteraction, visitorContainers);
            case EXPLOSIONS -> new RealmSettings(pvp, value, mobGriefing, visitorInteraction, visitorContainers);
            case MOB_GRIEFING -> new RealmSettings(pvp, explosions, value, visitorInteraction, visitorContainers);
            case VISITOR_INTERACTION -> new RealmSettings(pvp, explosions, mobGriefing, value, visitorContainers);
            case VISITOR_CONTAINERS -> new RealmSettings(pvp, explosions, mobGriefing, visitorInteraction, value);
        };
    }
}
