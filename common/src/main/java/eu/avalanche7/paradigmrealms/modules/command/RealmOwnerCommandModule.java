package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
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
                    "Realm name changed.");
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid realm name.");
            return 0;
        }
    }

    private static int description(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, String description) {
        try {
            return result(source,
                    require(source, supplier).setRealmDescription(requirePlayer(source).uuid(), description),
                    description.isEmpty() ? "Realm description cleared." : "Realm description changed.");
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid realm description.");
            return 0;
        }
    }

    private static int showDescription(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendError("You do not own or manage a realm.");
            return 0;
        }
        source.sendFeedback(realm.description().isEmpty() ? "Realm description is empty." : realm.description());
        return 1;
    }

    private static int listing(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, boolean listed) {
        return result(source, require(source, supplier).setRealmListed(requirePlayer(source).uuid(), listed),
                listed ? "Public directory listing enabled." : "Public directory listing disabled.");
    }

    private static int directory(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, int page) {
        var result = require(source, supplier).publicRealms(page);
        if (!result.valid()) {
            source.sendError("Invalid public realm directory page. Available pages: " + result.pageCount());
            return 0;
        }
        source.sendFeedback("Public realms — page " + result.requestedPage() + "/" + result.pageCount());
        if (result.entries().isEmpty()) source.sendFeedback("No realms are currently listed.");
        result.entries().forEach(entry -> {
            String owner = players.cached(source, entry.ownerUuid()).map(PlayerIdentity::name).orElse("Unknown owner");
            String description = entry.description().isEmpty() ? "" : " — " + entry.description();
            source.sendFeedback(new CommandText(List.of(
                    CommandText.Part.styled("◆ " + entry.displayName(), 0xF8FAFC, true),
                    CommandText.Part.styled(" by " + owner + description + " ", 0x94A3B8, false),
                    CommandText.Part.styledInteractive("[visit]", 0x22D3EE, true, false,
                            CommandText.ClickAction.RUN_COMMAND, "/realm visit id " + entry.realmId().value(),
                            "Visit realm #" + entry.realmId().value()))), false);
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
            source.sendError("Realm kick failed: " + status);
            return 0;
        }
        source.sendFeedback(identity.name() + " was kicked from the realm.");
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
                    identity.name() + " was banned from the realm.");
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid ban reason.");
            return 0;
        }
    }

    private static int unban(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier,
            PlayerDirectory players, String name) {
        PlayerIdentity identity = identity(source, players, name);
        if (identity == null) return 0;
        return result(source, require(source, supplier).unbanFromRealm(
                requirePlayer(source).uuid(), identity.uuid()), identity.name() + " was unbanned.");
    }

    private static int bans(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, int page) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendError("You do not own or manage a realm.");
            return 0;
        }
        var bans = realm.bans().values().stream()
                .sorted(java.util.Comparator.comparing(value -> value.playerNameSnapshot().toLowerCase(java.util.Locale.ROOT)))
                .toList();
        int pageSize = 8;
        int pages = Math.max(1, (bans.size() + pageSize - 1) / pageSize);
        if (page > pages) {
            source.sendError("Invalid ban page. Available pages: " + pages);
            return 0;
        }
        source.sendFeedback("Realm bans — page " + page + "/" + pages);
        bans.stream().skip((long) (page - 1) * pageSize).limit(pageSize).forEach(ban ->
                source.sendFeedback("- " + ban.playerNameSnapshot()
                        + ban.reason().map(reason -> " — " + reason).orElse("")));
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
            source.sendError("Role must be member or manager.");
            return 0;
        }
        return result(source, require(source, supplier).setRealmRole(
                requirePlayer(source).uuid(), identity.uuid(), role),
                identity.name() + " is now a " + role.name().toLowerCase(java.util.Locale.ROOT) + ".");
    }

    private static int managers(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, PlayerDirectory players) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendError("You do not own or manage a realm.");
            return 0;
        }
        source.sendFeedback("Realm managers:");
        if (realm.managers().isEmpty()) source.sendFeedback("- none");
        realm.managers().forEach(uuid -> source.sendFeedback("- "
                + players.cached(source, uuid).map(PlayerIdentity::name).orElse(uuid.toString())));
        return 1;
    }

    private static int who(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, PlayerDirectory players) {
        PlayerReference actor = requirePlayer(source);
        List<RealmOwnerCommandRuntime.Occupant> occupants = require(source, supplier).realmOccupants(actor.uuid());
        source.sendFeedback("Players in your realm:");
        if (occupants.isEmpty()) source.sendFeedback("- none");
        occupants.forEach(occupant -> source.sendFeedback("- "
                + players.cached(source, occupant.player()).map(PlayerIdentity::name)
                        .orElse(occupant.player().toString())
                + " [" + occupant.role().name().toLowerCase(java.util.Locale.ROOT) + "]"));
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
            source.sendError("Realm transfer offer failed: " + result.status());
            return 0;
        }
        source.sendFeedback("Ownership transfer offered to " + target.name() + ".");
        players.onlineSource(source, target.uuid()).ifPresent(targetSource ->
                targetSource.sendFeedback(ownerName + " offered you their realm. Use /realm transfer accept "
                        + ownerName));
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
            source.sendError("Realm transfer response failed: " + result.status());
            return 0;
        }
        source.sendFeedback(accept ? "You now own the realm." : "Realm transfer declined.");
        return 1;
    }

    private static int transferCancel(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        var result = require(source, supplier).cancelTransfer(requirePlayer(source).uuid());
        if (result.status()
                != eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Status.CANCELLED) {
            source.sendError("No pending ownership transfer could be cancelled.");
            return 0;
        }
        source.sendFeedback("Ownership transfer cancelled.");
        return 1;
    }

    private static int settings(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        Realm realm = require(source, supplier).managedRealm(requirePlayer(source).uuid()).orElse(null);
        if (realm == null) {
            source.sendError("You do not own or manage a realm.");
            return 0;
        }
        for (RealmSetting setting : RealmSetting.values()) {
            source.sendFeedback(setting.commandName() + ": " + (realm.settings().value(setting) ? "on" : "off"));
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
            source.sendError("Unknown realm setting.");
            return 0;
        }
        if (!valueName.equalsIgnoreCase("on") && !valueName.equalsIgnoreCase("off")) {
            source.sendError("Setting value must be on or off.");
            return 0;
        }
        return result(source, require(source, supplier).setRealmSetting(
                requirePlayer(source).uuid(), setting, valueName.equalsIgnoreCase("on")),
                "Realm setting " + setting.commandName() + " changed.");
    }

    private static int visitId(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier, long id) {
        RealmOwnerCommandRuntime runtime = require(source, supplier);
        PlayerReference player = requirePlayer(source);
        Realm realm = runtime.realmById(new RealmId(id)).orElse(null);
        if (realm == null) {
            source.sendError("Unknown realm ID.");
            return 0;
        }
        var decision = runtime.evaluateVisit(player.uuid(), realm.owner().uuid());
        if (!decision.allowed() || !decision.realm().map(value -> value.id().equals(realm.id())).orElse(false)) {
            source.sendError("You cannot visit that realm.");
            return 0;
        }
        TeleportResult teleport = runtime.visit(player.uuid(), realm);
        if (teleport != TeleportResult.SUCCESS) {
            source.sendError("Unable to visit realm: " + teleport);
            return 0;
        }
        source.sendFeedback("Visiting " + realm.displayName() + ".");
        return 1;
    }

    private static int result(
            CommandSource source, RealmOwnerManagementService.Result result, String success) {
        if (result.status() == RealmOwnerManagementService.Status.CHANGED) {
            source.sendFeedback(success);
            return 1;
        }
        if (result.status() == RealmOwnerManagementService.Status.SERVER_LOCKED) {
            source.sendError("That setting is locked by the server.");
            return 0;
        }
        source.sendError("Realm operation failed: " + result.status());
        return 0;
    }

    private static PlayerIdentity identity(CommandSource source, PlayerDirectory players, String name) {
        PlayerIdentityResolution resolution = players.resolveCached(source, name);
        if (resolution.status() == PlayerIdentityResolution.Status.AMBIGUOUS) {
            source.sendError("That cached player name is ambiguous.");
            return null;
        }
        if (resolution.status() == PlayerIdentityResolution.Status.UNKNOWN) {
            source.sendError("No cached player has that name.");
            return null;
        }
        return resolution.identity().orElseThrow();
    }

    private static RealmOwnerCommandRuntime require(
            CommandSource source, Supplier<? extends RealmOwnerCommandRuntime> supplier) {
        RealmOwnerCommandRuntime runtime = supplier.get();
        if (runtime == null) throw new IllegalStateException("Paradigm Realms has not completed startup.");
        return runtime;
    }

    private static PlayerReference requirePlayer(CommandSource source) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) throw new IllegalStateException("This command can only be used by a player.");
        return player;
    }

    @FunctionalInterface
    private interface ToggleHandler {
        int run(CommandSource source, boolean value);
    }
}
