package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmMemberRole;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSetting;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandArgument;
import eu.avalanche7.paradigmrealms.platform.command.CommandBuilder;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.command.CommandText;
import eu.avalanche7.paradigmrealms.platform.player.PlayerDirectory;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentityResolution;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;

public final class RealmOwnerCommandModule {
    private RealmOwnerCommandModule() {}

    public static void register(
            RealmsPlatformAdapter platform, Supplier<? extends RealmOwnerCommandRuntime> runtime) {
        var commands = platform.commands();
        var permissions = platform.permissions();
        PlayerDirectory players = platform.players();
        CommandBuilder root = commands.literal("realm")
                .then(commands.literal("name")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.NAME))
                        .then(commands.argument("name", CommandArgument.greedyString())
                                .executes(context -> name(context.source(), runtime, context.string("name")))))
                .then(description(commands, runtime, permissions))
                .then(commands.literal("public")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.PUBLIC_LIST))
                        .executes(context -> directory(context.source(), runtime, players, 1))
                        .then(commands.argument("page", CommandArgument.integer(1, 1_000_000))
                                .executes(context -> directory(
                                        context.source(), runtime, players, context.integer("page")))))
                .then(toggle(commands, platform.permissions(), "listing", RealmPermissionNodes.LISTING,
                        (source, value) -> listing(source, runtime, value)))
                .then(commands.literal("kick")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.KICK))
                        .then(playerArgument(commands, players)
                                .executes(context -> kick(context.source(), runtime, players,
                                        context.string("player")))))
                .then(ban(commands, runtime, permissions, players))
                .then(commands.literal("unban")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.BAN))
                        .then(playerArgument(commands, players)
                                .executes(context -> unban(context.source(), runtime, players,
                                        context.string("player")))))
                .then(commands.literal("bans")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.BAN))
                        .executes(context -> bans(context.source(), runtime, 1))
                        .then(commands.argument("page", CommandArgument.integer(1, 1_000_000))
                                .executes(context -> bans(context.source(), runtime, context.integer("page")))))
                .then(role(commands, runtime, permissions, players))
                .then(commands.literal("managers")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.ROLE_MANAGE))
                        .executes(context -> managers(context.source(), runtime, players)))
                .then(commands.literal("who")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.MEMBERS))
                        .executes(context -> who(context.source(), runtime, players)))
                .then(transfer(commands, runtime, permissions, players))
                .then(commands.literal("settings")
                        .requires(source -> permissions.allowed(source, RealmPermissionNodes.SETTINGS))
                        .executes(context -> settings(context.source(), runtime)))
                .then(setting(commands, runtime, permissions))
                .then(commands.literal("visit")
                        .then(commands.literal("id")
                                .requires(source -> permissions.allowed(source, RealmPermissionNodes.VISIT))
                                .then(commands.argument("realmId", CommandArgument.longArgument(1, Long.MAX_VALUE))
                                        .executes(context -> visitId(context.source(), runtime,
                                                context.longValue("realmId"))))));
        commands.register(root);
    }

    private static CommandBuilder description(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands,
            Supplier<? extends RealmOwnerCommandRuntime> runtime,
            eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate permissions) {
        return commands.literal("description")
                .requires(source -> permissions.allowed(source, RealmPermissionNodes.DESCRIPTION))
                .executes(context -> showDescription(context.source(), runtime))
                .then(commands.literal("set")
                        .then(commands.argument("text", CommandArgument.greedyString())
                                .executes(context -> description(
                                        context.source(), runtime, context.string("text")))))
                .then(commands.literal("clear")
                        .executes(context -> description(context.source(), runtime, "")));
    }

    private static CommandBuilder ban(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands,
            Supplier<? extends RealmOwnerCommandRuntime> runtime,
            eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate permissions,
            PlayerDirectory players) {
        CommandBuilder player = playerArgument(commands, players)
                .executes(context -> ban(context.source(), runtime, players,
                        context.string("player"), Optional.empty()))
                .then(commands.argument("reason", CommandArgument.greedyString())
                        .executes(context -> ban(context.source(), runtime, players,
                                context.string("player"), Optional.of(context.string("reason")))));
        return commands.literal("ban")
                .requires(source -> permissions.allowed(source, RealmPermissionNodes.BAN))
                .then(player);
    }

    private static CommandBuilder role(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands,
            Supplier<? extends RealmOwnerCommandRuntime> runtime,
            eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate permissions,
            PlayerDirectory players) {
        return commands.literal("role")
                .requires(source -> permissions.allowed(source, RealmPermissionNodes.ROLE_MANAGE))
                .then(playerArgument(commands, players)
                        .then(commands.argument("role", CommandArgument.word())
                                .suggests((context, input) -> List.of("member", "manager"))
                                .executes(context -> role(context.source(), runtime, players,
                                        context.string("player"), context.string("role")))));
    }

    private static CommandBuilder setting(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands,
            Supplier<? extends RealmOwnerCommandRuntime> runtime,
            eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate permissions) {
        return commands.literal("setting")
                .requires(source -> permissions.allowed(source, RealmPermissionNodes.SETTINGS))
                .then(commands.argument("setting", CommandArgument.word())
                        .suggests((context, input) -> java.util.Arrays.stream(RealmSetting.values())
                                .map(RealmSetting::commandName).toList())
                        .then(commands.argument("value", CommandArgument.word())
                                .suggests((context, input) -> List.of("on", "off"))
                                .executes(context -> setting(context.source(), runtime,
                                        context.string("setting"), context.string("value")))));
    }

    private static CommandBuilder transfer(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands,
            Supplier<? extends RealmOwnerCommandRuntime> runtime,
            eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate permissions,
            PlayerDirectory players) {
        return commands.literal("transfer")
                .requires(source -> permissions.allowed(source, RealmPermissionNodes.TRANSFER))
                .then(commands.literal("accept").then(playerArgument(commands, players)
                        .executes(context -> transferRespond(
                                context.source(), runtime, players, context.string("player"), true))))
                .then(commands.literal("decline").then(playerArgument(commands, players)
                        .executes(context -> transferRespond(
                                context.source(), runtime, players, context.string("player"), false))))
                .then(commands.literal("cancel")
                        .executes(context -> transferCancel(context.source(), runtime)))
                .then(playerArgument(commands, players)
                        .executes(context -> transferOffer(
                                context.source(), runtime, players, context.string("player"))));
    }

    private static CommandBuilder toggle(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands,
            eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate permissions,
            String literal, RealmPermissionNode permission, ToggleHandler handler) {
        return commands.literal(literal)
                .requires(source -> permissions.allowed(source, permission))
                .then(commands.literal("on").executes(context -> handler.run(context.source(), true)))
                .then(commands.literal("off").executes(context -> handler.run(context.source(), false)));
    }

    private static CommandBuilder playerArgument(
            eu.avalanche7.paradigmrealms.platform.command.CommandPlatform commands, PlayerDirectory players) {
        return commands.argument("player", CommandArgument.word())
                .suggests((context, input) -> players.cachedNames(context.source()));
    }

    private static int name(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, String name) {
        try {
            return result(source, require(source, supplier).setRealmName(requirePlayer(source).uuid(), name),
                    "commands.owner.name_changed", Map.of());
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.owner.errors.invalid_name");
            return 0;
        }
    }

    private static int description(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, String description) {
        try {
            return result(source,
                    require(source, supplier).setRealmDescription(requirePlayer(source).uuid(), description),
                    description.isEmpty() ? "commands.owner.description_cleared"
                            : "commands.owner.description_changed", Map.of());
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.owner.errors.invalid_description");
            return 0;
        }
    }

    private static int showDescription(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendErrorKey("errors.owner_or_manager_required");
            return 0;
        }
        if (realm.description().isEmpty()) source.sendFeedbackKey("commands.owner.description_empty");
        else source.sendFeedback(realm.description());
        return 1;
    }

    private static int listing(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, boolean listed) {
        return result(source, require(source, supplier).setRealmListed(requirePlayer(source).uuid(), listed),
                listed ? "commands.owner.listing_enabled" : "commands.owner.listing_disabled", Map.of());
    }

    private static int directory(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, int page) {
        var result = require(source, supplier).publicRealms(page);
        if (!result.valid()) {
            source.sendErrorKey("commands.owner.errors.invalid_directory_page",
                    Map.of("pages", Integer.toString(result.pageCount())));
            return 0;
        }
        source.sendFeedbackKey("commands.owner.directory_title", Map.of(
                "page", Integer.toString(result.requestedPage()), "pages", Integer.toString(result.pageCount())));
        if (result.entries().isEmpty()) source.sendFeedbackKey("commands.owner.directory_empty");
        result.entries().forEach(entry -> {
            String owner = players.cached(source, entry.ownerUuid()).map(PlayerIdentity::name).orElseGet(() ->
                    eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.unknown_owner"));
            String description = entry.description().isEmpty() ? "" :
                    eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "commands.owner.directory_description", Map.of("description", entry.description()));
            source.sendFeedback(new CommandText(List.of(
                    CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "commands.owner.directory_name", Map.of("name", entry.displayName())), 0xF8FAFC, true),
                    CommandText.Part.styled(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "commands.owner.directory_owner", Map.of("owner", owner, "description", description)),
                            0x94A3B8, false),
                    CommandText.Part.styledInteractive(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            "common.visit_button"), 0x22D3EE, true, false,
                            CommandText.ClickAction.RUN_COMMAND, "/realm visit id " + entry.realmId().value(),
                            eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                    "commands.owner.visit_hover",
                                    Map.of("realm_id", Long.toString(entry.realmId().value())))))), false);
        });
        return 1;
    }

    private static int kick(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String name) {
        PlayerIdentity identity = identity(source, players, name);
        if (identity == null) return 0;
        var status = require(source, supplier).kickFromRealm(requirePlayer(source).uuid(), identity.uuid());
        if (status != RealmOwnerCommandRuntime.KickResult.KICKED) {
            source.sendErrorKey("commands.owner.errors.kick_failed", Map.of("status", status.name()));
            return 0;
        }
        source.sendFeedbackKey("commands.owner.kicked", Map.of("player", identity.name()));
        return 1;
    }

    private static int ban(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String name, Optional<String> reason) {
        PlayerIdentity identity = identity(source, players, name);
        if (identity == null) return 0;
        try {
            return result(source, require(source, supplier).banFromRealm(
                    requirePlayer(source).uuid(), identity.uuid(), identity.name(), reason),
                    "commands.owner.banned", Map.of("player", identity.name()));
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.owner.errors.invalid_ban_reason");
            return 0;
        }
    }

    private static int unban(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String name) {
        PlayerIdentity identity = identity(source, players, name);
        if (identity == null) return 0;
        return result(source, require(source, supplier).unbanFromRealm(
                requirePlayer(source).uuid(), identity.uuid()), "commands.owner.unbanned",
                Map.of("player", identity.name()));
    }

    private static int bans(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, int page) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendErrorKey("errors.owner_or_manager_required");
            return 0;
        }
        var bans = realm.bans().values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.playerNameSnapshot().toLowerCase(java.util.Locale.ROOT)))
                .toList();
        int pageSize = 8;
        int pages = Math.max(1, (bans.size() + pageSize - 1) / pageSize);
        if (page > pages) {
            source.sendErrorKey("commands.owner.errors.invalid_ban_page", Map.of("pages", Integer.toString(pages)));
            return 0;
        }
        source.sendFeedbackKey("commands.owner.bans_title",
                Map.of("page", Integer.toString(page), "pages", Integer.toString(pages)));
        bans.stream().skip((long) (page - 1) * pageSize).limit(pageSize).forEach(ban ->
                source.sendFeedbackKey(ban.reason().isPresent()
                                ? "commands.owner.ban_line_reason" : "commands.owner.ban_line",
                        Map.of("player", ban.playerNameSnapshot(),
                                "reason", ban.reason().orElse(""))));
        return 1;
    }

    private static int role(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String name, String roleName) {
        PlayerIdentity identity = identity(source, players, name);
        if (identity == null) return 0;
        RealmMemberRole role;
        try {
            role = RealmMemberRole.valueOf(roleName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.owner.errors.invalid_role");
            return 0;
        }
        return result(source, require(source, supplier).setRealmRole(
                requirePlayer(source).uuid(), identity.uuid(), role), "commands.owner.role_changed",
                Map.of("player", identity.name(), "role",
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                "roles." + role.name().toLowerCase(java.util.Locale.ROOT))));
    }

    private static int managers(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, PlayerDirectory players) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendErrorKey("errors.owner_or_manager_required");
            return 0;
        }
        source.sendFeedbackKey("commands.owner.managers_title");
        if (realm.managers().isEmpty()) source.sendFeedbackKey("common.list_none");
        realm.managers().forEach(uuid -> source.sendFeedbackKey("common.list_item",
                Map.of("item", players.cached(source, uuid).map(PlayerIdentity::name).orElse(uuid.toString()))));
        return 1;
    }

    private static int who(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, PlayerDirectory players) {
        PlayerReference actor = requirePlayer(source);
        List<RealmOwnerCommandRuntime.Occupant> occupants = require(source, supplier).realmOccupants(actor.uuid());
        source.sendFeedbackKey("commands.owner.occupants_title");
        if (occupants.isEmpty()) source.sendFeedbackKey("common.list_none");
        occupants.forEach(occupant -> source.sendFeedbackKey("commands.owner.occupant_line", Map.of(
                "player", players.cached(source, occupant.player()).map(PlayerIdentity::name)
                        .orElse(occupant.player().toString()),
                "role", eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "roles." + occupant.role().name().toLowerCase(java.util.Locale.ROOT)))));
        return 1;
    }

    private static int transferOffer(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String targetName) {
        PlayerReference owner = requirePlayer(source);
        PlayerIdentity target = identity(source, players, targetName);
        if (target == null) return 0;
        String ownerName = players.cached(source, owner.uuid()).map(PlayerIdentity::name)
                .orElse(owner.uuid().toString());
        var result = require(source, supplier).offerTransfer(
                owner.uuid(), ownerName, target.uuid(), target.name());
        if (result.status()
                != eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Status.OFFERED) {
            source.sendError(eu.avalanche7.paradigmrealms.message.PlayerMessageMappings
                    .transferFailure(result.status()));
            return 0;
        }
        source.sendFeedbackKey("commands.owner.transfer_offered", Map.of("player", target.name()));
        players.onlineSource(source, target.uuid()).ifPresent(targetSource ->
                targetSource.sendFeedbackKey("commands.owner.transfer_received", Map.of("owner", ownerName)));
        return 1;
    }

    private static int transferRespond(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String ownerName, boolean accept) {
        PlayerReference target = requirePlayer(source);
        PlayerIdentity owner = identity(source, players, ownerName);
        if (owner == null) return 0;
        var result = accept
                ? require(source, supplier).acceptTransfer(target.uuid(), owner.uuid())
                : require(source, supplier).declineTransfer(target.uuid(), owner.uuid());
        var expected = accept
                ? eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Status.COMPLETED
                : eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Status.DECLINED;
        if (result.status() != expected) {
            source.sendError(eu.avalanche7.paradigmrealms.message.PlayerMessageMappings
                    .transferFailure(result.status()));
            return 0;
        }
        source.sendFeedbackKey(accept ? "commands.owner.transfer_accepted" : "commands.owner.transfer_declined");
        return 1;
    }

    private static int transferCancel(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        var result = require(source, supplier).cancelTransfer(requirePlayer(source).uuid());
        if (result.status()
                != eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Status.CANCELLED) {
            source.sendErrorKey("commands.owner.errors.transfer_cancel_missing");
            return 0;
        }
        source.sendFeedbackKey("commands.owner.transfer_cancelled");
        return 1;
    }

    private static int settings(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendErrorKey("errors.owner_or_manager_required");
            return 0;
        }
        for (RealmSetting setting : RealmSetting.values()) {
            source.sendFeedbackKey("commands.owner.setting_line", Map.of(
                    "setting", setting.commandName(), "value",
                    eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                            realm.settings().value(setting) ? "common.on" : "common.off")));
        }
        return 1;
    }

    private static int setting(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            String settingName, String valueName) {
        RealmSetting setting;
        try {
            setting = RealmSetting.parse(settingName);
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.owner.errors.unknown_setting");
            return 0;
        }
        if (!valueName.equalsIgnoreCase("on") && !valueName.equalsIgnoreCase("off")) {
            source.sendErrorKey("commands.owner.errors.invalid_setting_value");
            return 0;
        }
        return result(source, require(source, supplier).setRealmSetting(
                requirePlayer(source).uuid(), setting, valueName.equalsIgnoreCase("on")),
                "commands.owner.setting_changed", Map.of("setting", setting.commandName()));
    }

    private static int visitId(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, long id) {
        RealmOwnerCommandRuntime runtime = require(source, supplier);
        PlayerReference player = requirePlayer(source);
        Realm realm = runtime.realmById(new RealmId(id)).orElse(null);
        if (realm == null) {
            source.sendErrorKey("errors.realm_id_unknown");
            return 0;
        }
        var decision = runtime.evaluateVisit(player.uuid(), realm.owner().uuid());
        if (!decision.allowed() || !decision.realm().map(value -> value.id().equals(realm.id())).orElse(false)) {
            source.sendErrorKey("commands.owner.errors.visit_denied");
            return 0;
        }
        TeleportResult teleport = runtime.visit(player.uuid(), realm);
        if (teleport != TeleportResult.SUCCESS) {
            source.sendError(eu.avalanche7.paradigmrealms.message.PlayerMessageMappings
                    .teleportFailure(teleport));
            return 0;
        }
        source.sendFeedbackKey("commands.owner.visiting", Map.of("realm_id", Long.toString(realm.id().value())));
        return 1;
    }

    private static int result(
            CommandSource source, RealmOwnerManagementService.Result result,
            String successKey, Map<String, String> values) {
        if (result.status() == RealmOwnerManagementService.Status.CHANGED) {
            source.sendFeedbackKey(successKey, values);
            return 1;
        }
        source.sendError(eu.avalanche7.paradigmrealms.message.PlayerMessageMappings
                .ownerMutationFailure(result.status()));
        return 0;
    }

    private static PlayerIdentity identity(CommandSource source, PlayerDirectory players, String name) {
        PlayerIdentityResolution resolution = players.resolveCached(source, name);
        if (resolution.status() == PlayerIdentityResolution.Status.AMBIGUOUS) {
            source.sendErrorKey("errors.player_name_ambiguous");
            return null;
        }
        if (resolution.status() == PlayerIdentityResolution.Status.UNKNOWN) {
            source.sendErrorKey("errors.player_not_cached");
            return null;
        }
        return resolution.identity().orElseThrow();
    }

    private static RealmOwnerCommandRuntime require(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        RealmOwnerCommandRuntime runtime = supplier.get();
        if (runtime == null) throw new IllegalStateException(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("errors.startup_incomplete"));
        return runtime;
    }

    private static PlayerReference requirePlayer(CommandSource source) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) throw new IllegalStateException(
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("errors.player_only"));
        return player;
    }

    @FunctionalInterface
    private interface ToggleHandler {
        int run(CommandSource source, boolean value);
    }
}
