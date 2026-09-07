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
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_VALIDATE))
                        .executes(context -> validate(context.source(), runtime, messages)))
                .then(repair(commands, runtime, permissions))
                .then(commands.literal("support")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_SUPPORT))
                        .then(commands.literal("export")
                                .executes(context -> supportExport(context.source(), runtime))))
                .then(commands.literal("config")
                        .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_CONFIG))
                        .then(commands.literal("validate")
                                .executes(context -> config(context.source(), runtime, false)))
                        .then(commands.literal("reload")
                                .executes(context -> config(context.source(), runtime, true))))
                .then(realmLifecycle(commands, runtime, permissions, messages))
                .then(presets(commands, runtime, permissions, messages))
                .then(bypass(commands, runtime, permissions));
        commands.register(commands.literal("realms").then(admin));
    }

    private static CommandBuilder repair(
            CommandPlatform commands, Supplier<? extends RealmAdminCommandRuntime> runtime,
            CommandPermissionGate permissions) {
        return commands.literal("repair")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_REPAIR))
                .then(commands.literal("preview")
                        .executes(context -> repairPreview(context.source(), runtime)))
                .then(commands.literal("indexes")
                        .executes(context -> repairIndexes(context.source(), runtime)))
                .then(commands.literal("stale-sessions")
                        .executes(context -> repairStaleSessions(context.source(), runtime)))
                .then(commands.literal("expired-operations")
                        .executes(context -> repairExpiredOperations(context.source(), runtime)));
    }

    private static int repairPreview(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier) {
        requireRuntime(source, supplier).repairPreview().forEach(value ->
                source.sendFeedbackKey("common.detail", Map.of("detail", value)));
        return 1;
    }

    private static int repairIndexes(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier) {
        if (!requireRuntime(source, supplier).repairIndexes()) {
            source.sendErrorKey("commands.admin.errors.index_repair_refused");
            return 0;
        }
        source.sendFeedbackKey("commands.admin.indexes_rebuilt");
        return 1;
    }

    private static int repairStaleSessions(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier) {
        int removed = requireRuntime(source, supplier).repairStaleSessions();
        source.sendFeedbackKey("commands.admin.stale_sessions_removed", Map.of("count", Integer.toString(removed)));
        return 1;
    }

    private static int repairExpiredOperations(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier) {
        int removed = requireRuntime(source, supplier).repairExpiredOperations();
        source.sendFeedbackKey("commands.admin.expired_operations_removed",
                Map.of("count", Integer.toString(removed)));
        return 1;
    }

    private static int supportExport(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier) {
        var result = requireRuntime(source, supplier).exportSupportBundle();
        if (result.isEmpty()) {
            source.sendErrorKey("commands.admin.errors.support_export_failed");
            return 0;
        }
        source.sendFeedbackKey("commands.admin.support_exported", Map.of("path", result.orElseThrow().toString()));
        return 1;
    }

    private static int config(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier, boolean reload) {
        var result = reload ? requireRuntime(source, supplier).reloadConfig()
                : requireRuntime(source, supplier).validateConfig();
        result.applied().forEach(value -> source.sendFeedbackKey("commands.admin.config.applied",
                Map.of("value", value)));
        result.deferred().forEach(value -> source.sendFeedbackKey("commands.admin.config.deferred",
                Map.of("value", value)));
        result.restartRequired().forEach(value -> source.sendFeedbackKey("commands.admin.config.restart",
                Map.of("value", value)));
        result.rejected().forEach(value -> source.sendErrorKey("commands.admin.config.rejected",
                Map.of("value", value)));
        return result.valid() ? 1 : 0;
    }

    private static CommandBuilder realmLifecycle(
            CommandPlatform commands, Supplier<? extends RealmAdminCommandRuntime> runtime,
            CommandPermissionGate permissions, CommandMessageService messages) {
        CommandBuilder archives = commands.literal("archives")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_ARCHIVES))
                .then(commands.literal("list")
                        .executes(context -> archiveList(context.source(), runtime, messages)))
                .then(commands.literal("info")
                        .then(realmIdArgument(commands, runtime, true)
                                .executes(context -> archiveInfo(context.source(), runtime, messages,
                                        context.longValue("realmId")))))
                .then(commands.literal("restore")
                        .then(realmIdArgument(commands, runtime, true)
                                .executes(context -> archiveRestore(context.source(), runtime,
                                        context.longValue("realmId")))));
        CommandBuilder operation = commands.literal("operation")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.ADMIN_OPERATIONS))
                .then(commands.literal("info")
                        .then(realmIdArgument(commands, runtime, false)
                                .executes(context -> operationInfo(context.source(), runtime,
                                        context.longValue("realmId")))))
                .then(commands.literal("retry")
                        .then(realmIdArgument(commands, runtime, false)
                                .executes(context -> operationRetry(context.source(), runtime,
                                        context.longValue("realmId")))));
        return commands.literal("realm").then(archives).then(operation);
    }

    private static CommandBuilder realmIdArgument(
            CommandPlatform commands, Supplier<? extends RealmAdminCommandRuntime> runtime, boolean archived) {
        return commands.argument("realmId", CommandArgument.longArgument(1, RealmAllocator.MAX_REALM_ID))
                .suggests((context, input) -> {
                    RealmAdminCommandRuntime value = runtimeValue(runtime);
                    if (value == null) return List.of();
                    return value.inspectRealms().stream()
                            .filter(realm -> archived
                                    ? realm.state() == eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ARCHIVED
                                    : realm.lifecycleOperation().isPresent())
                            .map(realm -> Long.toString(realm.id().value())).toList();
                });
    }

    private static int archiveList(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) return 0;
        List<Realm> archives = runtime.inspectRealms().stream()
                .filter(realm -> realm.state()
                        == eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ARCHIVED)
                .toList();
        localizedFeedback(source, messages, "commands.admin.archives.title",
                Map.of("count", Integer.toString(archives.size())));
        archives.forEach(realm -> localizedFeedback(source, messages, "commands.admin.archives.line", Map.of(
                "summary", summary(realm),
                "archived_at", realm.archivedAt().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.unknown")),
                "replaced_by", realm.replacedBy().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none")))));
        return 1;
    }

    private static int archiveInfo(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier,
            CommandMessageService messages, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) return 0;
        Realm realm = runtime.inspectRealm(new RealmId(id)).orElse(null);
        if (realm == null || realm.state()
                != eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ARCHIVED) {
            source.sendErrorKey("commands.admin.errors.not_archived");
            return 0;
        }
        feedback(source, messages, summary(realm));
        localizedFeedback(source, messages, "commands.admin.archives.info", Map.of(
                "profile", realm.allocation().profile().toString(),
                "allocation", realm.allocation().cell().toString(),
                "archived_at", realm.archivedAt().orElseThrow().toString(),
                "replacement_of", realm.replacementOf().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none")),
                "replaced_by", realm.replacedBy().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none"))));
        return 1;
    }

    private static int archiveRestore(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) return 0;
        var result = runtime.restoreArchive(new RealmId(id));
        if (result.status()
                != eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.RESTORED) {
            source.sendErrorKey("commands.admin.errors.archive_restore_rejected",
                    Map.of("status", result.status().name()));
            return 0;
        }
        source.sendFeedbackKey("commands.admin.archive_restored", Map.of("realm_id", Long.toString(id)));
        return 1;
    }

    private static int operationInfo(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) return 0;
        Realm realm = runtime.inspectRealm(new RealmId(id)).orElse(null);
        if (realm == null || realm.lifecycleOperation().isEmpty()) {
            source.sendErrorKey("commands.admin.errors.no_lifecycle_operation");
            return 0;
        }
        var operation = realm.lifecycleOperation().orElseThrow();
        source.sendFeedbackKey("commands.admin.operation.info", Map.of(
                "operation", operation.operationId().toString(), "kind", operation.kind().name(),
                "stage", operation.stage().name(),
                "target", operation.targetRealmId().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none"))));
        operation.failureCode().ifPresent(code -> source.sendFeedbackKey("commands.admin.operation.failure",
                Map.of("code", code, "detail", operation.failureDetail().orElse(""))));
        return 1;
    }

    private static int operationRetry(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> supplier, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) return 0;
        var result = runtime.retryRealmOperation(new RealmId(id));
        if (result.status()
                == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.RESET_FAILED_OLD_REALM_PRESERVED
                || result.status()
                == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.NO_REALM) {
            source.sendErrorKey("commands.admin.errors.lifecycle_retry_failed",
                    Map.of("status", result.status().name()));
            return 0;
        }
        source.sendFeedbackKey("commands.admin.lifecycle_retry_result", Map.of("status", result.status().name()));
        return 1;
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
        localizedFeedback(source, messages, "commands.admin.realms_title",
                Map.of("count", Integer.toString(realms.size())));
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
            localizedFeedback(source, messages, "commands.admin.realm.allocation", Map.of(
                    "dimension", realm.dimension().toString(), "profile", realm.allocation().profile().toString(),
                    "cell", realm.allocation().cell().toString(),
                    "cell_bounds", realm.allocation().cellBounds().toString(),
                    "buildable", realm.allocation().buildableBounds().toString()));
            localizedFeedback(source, messages, "commands.admin.realm.details", Map.of(
                    "spawn", realm.spawn().toString(), "preset", realm.preset().toString(),
                    "members", Integer.toString(realm.members().size()),
                    "visitors", Integer.toString(realm.invitedVisitors().size()),
                    "policy", realm.accessPolicy().name(), "created_at", realm.createdAt().toString()));
            realm.failure().ifPresent(failure -> localizedFeedback(source, messages,
                    "commands.admin.realm.failure", Map.of("failure", failure.toString())));
            return 1;
        }).orElseGet(() -> {
            source.sendErrorKey("commands.admin.errors.unknown_realm_id", Map.of("realm_id", Long.toString(id)));
            return 0;
        });
    }

    private static int owner(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, List<PlayerIdentity> profiles) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        if (profiles.isEmpty()) {
            source.sendErrorKey("commands.admin.errors.player_uuid_missing");
            return 0;
        }
        PlayerIdentity profile = profiles.getFirst();
        return runtime.inspectRealmOwner(profile.uuid()).map(realm -> {
            feedback(source, messages, summary(realm));
            return 1;
        }).orElseGet(() -> {
            source.sendErrorKey("commands.admin.errors.owner_has_no_realm", Map.of("player", profile.name()));
            return 0;
        });
    }

    private static int allocationPreview(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages, long id) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        RealmAllocation allocation = runtime.previewAllocation(new RealmId(id));
        localizedFeedback(source, messages, "commands.admin.allocation_preview", Map.of(
                "realm_id", Long.toString(id), "profile", allocation.profile().toString(),
                "cell", allocation.cell().toString(), "cell_bounds", allocation.cellBounds().toString(),
                "buildable", allocation.buildableBounds().toString()));
        return 1;
    }

    private static int validate(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var report = runtime.validateRealms();
        if (report.issues().isEmpty()) {
            localizedFeedback(source, messages, "commands.admin.validation.valid", Map.of());
            return 1;
        }
        localizedFeedback(source, messages, "commands.admin.validation.issues",
                Map.of("count", Integer.toString(report.issues().size())));
        report.issues().forEach(issue -> reportIssue(source, messages, issue));
        return report.isValid() ? 1 : 0;
    }

    private static int presetList(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        var snapshot = runtime.presetCatalogSnapshot();
        localizedFeedback(source, messages, "commands.admin.presets.title", Map.of(
                "count", Integer.toString(snapshot.catalog().all().size()),
                "loaded_at", snapshot.loadedAt().toString()));
        snapshot.catalog().all().forEach(preset -> localizedFeedback(source, messages,
                "commands.admin.presets.line", Map.of(
                        "preset", preset.id().value(), "version", Integer.toString(preset.version()),
                        "enabled", eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                preset.enabled() ? "common.enabled_upper" : "common.disabled_upper"),
                        "source", preset.sourceType().name(),
                        "selectable", preset.playerSelectable()
                                ? eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.selectable_suffix") : "",
                        "legacy", preset.legacy()
                                ? eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.legacy_suffix") : "",
                        "reasons", preset.disableReasons().isEmpty() ? ""
                                : eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                                        "common.reason_suffix",
                                        Map.of("reasons", String.join("; ", preset.disableReasons()))))));
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
                source.sendErrorKey("commands.admin.errors.unknown_preset", Map.of("preset", presetValue));
                return 0;
            }
            var preset = found.orElseThrow();
            localizedFeedback(source, messages, "commands.admin.presets.identity", Map.of(
                    "id", preset.id().toString(), "version", Integer.toString(preset.version()),
                    "revision", preset.revision(), "source", preset.sourceType().name()));
            localizedFeedback(source, messages, "commands.admin.presets.placement", Map.of(
                    "format", preset.placementFormat(),
                    "structure", preset.structure().map(RealmPresetId::value).orElseGet(() ->
                            eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none")),
                    "bounds", preset.bounds().toString(), "spawn", preset.spawn().toString()));
            localizedFeedback(source, messages, "commands.admin.presets.flags", Map.of(
                    "selectable", Boolean.toString(preset.selectable()),
                    "legacy", Boolean.toString(preset.legacy()),
                    "required_mods", preset.requiredMods().toString(), "aliases", preset.aliases().toString(),
                    "fingerprint", preset.fingerprint().orElseGet(() ->
                            eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none"))));
            if (!preset.disableReasons().isEmpty()) {
                localizedFeedback(source, messages, "commands.admin.presets.disabled",
                        Map.of("reasons", String.join("; ", preset.disableReasons())));
            }
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.realm.errors.invalid_preset");
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
            localizedFeedback(source, messages, "commands.admin.presets.valid", Map.of());
            return 1;
        }
        localizedFeedback(source, messages, "commands.admin.presets.issues",
                Map.of("count", Integer.toString(issues.size())));
        issues.forEach(issue -> reportIssue(source, messages, issue));
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR) ? 0 : 1;
    }

    private static int presetReload(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        source.sendFeedbackKey("commands.admin.presets.reloading");
        runtime.reloadPresetCatalog().whenComplete((snapshot, failure) -> source.executeOnServerThread(() -> {
            if (failure != null) {
                source.sendErrorKey("commands.admin.errors.preset_reload_failed",
                        Map.of("error", failure.getClass().getSimpleName()));
            } else {
                source.sendFeedbackKey("commands.admin.presets.reloaded", Map.of(
                        "count", Integer.toString(snapshot.catalog().all().size()),
                        "issues", Integer.toString(snapshot.catalog().loadIssues().size())));
            }
        }));
        return 1;
    }

    private static int importList(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            CommandMessageService messages) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        if (runtime == null) return 0;
        localizedFeedback(source, messages, "commands.admin.imports.directory", Map.of());
        try {
            var bindings = runtime.presetImportBindings();
            localizedFeedback(source, messages, "commands.admin.imports.bindings",
                    Map.of("count", Integer.toString(bindings.size())));
            bindings.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparing(RealmPresetId::value)))
                    .forEach(entry -> localizedFeedback(source, messages, "commands.admin.imports.binding_line",
                            Map.of("preset", entry.getKey().toString(), "file", entry.getValue())));
        } catch (IOException exception) {
            source.sendErrorKey("commands.admin.errors.import_bindings_read",
                    Map.of("detail", String.valueOf(exception.getMessage())));
            return 0;
        }
        var files = runtime.presetImportFiles();
        localizedFeedback(source, messages, "commands.admin.imports.files",
                Map.of("count", Integer.toString(files.size())));
        files.forEach(file -> localizedFeedback(source, messages, "commands.admin.imports.file_line",
                Map.of("file", file)));
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
            localizedFeedback(source, messages, "commands.admin.imports.prepublication", Map.of());
            reportImport(source, messages, inspected);
            if (!inspected.successful()) return 0;
            return reportImport(source, messages, runtime.importPreset(file, id));
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.admin.errors.invalid_namespaced_preset");
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
            source.sendErrorKey("commands.admin.errors.invalid_preset_id");
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
            source.sendErrorKey("commands.admin.errors.invalid_preset_id");
            return 0;
        }
    }

    private static int reportImport(
            CommandSource source, CommandMessageService messages, PresetImportResult result) {
        localizedFeedback(source, messages, "commands.admin.imports.result", Map.of(
                "status", result.status().name(), "file", result.sourceFile(),
                "preset", result.presetId().map(id -> eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "commands.admin.imports.preset_suffix", Map.of("preset", id.toString()))).orElse("")));
        if (result.format().isPresent()) {
            localizedFeedback(source, messages, "commands.admin.imports.details", Map.of(
                    "format", result.format().orElseThrow().name(),
                    "fingerprint", result.fingerprint().orElseGet(() ->
                            eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none")),
                    "bounds", result.bounds().map(Object::toString).orElseGet(() ->
                            eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none")),
                    "blocks", Integer.toString(result.blockCount()),
                    "sanitized", Integer.toString(result.sanitizedBlockEntityCount())));
        }
        result.warnings().forEach(warning -> localizedFeedback(source, messages,
                "commands.admin.imports.warning", Map.of("warning", warning)));
        result.errors().forEach(error -> source.sendErrorKey("common.detail", Map.of("detail", error)));
        return result.successful() ? 1 : 0;
    }

    private static int bypass(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier,
            boolean enabled) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = source.player().orElse(null);
        if (runtime == null || player == null) {
            source.sendErrorKey("commands.admin.errors.bypass_player_required");
            return 0;
        }
        if (enabled) {
            runtime.enableSessionBypass(player.uuid());
            source.sendFeedbackKey("commands.admin.bypass_enabled");
        } else {
            runtime.disableSessionBypass(player.uuid());
            source.sendFeedbackKey("commands.admin.bypass_disabled");
        }
        return 1;
    }

    private static int bypassStatus(
            CommandSource source, Supplier<? extends RealmAdminCommandRuntime> runtimeSupplier) {
        RealmAdminCommandRuntime runtime = requireRuntime(source, runtimeSupplier);
        PlayerReference player = source.player().orElse(null);
        if (runtime == null || player == null) {
            source.sendErrorKey("commands.admin.errors.bypass_status_player_required");
            return 0;
        }
        source.sendFeedbackKey("commands.admin.bypass_status", Map.of("state",
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        runtime.sessionBypassEnabled(player.uuid()) ? "common.on_upper" : "common.off_upper")));
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
        if (runtime == null) source.sendErrorKey("errors.startup_incomplete");
        return runtime;
    }

    private static void reportIssue(
            CommandSource source, CommandMessageService messages, ValidationIssue issue) {
        localizedFeedback(source, messages, "commands.admin.validation.issue", Map.of(
                "severity", issue.severity().name(), "code", issue.code(),
                "path", issue.path(), "message", issue.message()));
    }

    private static boolean allowed(
            CommandSource source, CommandPermissionGate permissions, RealmPermissionNode permission) {
        return permissions.allowed(source, permission);
    }

    private static String summary(Realm realm) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.admin.realm.summary", Map.of(
                "realm_id", Long.toString(realm.id().value()), "owner", realm.owner().uuid().toString(),
                "state", realm.state().name()));
    }

    private static void feedback(
            CommandSource source, CommandMessageService messages, String message) {
        messages.sendLocalized(source, "commands.admin.message", Map.of("message", message));
    }

    private static void localizedFeedback(
            CommandSource source, CommandMessageService messages, String key, Map<String, String> values) {
        messages.sendLocalized(source, key, values);
    }
}
