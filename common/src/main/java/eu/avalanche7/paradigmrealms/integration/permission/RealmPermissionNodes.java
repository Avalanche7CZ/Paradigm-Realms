package eu.avalanche7.paradigmrealms.integration.permission;

import java.util.List;

public final class RealmPermissionNodes {
    public static final RealmPermissionNode CREATE = node(
            "paradigm.realms.create", "Allows a player to create their realm.", 0, "realm.create");
    public static final RealmPermissionNode HOME = node(
            "paradigm.realms.home", "Allows a player to teleport to their realm.", 0, "realm.home");
    public static final RealmPermissionNode SET_SPAWN = node(
            "paradigm.realms.setspawn", "Allows a player to set their realm spawn.", 0, "realm.setspawn");
    public static final RealmPermissionNode INFO = node(
            "paradigm.realms.info", "Allows a player to inspect their realm.", 0, "realm.info");
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
    public static final RealmPermissionNode RESET = node(
            "paradigm.realms.reset", "Allows an owner to safely recreate their realm.", 0, "realm.reset");
    public static final RealmPermissionNode DELETE = node(
            "paradigm.realms.delete", "Allows an owner to archive their realm.", 0, "realm.delete");
    public static final RealmPermissionNode NAME = node(
            "paradigm.realms.name", "Allows an owner to name their realm.", 0, "realm.name");
    public static final RealmPermissionNode DESCRIPTION = node(
            "paradigm.realms.description", "Allows an owner to describe their realm.", 0, "realm.description");
    public static final RealmPermissionNode PUBLIC_LIST = node(
            "paradigm.realms.public.list", "Allows a player to browse public realms.", 0, "realm.public");
    public static final RealmPermissionNode LISTING = node(
            "paradigm.realms.listing", "Allows realm directory listing control.", 0, "realm.listing");
    public static final RealmPermissionNode KICK = node(
            "paradigm.realms.kick", "Allows realm occupant kicks.", 0, "realm.kick");
    public static final RealmPermissionNode BAN = node(
            "paradigm.realms.ban", "Allows realm ban management.", 0, "realm.ban");
    public static final RealmPermissionNode ROLE_MANAGE = node(
            "paradigm.realms.role.manage", "Allows an owner to manage realm roles.", 0, "realm.role");
    public static final RealmPermissionNode SETTINGS = node(
            "paradigm.realms.settings", "Allows focused realm setting management.", 0, "realm.settings");
    public static final RealmPermissionNode TRANSFER = node(
            "paradigm.realms.transfer", "Allows safe two-party realm ownership transfer.", 0, "realm.transfer");
    public static final RealmPermissionNode ADMIN_ARCHIVES = node(
            "paradigm.realms.admin.archives", "Allows archive inspection and restore.", 3, "admin.archives");
    public static final RealmPermissionNode ADMIN_OPERATIONS = node(
            "paradigm.realms.admin.operations", "Allows lifecycle operation inspection and retry.", 3, "admin.operations");
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
            "paradigm.realms.admin.inspect", "Allows read-only realm administration.", 3, "admin.inspect");
    public static final RealmPermissionNode ADMIN_VALIDATE = node(
            "paradigm.realms.admin.validate", "Allows explicit realm state validation.", 3, "admin.validate");
    public static final RealmPermissionNode ADMIN_REPAIR = node(
            "paradigm.realms.admin.repair", "Allows narrow deterministic repairs.", 4, "admin.repair");
    public static final RealmPermissionNode ADMIN_SUPPORT = node(
            "paradigm.realms.admin.support", "Allows safe support bundle export.", 4, "admin.support");
    public static final RealmPermissionNode ADMIN_CONFIG = node(
            "paradigm.realms.admin.config", "Allows configuration validation and reload.", 4, "admin.config");
    public static final RealmPermissionNode COMPATIBILITY = node(
            "paradigm.realms.admin.compatibility", "Allows development compatibility teleports.", 4,
            "admin.compatibility");

    public static final List<RealmPermissionNode> ALL = List.of(
            CREATE, HOME, SET_SPAWN, INFO,
            VISIT, INVITE, INVITES, ACCEPT, DECLINE, REMOVE, LEAVE, MEMBERS, ACCESS,
            PRESETS, PRESET_SELECT, RESET, DELETE, NAME, DESCRIPTION, PUBLIC_LIST, LISTING, KICK, BAN,
            ROLE_MANAGE, SETTINGS, TRANSFER, WILDS_ENTER, WILDS_RTP, WILDS_SPAWN, WILDS_INFO,
            ADMIN_INSPECT, ADMIN_VALIDATE, ADMIN_REPAIR, ADMIN_SUPPORT, ADMIN_CONFIG,
            ADMIN_PRESETS, ADMIN_BYPASS,
            ADMIN_ARCHIVES, ADMIN_OPERATIONS,
            ADMIN_WILDS_STATUS, ADMIN_WILDS_VALIDATE, ADMIN_WILDS_MANAGE,
            ADMIN_WILDS_RESET, ADMIN_WILDS_BACKUPS, COMPATIBILITY);

    private RealmPermissionNodes() {}

    private static RealmPermissionNode node(String node, String description, int fallback, String feature) {
        return new RealmPermissionNode(node, description, fallback, "Realms", feature);
    }
}
