package eu.avalanche7.paradigmrealms.integration.permission;

import java.util.List;

public final class RealmPermissionNodes {
    public static final RealmPermissionNode CREATE = node(
            "paradigmrealms.realm.create", "Allows a player to create their realm.", 0, "realm.create");
    public static final RealmPermissionNode HOME = node(
            "paradigmrealms.realm.home", "Allows a player to teleport to their realm.", 0, "realm.home");
    public static final RealmPermissionNode SET_SPAWN = node(
            "paradigmrealms.realm.setspawn", "Allows a player to set their realm spawn.", 0, "realm.setspawn");
    public static final RealmPermissionNode INFO = node(
            "paradigmrealms.realm.info", "Allows a player to inspect their realm.", 0, "realm.info");
    public static final RealmPermissionNode VISIT = node(
            "paradigm.realms.visit", "Allows a player to visit accessible realms.", 0, "realm.visit");
    public static final RealmPermissionNode INVITE = node(
            "paradigm.realms.invite", "Allows a realm owner to invite members.", 0, "realm.invite");
    public static final RealmPermissionNode INVITES = node(
            "paradigm.realms.invites", "Allows a player to inspect their invitations.", 0, "realm.invites");
    public static final RealmPermissionNode ACCEPT = node(
            "paradigm.realms.accept", "Allows a player to accept a realm invitation.", 0, "realm.accept");
    public static final RealmPermissionNode DECLINE = node(
            "paradigm.realms.decline", "Allows a player to decline a realm invitation.", 0, "realm.decline");
    public static final RealmPermissionNode REMOVE = node(
            "paradigm.realms.remove", "Allows a realm owner to remove members.", 0, "realm.remove");
    public static final RealmPermissionNode LEAVE = node(
            "paradigm.realms.leave", "Allows a member to leave a realm.", 0, "realm.leave");
    public static final RealmPermissionNode MEMBERS = node(
            "paradigm.realms.members", "Allows owner and members to inspect membership.", 0, "realm.members");
    public static final RealmPermissionNode ACCESS = node(
            "paradigm.realms.access", "Allows a realm owner to change visit access.", 0, "realm.access");
    public static final RealmPermissionNode PRESETS = node(
            "paradigm.realms.presets", "Allows a player to list selectable realm presets.", 0, "realm.presets");
    public static final RealmPermissionNode PRESET_SELECT = node(
            "paradigm.realms.preset.select", "Allows explicit realm preset selection.", 0,
            "realm.preset.select");
    public static final RealmPermissionNode ADMIN_PRESETS = node(
            "paradigm.realms.admin.presets", "Allows realm preset inspection and reload.", 2,
            "admin.presets");
    public static final RealmPermissionNode ADMIN_BYPASS = node(
            "paradigm.realms.admin.bypass", "Allows explicit session protection bypass.", 4, "admin.bypass");
    public static final RealmPermissionNode WILDS_ENTER = node(
            "paradigm.realms.wilds.enter", "Allows entry to the active Wilds generation.", 0, "wilds.enter");
    public static final RealmPermissionNode WILDS_RTP = node(
            "paradigm.realms.wilds.rtp", "Allows bounded random teleport in Wilds.", 0, "wilds.rtp");
    public static final RealmPermissionNode WILDS_SPAWN = node(
            "paradigm.realms.wilds.spawn", "Allows teleport to the Wilds spawn.", 0, "wilds.spawn");
    public static final RealmPermissionNode WILDS_INFO = node(
            "paradigm.realms.wilds.info", "Allows inspection of public Wilds status.", 0, "wilds.info");
    public static final RealmPermissionNode ADMIN_WILDS_STATUS = node(
            "paradigm.realms.admin.wilds.status", "Allows administrative Wilds status inspection.", 2, "admin.wilds.status");
    public static final RealmPermissionNode ADMIN_WILDS_VALIDATE = node(
            "paradigm.realms.admin.wilds.validate", "Allows Wilds generation validation.", 2, "admin.wilds.validate");
    public static final RealmPermissionNode ADMIN_WILDS_MANAGE = node(
            "paradigm.realms.admin.wilds.manage", "Allows Wilds entry and spawn management.", 3, "admin.wilds.manage");
    public static final RealmPermissionNode ADMIN_WILDS_RESET = node(
            "paradigm.realms.admin.wilds.reset", "Allows preparation of an offline Wilds reset.", 4, "admin.wilds.reset");
    public static final RealmPermissionNode ADMIN_WILDS_BACKUPS = node(
            "paradigm.realms.admin.wilds.backups", "Allows validated Wilds backup retention operations.", 4, "admin.wilds.backups");
    public static final RealmPermissionNode ADMIN_INSPECT = node(
            "paradigmrealms.admin.inspect", "Allows read-only realm administration.", 3, "admin.inspect");
    public static final RealmPermissionNode COMPATIBILITY = node(
            "paradigmrealms.admin.compatibility", "Allows development compatibility teleports.", 4,
            "admin.compatibility");

    public static final List<RealmPermissionNode> ALL = List.of(
            CREATE, HOME, SET_SPAWN, INFO,
            VISIT, INVITE, INVITES, ACCEPT, DECLINE, REMOVE, LEAVE, MEMBERS, ACCESS,
            PRESETS, PRESET_SELECT, WILDS_ENTER, WILDS_RTP, WILDS_SPAWN, WILDS_INFO,
            ADMIN_INSPECT, ADMIN_PRESETS, ADMIN_BYPASS,
            ADMIN_WILDS_STATUS, ADMIN_WILDS_VALIDATE, ADMIN_WILDS_MANAGE,
            ADMIN_WILDS_RESET, ADMIN_WILDS_BACKUPS, COMPATIBILITY);

    private RealmPermissionNodes() {}

    private static RealmPermissionNode node(String node, String description, int fallback, String feature) {
        return new RealmPermissionNode(node, description, fallback, "Realms", feature);
    }
}
