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
        PlayerReference player = requirePlayer(source, "This command can only be used by a player.");
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
        String profile = state.activeProfile().map(Object::toString).orElse("none");
        String next = state.nextScheduledReset().map(Object::toString).orElse("not scheduled");
        String cooldown = source.player()
                .map(player -> runtime.wildsCooldownRemaining(player.uuid()).toSeconds() + "s")
                .orElse("n/a");
        messages.send(source,
                "<color:aqua>Wilds</color>: {state}, epoch {epoch}, profile {profile}",
                Map.of("state", state.lifecycle().name(), "epoch", Long.toString(state.activeEpoch()),
                        "profile", profile),
                "Wilds: " + state.lifecycle() + ", entry=" + state.lifecycle().entryOpen()
                        + ", epoch=" + state.activeEpoch() + ", profile=" + profile
                        + ", nextReset=" + next + ", rtpCooldown=" + cooldown);
        return 1;
    }

    private static int adminStatus(
            CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var state = runtime.wildsState();
        source.sendFeedback("Wilds state=" + state.lifecycle() + " entryOpen="
                + state.lifecycle().entryOpen() + " verified=" + state.generationVerified()
                + " epoch=" + state.activeEpoch()
                + " profile=" + state.activeProfile().map(Object::toString).orElse("not set")
                + " nextReset=" + state.nextScheduledReset().map(Object::toString).orElse("not scheduled")
                + " operation=" + state.operation().map(value -> value.operationId().toString()).orElse("none"));
        state.failure().ifPresent(failure -> source.sendError(
                "Failure " + failure.code() + ": " + failure.detail()));
        return 1;
    }

    private static int validate(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var issues = runtime.wildsValidationIssues();
        if (issues.isEmpty()) {
            source.sendFeedback("Wilds generation, seed, spawn and state are valid.");
            return 1;
        }
        issues.forEach(source::sendError);
        return 0;
    }

    private static int setSpawn(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(
                source, "Setspawn requires an administrator player inside Wilds.");
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
            source.sendFeedback("Wilds terrain sample at " + x + "," + z + ": "
                    + runtime.wildsTerrainSample(x, z));
            return 1;
        } catch (RuntimeException exception) {
            source.sendError("Terrain sample failed: " + exception.getMessage());
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
            source.sendFeedback("Wilds spawn is safe and current.");
            return 1;
        }
        issues.forEach(source::sendError);
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
            source.sendError("Invalid duration: " + exception.getMessage());
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
        source.sendFeedback("Recovery state: " + state);
        switch (state) {
            case RESET_SCHEDULED -> source.sendFeedback("Available: reset cancel, reset prepare");
            case ENTRY_BLOCKED, EVACUATING -> source.sendFeedback("Available: reset resume");
            case SAVE_BARRIER, OFFLINE_RESET_PENDING -> source.sendFeedback(
                    "Stop the server and run the offline Wilds reset tool; entry remains closed.");
            case VERIFYING, FAILED -> source.sendFeedback(
                    "Available: reset verify. Offline restore requires the documented reset tool procedure.");
            default -> source.sendFeedback("No incomplete reset requires recovery.");
        }
        return 1;
    }

    private static int backups(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var backups = runtime.wildsBackups();
        source.sendFeedback("Wilds quarantine backups: " + backups.size());
        backups.forEach(source::sendFeedback);
        return 1;
    }

    private static int prune(CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            int count = runtime.pruneWildsBackups();
            source.sendFeedback("Pruned " + count + " eligible Wilds backup(s).");
            return 1;
        } catch (IOException exception) {
            source.sendError("Backup pruning refused: " + exception.getMessage());
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
            source.sendFeedback("Wilds operation accepted.");
            return 1;
        }
        source.sendError("Wilds operation refused: " + result);
        return 0;
    }

    private static boolean allowed(
            CommandSource source, CommandPermissionGate permissions, RealmPermissionNode permission) {
        return permissions.allowed(source, permission);
    }

    private static PlayerReference requirePlayer(CommandSource source, String error) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) source.sendError(error);
        return player;
    }

    private static WildsCommandRuntime requireRuntime(
            CommandSource source, Supplier<? extends WildsCommandRuntime> runtimeSupplier) {
        WildsCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendError("Paradigm Realms has not completed startup.");
        return runtime;
    }

    private enum PlayerAction { ENTER, RTP, SPAWN }

    @FunctionalInterface
    private interface WildsOperation {
        WildsActionResult apply(WildsCommandRuntime runtime);
    }
}
