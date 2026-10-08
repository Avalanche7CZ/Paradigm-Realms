package eu.avalanche7.paradigmrealms.modules.command;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandArgument;
import eu.avalanche7.paradigmrealms.platform.command.CommandBuilder;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsActionResult;
import eu.avalanche7.paradigmrealms.wilds.WildsDurationParser;
import eu.avalanche7.paradigmrealms.wilds.WildsLifecycleState;

public final class WildsCommandModule {
    private static final WildsDurationParser DURATIONS = new WildsDurationParser();

    private WildsCommandModule() {}

    public static void register(
            RealmsPlatformAdapter platform,
            Supplier<? extends WildsCommandRuntime> runtime) {
        register(platform.commands(), runtime, platform.permissions(), platform.messages(), Clock.systemUTC());
    }

    static void register(
            CommandPlatform commands,
            Supplier<? extends WildsCommandRuntime> runtime,
            CommandPermissionGate permissions,
            CommandMessageService messages,
            Clock clock) {
        CommandBuilder playerRoot = commands.literal("wilds")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.WILDS_ENTER))
                .executes(context -> playerAction(context.source(), runtime, PlayerAction.ENTER))
                .then(commands.literal("rtp")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.WILDS_RTP))
                        .executes(context -> playerAction(context.source(), runtime, PlayerAction.RTP)))
                .then(commands.literal("spawn")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.WILDS_SPAWN))
                        .executes(context -> playerAction(context.source(), runtime, PlayerAction.SPAWN)))
                .then(commands.literal("info")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.WILDS_INFO))
                        .executes(context -> info(context.source(), runtime, messages)));
        commands.register(playerRoot);

        CommandBuilder adminWilds = commands.literal("wilds")
                .then(commands.literal("status")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_STATUS))
                        .executes(context -> adminStatus(context.source(), runtime)))
                .then(commands.literal("validate")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_VALIDATE))
                        .executes(context -> validate(context.source(), runtime)))
                .then(commands.literal("open")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_MANAGE))
                        .executes(context -> invoke(context.source(), runtime, WildsCommandRuntime::openWildsEntry)))
                .then(commands.literal("close")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_MANAGE))
                        .executes(context -> invoke(context.source(), runtime, WildsCommandRuntime::closeWildsEntry)))
                .then(commands.literal("setspawn")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_MANAGE))
                        .executes(context -> setSpawn(context.source(), runtime)))
                .then(commands.literal("spawn").then(commands.literal("validate")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_VALIDATE))
                        .executes(context -> validateSpawn(context.source(), runtime))))
                .then(commands.literal("terrain").then(commands.literal("sample")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_VALIDATE))
                        .then(commands.argument("x", CommandArgument.integer(-29_000_000, 29_000_000))
                                .then(commands.argument("z", CommandArgument.integer(-29_000_000, 29_000_000))
                                        .executes(context -> terrainSample(
                                                context.source(), runtime,
                                                context.integer("x"), context.integer("z")))))))
                .then(resetBranch(commands, runtime, permissions, clock))
                .then(commands.literal("backups")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_BACKUPS))
                        .then(commands.literal("list")
                                .executes(context -> backups(context.source(), runtime)))
                        .then(commands.literal("prune")
                                .executes(context -> prune(context.source(), runtime))));
        commands.register(commands.literal("realms")
                .then(commands.literal("admin")
                        .requires(source -> RealmAdminCommandAccess.allowedAny(source, permissions))
                        .then(adminWilds)));
    }

    private static CommandBuilder resetBranch(
            CommandPlatform commands,
            Supplier<? extends WildsCommandRuntime> runtime,
            CommandPermissionGate permissions,
            Clock clock) {
        return commands.literal("reset")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_WILDS_RESET))
                .then(commands.literal("schedule")
                        .then(commands.argument("duration", CommandArgument.word())
                                .executes(context -> schedule(
                                        context.source(), runtime, context.string("duration"), clock))))
                .then(commands.literal("now")
                        .executes(context -> schedule(context.source(), runtime, "1s", clock))
                        .then(commands.argument("countdown", CommandArgument.word())
                                .executes(context -> schedule(
                                        context.source(), runtime, context.string("countdown"), clock))))
                .then(commands.literal("cancel")
                        .executes(context -> invoke(context.source(), runtime, WildsCommandRuntime::cancelWildsReset)))
                .then(commands.literal("prepare")
                        .executes(context -> invoke(context.source(), runtime, WildsCommandRuntime::prepareWildsReset)))
                .then(commands.literal("resume")
                        .executes(context -> resume(context.source(), runtime)))
                .then(commands.literal("verify")
                        .executes(context -> invoke(
                                context.source(), runtime, WildsCommandRuntime::retryWildsVerification)))
                .then(commands.literal("recovery")
                        .executes(context -> recovery(context.source(), runtime)));
    }

    private static int playerAction(
            CommandSource source,
            Supplier<? extends WildsCommandRuntime> runtimeSupplier,
            PlayerAction action) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source, "errors.player_only");
        if (runtime == null || player == null) return 0;
        WildsActionResult value = switch (action) {
            case ENTER -> runtime.enterWilds(player.uuid());
            case RTP -> runtime.requestWildsRtp(player.uuid());
            case SPAWN -> runtime.teleportWildsSpawn(player.uuid());
        };
        return result(source, value);
    }

    private static int info(
            CommandSource source,
            Supplier<? extends WildsCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var state = runtime.wildsState();
        String profile = state.activeProfile().map(Object::toString).orElseGet(() ->
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none"));
        String next = state.nextScheduledReset().map(Object::toString).orElseGet(() ->
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.not_scheduled"));
        String cooldown = source.player()
                .map(player -> eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "common.seconds", Map.of("seconds", Long.toString(
                                runtime.wildsCooldownRemaining(player.uuid()).toSeconds()))))
                .orElseGet(() -> eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.not_available"));
        messages.sendLocalized(source, "commands.wilds.status",
                Map.of("state", runtime.wildsEnabled() ? state.lifecycle().name() : WildsLifecycleState.DISABLED.name(),
                        "entry", Boolean.toString(runtime.wildsEnabled() && state.lifecycle().entryOpen()),
                        "epoch", Long.toString(state.activeEpoch()), "profile", profile,
                        "next_reset", next, "cooldown", cooldown));
        return 1;
    }

    private static int adminStatus(
            CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var state = runtime.wildsState();
        source.sendFeedbackKey("commands.wilds.admin_status", Map.of(
                "state", runtime.wildsEnabled() ? state.lifecycle().name() : WildsLifecycleState.DISABLED.name(),
                "lifecycle", runtime.wildsEnabled() ? state.lifecycle().name() : WildsLifecycleState.DISABLED.name(),
                "entry", Boolean.toString(runtime.wildsEnabled() && state.lifecycle().entryOpen()),
                "verified", Boolean.toString(state.generationVerified()),
                "epoch", Long.toString(state.activeEpoch()),
                "seed", Long.toString(state.activeSeed()),
                "recovery", state.failure().map(failure -> failure.code()).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none")),
                "profile", state.activeProfile().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.not_set")),
                "next_reset", state.nextScheduledReset().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.not_scheduled")),
                "operation", state.operation().map(value -> value.operationId().toString()).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none"))));
        state.failure().ifPresent(failure -> source.sendErrorKey("commands.wilds.failure",
                Map.of("code", failure.code(), "detail", failure.detail())));
        return 1;
    }

    private static int validate(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var issues = runtime.wildsValidationIssues();
        if (issues.isEmpty()) {
            source.sendFeedbackKey("commands.wilds.valid");
            return 1;
        }
        issues.forEach(issue -> source.sendErrorKey("common.detail", Map.of("detail", issue)));
        return 0;
    }

    private static int setSpawn(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(
                source, "commands.wilds.errors.setspawn_player_required");
        return runtime == null || player == null ? 0 : result(source, runtime.setWildsSpawn(player.uuid()));
    }

    private static int terrainSample(
            CommandSource source,
            Supplier<? extends WildsCommandRuntime> runtimeSupplier,
            int x,
            int z) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            source.sendFeedbackKey("commands.wilds.terrain_sample", Map.of(
                    "x", Integer.toString(x), "z", Integer.toString(z),
                    "sample", runtime.wildsTerrainSample(x, z)));
            return 1;
        } catch (RuntimeException exception) {
            source.sendErrorKey("commands.wilds.errors.terrain_sample_failed",
                    Map.of("detail", String.valueOf(exception.getMessage())));
            return 0;
        }
    }

    private static int validateSpawn(
            CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var issues = runtime.wildsValidationIssues().stream()
                .filter(value -> value.toLowerCase(java.util.Locale.ROOT).contains("spawn")).toList();
        if (issues.isEmpty()) {
            source.sendFeedbackKey("commands.wilds.spawn_valid");
            return 1;
        }
        issues.forEach(issue -> source.sendErrorKey("common.detail", Map.of("detail", issue)));
        return 0;
    }

    private static int schedule(
            CommandSource source,
            Supplier<? extends WildsCommandRuntime> runtimeSupplier,
            String durationText,
            Clock clock) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            runtime.reloadWildsConfig();
            Duration duration = DURATIONS.parse(durationText);
            return result(source, runtime.scheduleWildsReset(clock.instant().plus(duration)));
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.wilds.errors.invalid_duration",
                    Map.of("detail", String.valueOf(exception.getMessage())));
            return 0;
        }
    }

    private static int resume(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        WildsLifecycleState state = runtime.wildsState().lifecycle();
        WildsActionResult value = switch (state) {
            case ENTRY_BLOCKED, EVACUATING, RESET_SCHEDULED -> runtime.prepareWildsReset();
            case FAILED, VERIFYING, OFFLINE_RESET_PENDING -> runtime.retryWildsVerification();
            default -> WildsActionResult.INVALID_STATE;
        };
        return result(source, value);
    }

    private static int recovery(
            CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        WildsLifecycleState state = runtime.wildsState().lifecycle();
        source.sendFeedbackKey("commands.wilds.recovery_state", Map.of("state", state.name()));
        switch (state) {
            case RESET_SCHEDULED -> source.sendFeedbackKey("commands.wilds.recovery.scheduled");
            case ENTRY_BLOCKED, EVACUATING -> source.sendFeedbackKey("commands.wilds.recovery.resume");
            case SAVE_BARRIER, OFFLINE_RESET_PENDING ->
                    source.sendFeedbackKey("commands.wilds.recovery.offline_tool");
            case VERIFYING, FAILED -> source.sendFeedbackKey("commands.wilds.recovery.verify");
            default -> source.sendFeedbackKey("commands.wilds.recovery.none");
        }
        return 1;
    }

    private static int backups(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var backups = runtime.wildsBackups();
        source.sendFeedbackKey("commands.wilds.backups_title", Map.of("count", Integer.toString(backups.size())));
        backups.forEach(backup -> source.sendFeedbackKey("common.detail", Map.of("detail", backup)));
        return 1;
    }

    private static int prune(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            int count = runtime.pruneWildsBackups();
            source.sendFeedbackKey("commands.wilds.backups_pruned", Map.of("count", Integer.toString(count)));
            return 1;
        } catch (IOException exception) {
            source.sendErrorKey("commands.wilds.errors.prune_refused",
                    Map.of("detail", String.valueOf(exception.getMessage())));
            return 0;
        }
    }

    private static int invoke(
            CommandSource source,
            Supplier<? extends WildsCommandRuntime> runtimeSupplier,
            WildsOperation operation) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        return runtime == null ? 0 : result(source, operation.apply(runtime));
    }

    private static int result(CommandSource source, WildsActionResult result) {
        if (result == WildsActionResult.SUCCESS) {
            source.sendFeedbackKey("commands.wilds.operation_accepted", Map.of("operation", result.name()));
            return 1;
        }
        source.sendErrorKey("commands.wilds.errors.operation_refused",
                Map.of("result", result.name(), "operation", result.name()));
        return 0;
    }

    private static boolean allowed(
            CommandSource source, CommandPermissionGate permissions, RealmPermissionNode permission) {
        return permissions.allowed(source, permission);
    }

    private static PlayerReference requirePlayer(CommandSource source, String errorKey) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) source.sendErrorKey(errorKey);
        return player;
    }

    private static WildsCommandRuntime requireRuntime(
            CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendErrorKey("errors.startup_incomplete");
        return runtime;
    }

    private enum PlayerAction { ENTER, RTP, SPAWN }

    @FunctionalInterface
    private interface WildsOperation {
        WildsActionResult apply(WildsCommandRuntime runtime);
    }
}
