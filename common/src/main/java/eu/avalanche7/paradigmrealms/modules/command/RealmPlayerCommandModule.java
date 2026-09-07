package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionResult;
import eu.avalanche7.paradigmrealms.generation.RealmAlreadyExistsException;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.persistence.ReadOnlyStoreException;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandArgument;
import eu.avalanche7.paradigmrealms.platform.command.CommandBuilder;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.teleport.SetSpawnResult;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;

public final class RealmPlayerCommandModule {
    private RealmPlayerCommandModule() {}

    public static void register(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmPlayerCommandRuntime> runtime) {
        register(platform.commands(), runtime, platform.permissions(), platform.messages());
    }

    static void register(
            CommandPlatform commands,
            Supplier<? extends RealmPlayerCommandRuntime> runtime,
            CommandPermissionGate permissions,
            CommandMessageService messages) {
        CommandBuilder create = commands.literal("create")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.CREATE))
                .executes(context -> create(context.source(), runtime, messages, Optional.empty()))
                .then(commands.argument("preset", CommandArgument.greedyString())
                        .requires(source -> canSelect(runtime, source, permissions))
                        .suggests((context, input) -> {
                            RealmPlayerCommandRuntime value = runtime.get();
                            if (value == null) return List.of();
                            return value.selectablePresets().stream().map(preset -> preset.id().value()).toList();
                        })
                        .executes(context -> create(
                                context.source(), runtime, messages,
                                Optional.of(context.string("preset")))));

        CommandBuilder root = commands.literal("realm")
                .then(create)
                .then(commands.literal("presets")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.PRESETS))
                        .executes(context -> presets(context.source(), runtime, messages)))
                .then(commands.literal("home")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.HOME))
                        .executes(context -> home(context.source(), runtime, messages)))
                .then(commands.literal("setspawn")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.SET_SPAWN))
                        .executes(context -> setSpawn(context.source(), runtime, messages)))
                .then(commands.literal("info")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.INFO))
                        .executes(context -> info(context.source(), runtime, messages)))
                .then(commands.literal("leave")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.LEAVE))
                        .executes(context -> leave(context.source(), runtime)))
                .then(commands.literal("reset")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.RESET))
                        .executes(context -> requestReset(context.source(), runtime, Optional.empty()))
                        .then(commands.argument("preset", CommandArgument.greedyString())
                                .suggests((context, input) -> {
                                    RealmPlayerCommandRuntime value = runtime.get();
                                    return value == null ? List.of() : value.selectablePresets().stream()
                                            .map(preset -> preset.id().value()).toList();
                                })
                                .executes(context -> requestReset(context.source(), runtime,
                                        Optional.of(context.string("preset")))))
                        .then(commands.literal("confirm").then(commands.argument("token", CommandArgument.word())
                                .executes(context -> confirmReset(context.source(), runtime,
                                        context.string("token")))))
                        .then(commands.literal("cancel")
                                .executes(context -> cancelReset(context.source(), runtime))))
                .then(commands.literal("delete")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.DELETE))
                        .executes(context -> requestDelete(context.source(), runtime))
                        .then(commands.literal("confirm").then(commands.argument("token", CommandArgument.word())
                                .executes(context -> confirmDelete(context.source(), runtime,
                                        context.string("token")))))
                        .then(commands.literal("cancel")
                                .executes(context -> cancelDelete(context.source(), runtime))));
        commands.register(root);
    }

    private static int requestReset(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            Optional<String> requestedPreset) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        PresetSelectionResult selected;
        try {
            selected = runtime.selectPreset(requestedPreset.map(RealmPresetId::new));
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.realm.errors.invalid_preset");
            return 0;
        }
        if (!selected.selected()) {
            source.sendError(presetSelectionFailure(selected.status()));
            return 0;
        }
        Optional<String> token = runtime.requestResetConfirmation(player.uuid(), selected.preset().orElseThrow().id());
        if (token.isEmpty()) {
            source.sendErrorKey("commands.realm.errors.reset_unavailable");
            return 0;
        }
        source.sendFeedbackKey("commands.realm.reset_requested", Map.of("token", token.orElseThrow()));
        return 1;
    }

    private static int confirmReset(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            String token) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var result = runtime.confirmReset(player.uuid(), token);
        if (result.status()
                == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.PRE_OPERATION_BACKUP_QUEUED) {
            source.sendFeedbackKey("commands.realm.reset_backup_queued");
            return 1;
        }
        if (result.status() != eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.RESET_COMPLETED) {
            source.sendError(resetFailureMessage(result.status()));
            return 0;
        }
        source.sendFeedbackKey("commands.realm.reset_completed");
        return 1;
    }

    private static int cancelReset(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        runtime.cancelReset(player.uuid());
        source.sendFeedbackKey("commands.realm.reset_cancelled");
        return 1;
    }

    private static int requestDelete(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        Optional<String> token = runtime.requestDeleteConfirmation(player.uuid());
        if (token.isEmpty()) {
            source.sendErrorKey("commands.realm.errors.delete_unavailable");
            return 0;
        }
        source.sendFeedbackKey("commands.realm.delete_requested", Map.of("token", token.orElseThrow()));
        return 1;
    }

    private static int confirmDelete(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            String token) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var result = runtime.confirmDelete(player.uuid(), token);
        if (result.status()
                == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.PRE_OPERATION_BACKUP_QUEUED) {
            source.sendFeedbackKey("commands.realm.delete_backup_queued");
            return 1;
        }
        if (result.status() != eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.ARCHIVED) {
            source.sendError(deleteFailureMessage(result.status()));
            return 0;
        }
        source.sendFeedbackKey("commands.realm.delete_completed");
        return 1;
    }

    private static int cancelDelete(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        runtime.cancelDelete(player.uuid());
        source.sendFeedbackKey("commands.realm.delete_cancelled");
        return 1;
    }

    private static String resetFailureMessage(
            eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status status) {
        String key = switch (status) {
            case CONFIRMATION_INVALID -> "confirmation_invalid";
            case PRESET_UNAVAILABLE -> "preset_unavailable";
            case OPERATION_IN_PROGRESS -> "operation_in_progress";
            case PRE_OPERATION_BACKUP_FAILED -> "backup_failed";
            case RESET_FAILED_OLD_REALM_PRESERVED -> "failed_preserved";
            case NO_REALM -> "no_realm";
            default -> "generic";
        };
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.realm.errors.reset." + key);
    }

    private static String deleteFailureMessage(
            eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status status) {
        String key = switch (status) {
            case CONFIRMATION_INVALID -> "confirmation_invalid";
            case OPERATION_IN_PROGRESS -> "operation_in_progress";
            case PRE_OPERATION_BACKUP_FAILED -> "backup_failed";
            case NO_REALM -> "no_realm";
            default -> "generic";
        };
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.realm.errors.delete." + key);
    }

    private static int create(
            CommandSource source,
            Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            CommandMessageService messages,
            Optional<String> requestedPreset) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        try {
            Optional<RealmPresetId> requested = requestedPreset.map(RealmPresetId::new);
            PresetSelectionResult selected = runtime.selectPreset(requested);
            if (!selected.selected()) {
                source.sendError(presetSelectionFailure(selected.status()));
                return 0;
            }
            var realm = runtime.createRealm(player.uuid(), selected.preset().orElseThrow());
            Map<String, String> values = Map.of(
                    "realm_id", Long.toString(realm.id().value()),
                    "realm_preset", realm.preset().value(),
                    "realm_state", realm.state().name());
            if (realm.state() == RealmLifecycleState.ACTIVE) {
                messages.sendLocalized(source, "commands.realm.created", values);
                TeleportResult teleport = runtime.teleportHome(player.uuid(), realm);
                if (teleport != TeleportResult.SUCCESS) {
                    source.sendErrorKey("commands.realm.errors.created_teleport_failed",
                            Map.of("detail", teleportFailureMessage(teleport)));
                }
                return 1;
            }
            source.sendErrorKey("commands.realm.errors.generation_failed",
                    Map.of("realm_id", Long.toString(realm.id().value())));
        } catch (RealmAlreadyExistsException exception) {
            source.sendErrorKey("commands.realm.errors.already_owned");
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.realm.errors.invalid_placement");
        } catch (ReadOnlyStoreException exception) {
            source.sendErrorKey("commands.realm.errors.read_only");
        }
        return 0;
    }

    private static int presets(
            CommandSource source,
            Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var values = runtime.selectablePresets();
        if (values.isEmpty()) {
            source.sendErrorKey("commands.realm.errors.no_presets");
            return 0;
        }
        String defaultId = runtime.presetSelection().defaultPreset().value();
        messages.sendLocalized(source, "commands.realm.presets", Map.of());
        values.forEach(preset -> source.sendFeedbackKey(
                preset.id().value().equals(defaultId)
                        ? "commands.realm.preset_line_default" : "commands.realm.preset_line",
                Map.of("preset", preset.id().value(), "description", preset.description())));
        return values.size();
    }

    private static int home(
            CommandSource source,
            Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var realm = runtime.findRealmByOwner(player.uuid());
        if (realm.isEmpty()) {
            source.sendErrorKey("commands.realm.errors.not_owner");
            return 0;
        }
        TeleportResult result = runtime.teleportHome(player.uuid(), realm.orElseThrow());
        if (result != TeleportResult.SUCCESS) {
            source.sendError(teleportFailureMessage(result));
            return 0;
        }
        messages.sendLocalized(source, "commands.realm.welcome_home", Map.of());
        return 1;
    }

    private static int setSpawn(
            CommandSource source,
            Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        SetSpawnResult result = runtime.setSpawn(player.uuid());
        if (result != SetSpawnResult.SUCCESS) {
            source.sendError(setSpawnFailureMessage(result));
            return 0;
        }
        messages.sendLocalized(source, "commands.realm.spawn_updated", Map.of());
        return 1;
    }

    private static int info(
            CommandSource source,
            Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var realm = runtime.findRealmByOwner(player.uuid());
        if (realm.isEmpty()) {
            source.sendErrorKey("commands.realm.errors.not_owner");
            return 0;
        }
        var value = realm.orElseThrow();
        messages.sendLocalized(source, "commands.realm.info",
                Map.of(
                        "realm_id", Long.toString(value.id().value()),
                        "realm_preset", value.preset().value(),
                        "realm_state", value.state().name()));
        if (!runtime.presetAvailable(value.preset())) {
            source.sendFeedbackKey("commands.realm.preset_metadata_unavailable");
        }
        return 1;
    }

    private static int leave(
            CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        TeleportResult result = runtime.leaveForeignRealm(player.uuid());
        if (result != TeleportResult.SUCCESS) {
            source.sendError(teleportFailureMessage(result));
            return 0;
        }
        source.sendFeedbackKey("commands.realm.left_safely");
        return 1;
    }

    private static boolean canSelect(
            Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            CommandSource source,
            CommandPermissionGate permissions) {
        RealmPlayerCommandRuntime runtime = runtimeSupplier.get();
        return runtime != null && runtime.presetSelection().allowPlayerSelection()
                && allowed(source, permissions, RealmPermissionNodes.PRESET_SELECT);
    }

    private static boolean allowed(
            CommandSource source, CommandPermissionGate permissions, RealmPermissionNode permission) {
        return permissions.allowed(source, permission);
    }

    private static PlayerReference requirePlayer(CommandSource source) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) source.sendErrorKey("errors.player_only");
        return player;
    }

    private static String teleportFailureMessage(TeleportResult result) {
        String key = switch (result) {
            case REALM_NOT_ACTIVE -> "realm_not_active";
            case WORLD_UNAVAILABLE -> "world_unavailable";
            case OUTSIDE_BOUNDS -> "outside_bounds";
            case OUTSIDE_WORLD_BORDER -> "outside_border";
            case UNSAFE_DESTINATION -> "unsafe_spawn";
            case RIDING_OR_HAS_PASSENGERS -> "riding";
            case SUCCESS -> "success";
        };
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.realm.errors.teleport." + key);
    }

    private static String setSpawnFailureMessage(SetSpawnResult result) {
        String key = switch (result) {
            case NO_REALM -> "no_realm";
            case REALM_NOT_ACTIVE -> "realm_not_active";
            case NOT_IN_REALMS -> "not_in_realms";
            case OUTSIDE_BOUNDS -> "outside_bounds";
            case UNSAFE_DESTINATION -> "unsafe";
            case SUCCESS -> "success";
        };
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.realm.errors.setspawn." + key);
    }

    private static String presetSelectionFailure(
            eu.avalanche7.paradigmrealms.generation.PresetSelectionStatus status) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                "commands.realm.errors.preset_selection."
                        + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static RealmPlayerCommandRuntime requireRuntime(
            CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendErrorKey("errors.startup_incomplete");
        return runtime;
    }
}
