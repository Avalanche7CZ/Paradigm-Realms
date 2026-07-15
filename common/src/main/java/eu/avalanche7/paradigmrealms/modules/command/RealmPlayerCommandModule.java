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
            source.sendError("Invalid preset identifier.");
            return 0;
        }
        if (!selected.selected()) {
            source.sendError("Preset unavailable: " + selected.detail());
            return 0;
        }
        Optional<String> token = runtime.requestResetConfirmation(player.uuid(), selected.preset().orElseThrow().id());
        if (token.isEmpty()) {
            source.sendError("No active realm is available for reset, or a lifecycle operation is already in progress.");
            return 0;
        }
        source.sendFeedback("Reset will archive the old realm after a replacement is active. Confirm with /realm reset confirm "
                + token.orElseThrow());
        return 1;
    }

    private static int confirmReset(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            String token) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var result = runtime.confirmReset(player.uuid(), token);
        if (result.status() != eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.RESET_COMPLETED) {
            source.sendError("Realm reset was not completed: " + result.status());
            return 0;
        }
        source.sendFeedback("Realm reset completed. Your old realm is archived.");
        return 1;
    }

    private static int cancelReset(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        runtime.cancelReset(player.uuid());
        source.sendFeedback("Pending realm reset confirmation cancelled.");
        return 1;
    }

    private static int requestDelete(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        Optional<String> token = runtime.requestDeleteConfirmation(player.uuid());
        if (token.isEmpty()) {
            source.sendError("No active realm is available for deletion, or a lifecycle operation is already in progress.");
            return 0;
        }
        source.sendFeedback("Deletion archives the realm and retains its protected cell. Confirm with /realm delete confirm "
                + token.orElseThrow());
        return 1;
    }

    private static int confirmDelete(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier,
            String token) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        var result = runtime.confirmDelete(player.uuid(), token);
        if (result.status() != eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.ARCHIVED) {
            source.sendError("Realm deletion was not completed: " + result.status());
            return 0;
        }
        source.sendFeedback("Realm archived. Its blocks and allocation remain protected.");
        return 1;
    }

    private static int cancelDelete(CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) return 0;
        runtime.cancelDelete(player.uuid());
        source.sendFeedback("Pending realm deletion confirmation cancelled.");
        return 1;
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
                source.sendError("Preset unavailable: " + selected.detail());
                return 0;
            }
            var realm = runtime.createRealm(player.uuid(), selected.preset().orElseThrow());
            Map<String, String> values = Map.of(
                    "realm_id", Long.toString(realm.id().value()),
                    "realm_preset", realm.preset().value(),
                    "realm_state", realm.state().name());
            if (realm.state() == RealmLifecycleState.ACTIVE) {
                messages.send(source,
                        "<color:aqua>Realm {realm_id}</color> created with preset {realm_preset}.",
                        values,
                        "Realm " + realm.id().value() + " created with preset " + realm.preset() + ".");
                TeleportResult teleport = runtime.teleportHome(player.uuid(), realm);
                if (teleport != TeleportResult.SUCCESS) {
                    source.sendError("Realm created, but teleport failed: " + teleport);
                }
                return 1;
            }
            source.sendError("Realm generation failed; realm " + realm.id().value()
                    + " remains reserved for administration.");
        } catch (RealmAlreadyExistsException exception) {
            source.sendError("You already own a realm.");
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid preset identifier or placement request.");
        } catch (ReadOnlyStoreException exception) {
            source.sendError("Realm storage is read-only; run /realms admin validate.");
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
            source.sendError("No realm presets are currently enabled by the server.");
            return 0;
        }
        String defaultId = runtime.presetSelection().defaultPreset().value();
        messages.send(source, "<color:aqua>Available realm presets:</color>", Map.of(),
                "Available realm presets:");
        values.forEach(preset -> source.sendFeedback(
                preset.id().value() + (preset.id().value().equals(defaultId) ? " [default]" : "")
                        + " - " + preset.description()));
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
            source.sendError("You do not own a realm.");
            return 0;
        }
        TeleportResult result = runtime.teleportHome(player.uuid(), realm.orElseThrow());
        if (result != TeleportResult.SUCCESS) {
            source.sendError("Unable to teleport home: " + result);
            return 0;
        }
        messages.send(source, "<color:aqua>Welcome home.</color>", Map.of(), "Welcome home.");
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
            source.sendError("Unable to set realm spawn: " + result);
            return 0;
        }
        messages.send(source, "<color:aqua>Realm spawn updated.</color>", Map.of(),
                "Realm spawn updated.");
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
            source.sendError("You do not own a realm.");
            return 0;
        }
        var value = realm.orElseThrow();
        messages.send(source,
                "<color:aqua>Realm {realm_id}</color>: {realm_preset}, {realm_state}",
                Map.of(
                        "realm_id", Long.toString(value.id().value()),
                        "realm_preset", value.preset().value(),
                        "realm_state", value.state().name()),
                "Realm " + value.id().value() + ": " + value.preset() + ", " + value.state());
        if (!runtime.presetAvailable(value.preset())) {
            source.sendFeedback("Preset metadata is currently unavailable; this does not affect the existing realm.");
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
            source.sendError("Unable to leave the realm safely: " + result);
            return 0;
        }
        source.sendFeedback("You left the realm safely.");
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
        if (player == null) source.sendError("This command can only be used by a player.");
        return player;
    }

    private static RealmPlayerCommandRuntime requireRuntime(
            CommandSource source, Supplier<? extends RealmPlayerCommandRuntime> runtimeSupplier) {
        RealmPlayerCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendError("Paradigm Realms has not completed server startup.");
        return runtime;
    }
}
