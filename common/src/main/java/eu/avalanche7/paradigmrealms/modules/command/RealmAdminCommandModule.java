package eu.avalanche7.paradigmrealms.modules.command;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;
import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportResult;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationSeverity;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandArgument;
import eu.avalanche7.paradigmrealms.platform.command.CommandBuilder;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessageService;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;

public final class RealmAdminCommandModule {
    private RealmAdminCommandModule() {}

    public static void register(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmAdminCommandRuntime> runtime) {
        CommandPlatform commands = platform.commands();
        CommandPermissionGate permissions = platform.permissions();
        CommandMessageService messages = platform.messages();

        CommandBuilder admin = commands.literal("admin")
                .requires(source -> RealmAdminCommandAccess.allowedAny(source, permissions))
                .then(commands.literal("list")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_INSPECT))
                        .executes(context -> list(context.source(), runtime, messages)))
                .then(commands.literal("info")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_INSPECT))
                        .then(commands.argument("realmId", CommandArgument.longArgument(1, RealmAllocator.MAX_REALM_ID))
                                .executes(context -> info(
                                        context.source(), runtime, messages, context.longValue("realmId")))))
                .then(commands.literal("owner")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_INSPECT))
                        .then(commands.argument("player", CommandArgument.playerProfiles())
                                .executes(context -> owner(
                                        context.source(), runtime, messages,
                                        context.playerProfiles("player")))))
                .then(commands.literal("allocation")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_INSPECT))
                        .then(commands.literal("preview")
                                .then(commands.argument("realmId", CommandArgument.longArgument(
                                                1, RealmAllocator.MAX_REALM_ID))
                                        .executes(context -> allocationPreview(
                                                context.source(), runtime, messages,
                                                context.longValue("realmId"))))))
                .then(commands.literal("validate")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_INSPECT))
                        .executes(context -> validate(context.source(), runtime, messages)))
                .then(presets(commands, runtime, permissions, messages))
                .then(bypass(commands, runtime, permissions));
        commands.register(commands.literal("realms").then(admin));
    }

    private static CommandBuilder presets(
            CommandPlatform commands, Supplier<? extends RealmAdminCommandRuntime> runtime,
            CommandPermissionGate permissions, CommandMessageService messages) {
        return commands.literal("presets")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_PRESETS))
                .then(commands.literal("list")
                        .executes(context -> presetList(context.source(), runtime, messages)))
                .then(commands.literal("info")
                        .then(commands.argument("preset", CommandArgument.greedyString())
                                .suggests((context, input) -> runtimeValue(runtime) == null ? List.of()
                                        : runtimeValue(runtime).presetCatalogSnapshot().catalog().all().stream()
                                                .map(preset -> preset.id().value()).toList())
                                .executes(context -> presetInfo(
                                        context.source(), runtime, messages, context.string("preset")))))
                .then(commands.literal("validate")
                        .executes(context -> presetValidate(context.source(), runtime, messages)))
                .then(commands.literal("reload")
                        .executes(context -> presetReload(context.source(), runtime)))
                .then(commands.literal("imports")
                        .then(commands.literal("list")
                                .executes(context -> importList(context.source(), runtime, messages)))
                        .then(commands.literal("inspect")
                                .then(commands.argument("file", CommandArgument.string())
                                        .suggests((context, input) -> runtimeValue(runtime) == null ? List.of()
                                                : runtimeValue(runtime).presetImportFiles())
                                        .executes(context -> inspectImport(
                                                context.source(), runtime, messages,
                                                context.string("file")))))
                        .then(commands.literal("import")
                                .then(commands.argument("file", CommandArgument.string())
                                        .suggests((context, input) -> runtimeValue(runtime) == null ? List.of()
                                                : runtimeValue(runtime).presetImportFiles())
                                        .then(commands.argument("presetId", CommandArgument.greedyString())
                                                .executes(context -> publishImport(
                                                        context.source(), runtime, messages,
                                                        context.string("file"),
                                                        context.string("presetId"))))))
                        .then(commands.literal("remove")
                                .then(commands.argument("presetId", CommandArgument.greedyString())
                                        .suggests((context, input) -> importedPresetSuggestions(runtime))
                                        .executes(context -> removeImport(
                                                context.source(), runtime, messages,
                                                context.string("presetId")))))
                        .then(commands.literal("reimport")
                                .then(commands.argument("presetId", CommandArgument.greedyString())
                                        .suggests((context, input) -> importedPresetSuggestions(runtime))
                                        .executes(context -> reimport(
                                                context.source(), runtime, messages,
                                                context.string("presetId"))))));
    }

    private static CommandBuilder bypass(
            CommandPlatform commands, Supplier<? extends RealmAdminCommandRuntime> runtime,
            CommandPermissionGate permissions) {
        return commands.literal("bypass")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_BYPASS))
                .then(commands.literal("on").executes(context -> bypass(context.source(), runtime, true)))
                .then(commands.literal("off").executes(context -> bypass(context.source(), runtime, false)))
                .then(commands.literal("status").executes(context -> bypassStatus(context.source(), runtime)));
    }

    private static int list(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var realms = runtime.inspectRealms();
        feedback(source, messages, "Realms: " + realms.size());
        realms.forEach(realm -> feedback(source, messages, summary(realm)));
        return realms.size();
    }

    private static int info(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        return runtime.inspectRealm(new RealmId(id)).map(realm -> {
            feedback(source, messages, summary(realm));
            feedback(source, messages, "dimension=" + realm.dimension()
                    + " cell=" + realm.allocation().cell()
                    + " cellBounds=" + realm.allocation().cellBounds()
                    + " buildable=" + realm.allocation().buildableBounds());
            feedback(source, messages, "spawn=" + realm.spawn()
                    + " preset=" + realm.preset()
                    + " members=" + realm.members().size()
                    + " visitors=" + realm.invitedVisitors().size()
                    + " policy=" + realm.accessPolicy()
                    + " createdAt=" + realm.createdAt());
            realm.failure().ifPresent(failure -> feedback(source, messages, "failure=" + failure));
            return 1;
        }).orElseGet(() -> {
            source.sendError("Unknown realm ID " + id);
            return 0;
        });
    }

    private static int owner(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, List<PlayerIdentity> profiles) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        if (profiles.isEmpty()) {
            source.sendError("The selected player has no UUID");
            return 0;
        }
        PlayerIdentity profile = profiles.getFirst();
        return runtime.inspectRealmOwner(profile.uuid()).map(realm -> {
            feedback(source, messages, summary(realm));
            return 1;
        }).orElseGet(() -> {
            source.sendError("No realm belongs to " + profile.name());
            return 0;
        });
    }

    private static int allocationPreview(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        RealmAllocation allocation = runtime.previewAllocation(new RealmId(id));
        feedback(source, messages, "Realm " + id + " cell=" + allocation.cell()
                + " cellBounds=" + allocation.cellBounds()
                + " buildable=" + allocation.buildableBounds());
        return 1;
    }

    private static int validate(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var report = runtime.validateRealms();
        if (report.issues().isEmpty()) {
            feedback(source, messages, "Paradigm Realms state is valid");
            return 1;
        }
        feedback(source, messages, "Validation issues: " + report.issues().size());
        report.issues().forEach(issue -> reportIssue(source, messages, issue));
        return report.isValid() ? 1 : 0;
    }

    private static int presetList(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var snapshot = runtime.presetCatalogSnapshot();
        feedback(source, messages, "Preset catalog: " + snapshot.catalog().all().size()
                + " definitions; loaded " + snapshot.loadedAt());
        snapshot.catalog().all().forEach(preset -> feedback(source, messages,
                preset.id().value() + " v" + preset.version()
                        + " " + (preset.enabled() ? "ENABLED" : "DISABLED")
                        + " " + preset.sourceType()
                        + (preset.playerSelectable() ? " SELECTABLE" : "")
                        + (preset.legacy() ? " LEGACY" : "")
                        + (preset.disableReasons().isEmpty() ? ""
                                : " - " + String.join("; ", preset.disableReasons()))));
        return snapshot.catalog().all().size();
    }

    private static int presetInfo(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, String presetValue) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            var found = runtime.presetCatalogSnapshot().catalog().resolve(new RealmPresetId(presetValue));
            if (found.isEmpty()) {
                source.sendError("Unknown preset " + presetValue);
                return 0;
            }
            var preset = found.orElseThrow();
            feedback(source, messages, "id=" + preset.id() + " version=" + preset.version()
                    + " revision=" + preset.revision() + " source=" + preset.sourceType());
            feedback(source, messages, "format=" + preset.placementFormat()
                    + " structure=" + preset.structure().map(RealmPresetId::value).orElse("none")
                    + " bounds=" + preset.bounds() + " spawn=" + preset.spawn());
            feedback(source, messages, "selectable=" + preset.selectable() + " legacy=" + preset.legacy()
                    + " requiredMods=" + preset.requiredMods() + " aliases=" + preset.aliases()
                    + " fingerprint=" + preset.fingerprint().orElse("none"));
            if (!preset.disableReasons().isEmpty()) {
                feedback(source, messages, "disabled: " + String.join("; ", preset.disableReasons()));
            }
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid preset identifier.");
            return 0;
        }
    }

    private static int presetValidate(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var issues = runtime.presetValidationIssues();
        if (issues.isEmpty()) {
            feedback(source, messages,
                    "Preset catalog, configuration, realm references, and void invariant are valid");
            return 1;
        }
        feedback(source, messages, "Preset validation issues: " + issues.size());
        issues.forEach(issue -> reportIssue(source, messages, issue));
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR) ? 0 : 1;
    }

    private static int presetReload(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        source.sendFeedback("Reloading server resources and realm preset catalog...");
        runtime.reloadPresetCatalog().whenComplete((snapshot, failure) -> source.executeOnServerThread(() -> {
            if (failure != null) {
                source.sendError("Preset reload failed; prior catalog remains available: "
                        + failure.getClass().getSimpleName());
            } else {
                source.sendFeedback("Preset reload published " + snapshot.catalog().all().size()
                        + " definitions with " + snapshot.catalog().loadIssues().size()
                        + " isolated issue(s).");
            }
        }));
        return 1;
    }

    private static int importList(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        feedback(source, messages, "Import directory: config/paradigm-realms/imports");
        try {
            var bindings = runtime.presetImportBindings();
            feedback(source, messages, "Bound import definitions: " + bindings.size());
            bindings.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparing(RealmPresetId::value)))
                    .forEach(entry -> feedback(source, messages, entry.getKey() + " <- " + entry.getValue()));
        } catch (IOException exception) {
            source.sendError("Cannot read imported preset bindings: " + exception.getMessage());
            return 0;
        }
        var files = runtime.presetImportFiles();
        feedback(source, messages, "Available files: " + files.size());
        files.forEach(file -> feedback(source, messages, "file=" + file));
        return 1;
    }

    private static int inspectImport(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, String file) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        return runtime == null ? 0 : reportImport(source, messages, runtime.inspectPresetImport(file));
    }

    private static int publishImport(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, String file, String presetValue) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            RealmPresetId id = new RealmPresetId(presetValue);
            id.requireNamespaced();
            PresetImportResult inspected = runtime.inspectPresetImport(file);
            feedback(source, messages, "Pre-publication validation summary:");
            reportImport(source, messages, inspected);
            if (!inspected.successful()) return 0;
            return reportImport(source, messages, runtime.importPreset(file, id));
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid namespaced preset ID.");
            return 0;
        }
    }

    private static int removeImport(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, String presetValue) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            return reportImport(source, messages,
                    runtime.removePresetImport(new RealmPresetId(presetValue)));
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid preset ID.");
            return 0;
        }
    }

    private static int reimport(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, String presetValue) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        try {
            return reportImport(source, messages, runtime.reimportPreset(new RealmPresetId(presetValue)));
        } catch (IllegalArgumentException exception) {
            source.sendError("Invalid preset ID.");
            return 0;
        }
    }

    private static int reportImport(
            CommandSource source, CommandMessageService messages, PresetImportResult result) {
        feedback(source, messages, "status=" + result.status() + " file=" + result.sourceFile()
                + result.presetId().map(id -> " preset=" + id).orElse(""));
        if (result.format().isPresent()) {
            feedback(source, messages, "format=" + result.format().orElseThrow()
                    + " fingerprint=" + result.fingerprint().orElse("none")
                    + " bounds=" + result.bounds().map(Object::toString).orElse("none")
                    + " blocks=" + result.blockCount()
                    + " sanitizedBlockEntities=" + result.sanitizedBlockEntityCount());
        }
        result.warnings().forEach(warning -> feedback(source, messages, "WARNING: " + warning));
        result.errors().forEach(source::sendError);
        return result.successful() ? 1 : 0;
    }

    private static int bypass(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            boolean enabled) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = source.player().orElse(null);
        if (runtime == null || player == null) {
            source.sendError("Session bypass can only be used by an online player.");
            return 0;
        }
        if (enabled) {
            runtime.enableSessionBypass(player.uuid());
            source.sendFeedback("WARNING: explicit realm protection bypass is ON for this session.");
        } else {
            runtime.disableSessionBypass(player.uuid());
            source.sendFeedback("Realm protection bypass is OFF.");
        }
        return 1;
    }

    private static int bypassStatus(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = source.player().orElse(null);
        if (runtime == null || player == null) {
            source.sendError("Session bypass status is only available to a player.");
            return 0;
        }
        source.sendFeedback("Realm protection bypass is "
                + (runtime.sessionBypassEnabled(player.uuid()) ? "ON" : "OFF") + ".");
        return 1;
    }

    private static List<String> importedPresetSuggestions(
            Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        RealmAdminCommandRuntime runtime = runtimeValue(runtimeSupplier);
        return runtime == null ? List.of() : runtime.presetCatalogSnapshot().importedPresetIds().stream()
                .map(RealmPresetId::value).sorted().toList();
    }

    private static RealmAdminCommandRuntime runtimeValue(
            Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        return runtimeSupplier.get();
    }

    private static RealmAdminCommandRuntime requireRuntime(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        RealmAdminCommandRuntime runtime = runtimeSupplier.get();
        if (runtime == null) source.sendError("Paradigm Realms has not completed server startup");
        return runtime;
    }

    private static void reportIssue(
            CommandSource source, CommandMessageService messages, ValidationIssue issue) {
        feedback(source, messages, issue.severity() + " " + issue.code()
                + " " + issue.path() + ": " + issue.message());
    }

    private static boolean allowed(
            CommandSource source, CommandPermissionGate permissions, RealmPermissionNode permission) {
        return permissions.allowed(source, permission);
    }

    private static String summary(Realm realm) {
        return "realm=" + realm.id().value()
                + " owner=" + realm.owner().uuid()
                + " state=" + realm.state();
    }

    private static void feedback(
            CommandSource source, CommandMessageService messages, String message) {
        messages.send(source, "<color:aqua>{message}</color>", Map.of("message", message), message);
    }
}
