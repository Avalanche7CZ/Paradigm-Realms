package eu.avalanche7.paradigmrealms.modules.command;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupRequestResult;
import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.platform.RealmsPlatformAdapter;
import eu.avalanche7.paradigmrealms.platform.command.CommandArgument;
import eu.avalanche7.paradigmrealms.platform.command.CommandBuilder;
import eu.avalanche7.paradigmrealms.platform.command.CommandPermissionGate;
import eu.avalanche7.paradigmrealms.platform.command.CommandPlatform;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.player.PlayerDirectory;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentityResolution;

public final class RealmBackupCommandModule {
    private RealmBackupCommandModule() {}

    public static void register(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmBackupCommandRuntime> runtime) {
        registerPlayer(platform, runtime);
        registerAdmin(platform, runtime);
    }

    private static void registerPlayer(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmBackupCommandRuntime> runtime) {
        CommandPlatform commands = platform.commands();
        CommandPermissionGate permissions = platform.permissions();
        CommandBuilder backup = commands.literal("backup")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.BACKUP_SELF))
                .executes(context -> requestOwn(context.source(), runtime));
        CommandBuilder backups = commands.literal("backups")
                .requires(source -> allowed(source, permissions, RealmPermissionNodes.BACKUP_SELF_LIST))
                .executes(context -> listOwn(context.source(), runtime));
        commands.register(commands.literal("realm").then(backup).then(backups));
    }

    private static void registerAdmin(
            RealmsPlatformAdapter platform,
            Supplier<? extends RealmBackupCommandRuntime> runtime) {
        CommandPlatform commands = platform.commands();
        CommandPermissionGate permissions = platform.permissions();
        PlayerDirectory players = platform.players();

        CommandBuilder backups = commands.literal("backups")
                .then(commands.literal("status")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_STATUS))
                        .executes(context -> status(context.source(), runtime)))
                .then(commands.literal("create")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_CREATE))
                        .then(commands.literal("realm")
                                .then(commands.literal("all")
                                        .executes(context -> createAll(context.source(), runtime)))
                                .then(realmIdArgument(commands, runtime)
                                        .executes(context -> create(
                                                context.source(),
                                                runtime,
                                                context.longValue("realmId")))))
                        .then(commands.literal("player")
                                .then(commands.argument("owner", CommandArgument.word())
                                        .suggests((context, input) -> ownerSuggestions(
                                                context.source(), runtime, players))
                                        .executes(context -> createForOwner(
                                                context.source(), runtime, players,
                                                context.string("owner"))))))
                .then(listCommand(commands, runtime, permissions))
                .then(commands.literal("info")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_LIST))
                        .then(backupIdArgument(commands, runtime)
                                .executes(context -> info(
                                        context.source(), runtime, context.string("backupId")))))
                .then(commands.literal("verify")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_VERIFY))
                        .then(backupIdArgument(commands, runtime)
                                .executes(context -> verify(
                                        context.source(), runtime, context.string("backupId")))))
                .then(pinCommand(commands, runtime, permissions, true))
                .then(pinCommand(commands, runtime, permissions, false))
                .then(deleteCommand(commands, runtime, permissions))
                .then(commands.literal("prepare-restore")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_RESTORE))
                        .then(backupIdArgument(commands, runtime)
                                .executes(context -> prepareRestore(
                                        context.source(),
                                        runtime,
                                        context.string("backupId")))))
                .then(commands.literal("cancel-restore")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_RESTORE))
                        .then(backupIdArgument(commands, runtime)
                                .executes(context -> cancelRestore(
                                        context.source(),
                                        runtime,
                                        context.string("backupId")))))
                .then(commands.literal("schedule")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_SCHEDULE))
                        .then(commands.literal("status")
                                .executes(context -> status(context.source(), runtime)))
                        .then(commands.literal("run-due")
                                .executes(context -> runDue(context.source(), runtime))))
                .then(commands.literal("prune")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_PRUNE))
                        .then(commands.literal("preview")
                                .executes(context -> prune(context.source(), runtime, false)))
                        .then(commands.literal("run")
                                .executes(context -> prune(context.source(), runtime, true))))
                .then(commands.literal("catalog")
                        .requires(source -> allowed(
                                source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_LIST))
                        .then(commands.literal("validate")
                                .executes(context -> catalogValidate(context.source(), runtime)))
                        .then(commands.literal("rebuild")
                                .requires(source -> allowed(
                                        source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_PRUNE))
                                .executes(context -> catalogRebuild(context.source(), runtime))));

        commands.register(commands.literal("realms")
                .then(commands.literal("admin").then(backups)));
    }

    private static CommandBuilder listCommand(
            CommandPlatform commands,
            Supplier<? extends RealmBackupCommandRuntime> runtime,
            CommandPermissionGate permissions) {
        return commands.literal("list")
                .requires(source -> allowed(
                        source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_LIST))
                .executes(context -> list(context.source(), runtime, Optional.empty()))
                .then(commands.literal("realm")
                        .then(commands.argument("realmId", CommandArgument.longArgument(
                                        1, RealmAllocator.MAX_REALM_ID))
                                .executes(context -> list(
                                        context.source(),
                                        runtime,
                                        Optional.of(context.longValue("realmId"))))));
    }

    private static CommandBuilder pinCommand(
            CommandPlatform commands,
            Supplier<? extends RealmBackupCommandRuntime> runtime,
            CommandPermissionGate permissions,
            boolean pinned) {
        String literal = pinned ? "pin" : "unpin";
        return commands.literal(literal)
                .requires(source -> allowed(
                        source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_PIN))
                .then(backupIdArgument(commands, runtime)
                        .executes(context -> pin(
                                context.source(), runtime, context.string("backupId"), pinned)));
    }

    private static CommandBuilder backupIdArgument(
            CommandPlatform commands,
            Supplier<? extends RealmBackupCommandRuntime> runtime) {
        return commands.argument("backupId", CommandArgument.word())
                .suggests((context, input) -> {
                    RealmBackupCommandRuntime value = runtime.get();
                    return value == null
                            ? List.of()
                            : value.backups().stream().map(entry -> entry.backupId().value()).toList();
                });
    }

    private static CommandBuilder realmIdArgument(
            CommandPlatform commands,
            Supplier<? extends RealmBackupCommandRuntime> runtime) {
        return commands.argument("realmId", CommandArgument.longArgument(1, RealmAllocator.MAX_REALM_ID))
                .suggests((context, input) -> {
                    RealmBackupCommandRuntime value = runtime.get();
                    return value == null
                            ? List.of()
                            : value.backupRealmIds().stream().map(String::valueOf).toList();
                });
    }

    private static CommandBuilder deleteCommand(
            CommandPlatform commands,
            Supplier<? extends RealmBackupCommandRuntime> runtime,
            CommandPermissionGate permissions) {
        return commands.literal("delete")
                .requires(source -> allowed(
                        source, permissions, RealmPermissionNodes.ADMIN_BACKUPS_DELETE))
                .then(commands.literal("confirm")
                        .then(commands.argument("token", CommandArgument.word())
                                .executes(context -> confirmDelete(
                                        context.source(),
                                        runtime,
                                        context.string("token")))))
                .then(backupIdArgument(commands, runtime)
                        .executes(context -> requestDelete(
                                context.source(),
                                runtime,
                                context.string("backupId"))));
    }

    private static int requestOwn(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) {
            return 0;
        }
        return reportRequest(source, runtime.requestOwnBackup(player.uuid(), player.name()));
    }

    private static int listOwn(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        PlayerReference player = requirePlayer(source);
        if (runtime == null || player == null) {
            return 0;
        }
        List<BackupCatalogEntry> backups = runtime.ownBackups(player.uuid());
        source.sendFeedbackKey("commands.backups.own_title", Map.of("count", Integer.toString(backups.size())));
        backups.forEach(entry -> source.sendFeedback(playerSummary(entry)));
        return 1;
    }

    private static int create(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            long realmId) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        PlayerReference actor = source.player().orElse(null);
        java.util.UUID actorId = actor == null ? new java.util.UUID(0, 0) : actor.uuid();
        String actorName = actor == null ? source.name() : actor.name();
        return reportAdminRequest(
                source,
                runtime.requestAdminBackup(realmId, actorId, actorName),
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "commands.backups.target_realm", Map.of("realm_id", Long.toString(realmId))));
    }

    private static int createForOwner(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            PlayerDirectory players,
            String ownerName) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        PlayerIdentityResolution resolution = players.resolveCached(source, ownerName);
        if (resolution.status() == PlayerIdentityResolution.Status.AMBIGUOUS) {
            source.sendErrorKey("commands.backups.errors.owner_ambiguous");
            return 0;
        }
        if (resolution.status() == PlayerIdentityResolution.Status.UNKNOWN) {
            source.sendErrorKey("commands.backups.errors.owner_unknown");
            return 0;
        }

        PlayerIdentity owner = resolution.identity().orElseThrow();
        PlayerReference actor = source.player().orElse(null);
        java.util.UUID actorId = actor == null ? new java.util.UUID(0, 0) : actor.uuid();
        String actorName = actor == null ? source.name() : actor.name();
        return reportAdminRequest(
                source,
                runtime.requestAdminBackupForOwner(owner.uuid(), actorId, actorName),
                eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "commands.backups.target_owner", Map.of("owner", owner.name())));
    }

    private static List<String> ownerSuggestions(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            PlayerDirectory players) {
        RealmBackupCommandRuntime runtime = supplier.get();
        if (runtime == null) {
            return List.of();
        }
        return runtime.backupRealmOwners().stream()
                .map(owner -> players.cached(source, owner))
                .flatMap(Optional::stream)
                .map(PlayerIdentity::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private static int createAll(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        List<Long> realmIds = runtime.backupRealmIds();
        if (realmIds.isEmpty()) {
            source.sendErrorKey("commands.backups.errors.no_active_realms");
            return 0;
        }

        PlayerReference actor = source.player().orElse(null);
        java.util.UUID actorId = actor == null ? new java.util.UUID(0, 0) : actor.uuid();
        String actorName = actor == null ? source.name() : actor.name();
        int queued = 0;
        for (long realmId : realmIds) {
            if (runtime.requestAdminBackup(realmId, actorId, actorName).accepted()) {
                queued++;
            }
        }

        int rejected = realmIds.size() - queued;
        source.sendFeedbackKey("commands.backups.queued_all", Map.of(
                "queued", Integer.toString(queued), "total", Integer.toString(realmIds.size())));
        if (rejected > 0) {
            source.sendErrorKey("commands.backups.errors.queued_all_rejected",
                    Map.of("count", Integer.toString(rejected)));
        }
        return queued > 0 ? 1 : 0;
    }

    private static int status(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        var status = runtime.backupStatus();
        source.sendFeedbackKey("commands.backups.status", Map.of(
                "running", Integer.toString(status.runningOperations()),
                "verified", Integer.toString(status.catalogSize()), "queued", Integer.toString(status.queueLength()),
                "locks", Integer.toString(status.activeLocks())));
        source.sendFeedbackKey("commands.backups.active", Map.of("active", status.activeOperation()
                .map(operation -> eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        "commands.backups.active_realm", Map.of("realm_id", Long.toString(operation.realmId()),
                                "state", friendlyState(operation.state()))))
                .orElseGet(() -> eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.none"))));
        source.sendFeedbackKey("commands.backups.next_due", Map.of("time",
                status.nextDue().map(Object::toString).orElseGet(() ->
                        eu.avalanche7.paradigmrealms.message.PlayerMessages.text("common.not_scheduled"))));
        return 1;
    }

    private static int list(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            Optional<Long> realmId) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        List<BackupCatalogEntry> backups = realmId
                .map(runtime::backupsForRealm)
                .orElseGet(runtime::backups);
        source.sendFeedbackKey("commands.backups.catalog_title", Map.of("count", Integer.toString(backups.size())));
        backups.forEach(entry -> source.sendFeedback(adminSummary(entry)));
        return 1;
    }

    private static int info(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String value) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        BackupId backupId = parseId(source, value);
        if (runtime == null || backupId == null) {
            return 0;
        }
        BackupCatalogEntry entry = runtime.backup(backupId).orElse(null);
        if (entry == null) {
            source.sendErrorKey("commands.backups.errors.not_found");
            return 0;
        }
        source.sendFeedback(adminSummary(entry));
        source.sendFeedbackKey("commands.backups.info_integrity", Map.of(
                "integrity", friendlyIntegrity(entry.integrityStatus()), "reason", friendlyReason(entry.reason()),
                "pinned", eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                        entry.pinned() ? "common.yes" : "common.no")));
        source.sendFeedbackKey("commands.backups.info_allocation", Map.of(
                "profile", entry.allocationProfile().toString(), "strategy", entry.strategy().name()));
        source.sendFeedbackKey("commands.backups.info_files",
                Map.of("count", Integer.toString(entry.payloadFileCount())));
        return 1;
    }

    private static int verify(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String value) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        BackupId backupId = parseId(source, value);
        if (runtime == null || backupId == null) {
            return 0;
        }
        source.sendFeedbackKey("commands.backups.verifying", Map.of("backup_id", backupId.value()));
        runtime.verifyBackup(backupId).whenComplete((result, failure) ->
                source.executeOnServerThread(() -> {
                    if (failure != null) {
                        source.sendErrorKey("commands.backups.errors.verify_exception");
                    } else if (result.valid()) {
                        source.sendFeedbackKey("commands.backups.verify_success");
                    } else {
                        source.sendErrorKey("commands.backups.errors.verify_failed",
                                Map.of("failures", String.join("; ", result.failures())));
                    }
                }));
        return 1;
    }

    private static int pin(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String value,
            boolean pinned) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        BackupId backupId = parseId(source, value);
        if (runtime == null || backupId == null) {
            return 0;
        }
        if (!runtime.setBackupPinned(
                backupId,
                pinned,
                actorUuid(source),
                source.name())) {
            source.sendErrorKey("commands.backups.errors.catalog_update_failed");
            return 0;
        }
        source.sendFeedbackKey(pinned ? "commands.backups.pinned" : "commands.backups.unpinned");
        return 1;
    }

    private static int runDue(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        int queued = runtime.runDueBackups();
        source.sendFeedbackKey("commands.backups.due_queued", Map.of("count", Integer.toString(queued)));
        return 1;
    }

    private static int requestDelete(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String value) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        BackupId backupId = parseId(source, value);
        if (runtime == null || backupId == null) {
            return 0;
        }

        var result = runtime.requestBackupDeletion(backupId, actorUuid(source));
        if (result.confirmationToken().isEmpty()) {
            source.sendErrorKey("common.detail", Map.of("detail", result.message()));
            return 0;
        }
        source.sendFeedbackKey("common.detail", Map.of("detail", result.message()));
        source.sendFeedbackKey("commands.backups.delete_confirm",
                Map.of("token", result.confirmationToken().orElseThrow()));
        return 1;
    }

    private static int confirmDelete(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String token) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }

        var result = runtime.confirmBackupDeletion(token, actorUuid(source));
        if (!result.successful()) {
            source.sendErrorKey("common.detail", Map.of("detail", result.message()));
            return 0;
        }
        source.sendFeedbackKey("common.detail", Map.of("detail", result.message()));
        return 1;
    }

    private static int prepareRestore(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String value) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        BackupId backupId = parseId(source, value);
        if (runtime == null || backupId == null) {
            return 0;
        }

        PlayerReference actor = source.player().orElse(null);
        java.util.UUID actorId = actor == null ? new java.util.UUID(0, 0) : actor.uuid();
        String actorName = actor == null ? source.name() : actor.name();
        source.sendFeedbackKey("commands.backups.restore_preparing");
        runtime.prepareBackupRestore(
                        backupId,
                        eu.avalanche7.paradigmrealms.backup.RestoreMode.WORLD_ONLY,
                        actorId,
                        actorName)
                .whenComplete((result, failure) -> source.executeOnServerThread(() -> {
                    if (failure != null) {
                        source.sendErrorKey("commands.backups.errors.restore_prepare_failed");
                        return;
                    }
                    if (result.status()
                            != eu.avalanche7.paradigmrealms.backup.RestorePreparationResult.Status.PREPARED) {
                        source.sendErrorKey("common.detail", Map.of("detail", result.message()));
                        return;
                    }
                    source.sendFeedbackKey("common.detail", Map.of("detail", result.message()));
                    source.sendFeedbackKey("commands.backups.operation_id",
                            Map.of("operation_id", result.operationId().orElseThrow().toString()));
                    source.sendFeedbackKey("commands.backups.restore_tool_instruction");
                }));
        return 1;
    }

    private static int cancelRestore(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            String value) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        BackupId backupId = parseId(source, value);
        if (runtime == null || backupId == null) {
            return 0;
        }
        if (!runtime.cancelBackupRestore(backupId)) {
            source.sendErrorKey("commands.backups.errors.restore_cancel_missing");
            return 0;
        }
        source.sendFeedbackKey("commands.backups.restore_cancelled");
        return 1;
    }

    private static int prune(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier,
            boolean run) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        var result = run
                ? runtime.runBackupPrune(actorUuid(source), source.name())
                : runtime.previewBackupPrune();
        source.sendFeedbackKey(run ? "commands.backups.pruned" : "commands.backups.prune_preview", Map.of(
                "count", Integer.toString(result.selected().size()),
                "size", humanBytes(result.reclaimableBytes())));
        result.selected().forEach(entry -> source.sendFeedbackKey("commands.backups.prune_line", Map.of(
                "backup_id", entry.backupId().value(), "realm_id", Long.toString(entry.realmId()),
                "created_at", entry.createdAt().toString())));
        if (!result.storageLimitsSatisfied()) {
            source.sendErrorKey("commands.backups.errors.storage_limits");
        }
        return 1;
    }

    private static int catalogValidate(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        source.sendFeedbackKey("commands.backups.catalog_validated",
                Map.of("count", Integer.toString(runtime.backups().size())));
        return 1;
    }

    private static int catalogRebuild(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = requireRuntime(source, supplier);
        if (runtime == null) {
            return 0;
        }
        var result = runtime.rebuildBackupCatalog(actorUuid(source), source.name());
        if (!result.successful()) {
            source.sendErrorKey("commands.backups.errors.catalog_rebuild_failed");
            return 0;
        }
        source.sendFeedbackKey("commands.backups.catalog_rebuilt", Map.of(
                "backups", Integer.toString(result.catalogEntries()),
                "archives", Integer.toString(result.scannedArchives())));
        result.warnings().forEach(warning -> source.sendErrorKey("commands.backups.catalog_warning",
                Map.of("warning", warning)));
        return 1;
    }

    private static int reportRequest(CommandSource source, BackupRequestResult result) {
        if (!result.accepted()) {
            source.sendErrorKey("common.detail", Map.of("detail", result.message()));
            result.cooldownRemaining().ifPresent(remaining ->
                    source.sendFeedbackKey("commands.backups.try_again",
                            Map.of("duration", duration(remaining))));
            return 0;
        }
        source.sendFeedbackKey("common.detail", Map.of("detail", result.message()));
        source.sendFeedbackKey("commands.backups.queue_position",
                Map.of("position", Integer.toString(result.queuePosition())));
        return 1;
    }

    private static int reportAdminRequest(
            CommandSource source,
            BackupRequestResult result,
            String target) {
        if (!result.accepted()) {
            source.sendErrorKey("common.detail", Map.of("detail", result.message()));
            return 0;
        }
        source.sendFeedbackKey("commands.backups.admin_queued", Map.of(
                "target", target, "position", Integer.toString(result.queuePosition())));
        return 1;
    }

    private static BackupId parseId(CommandSource source, String value) {
        try {
            return new BackupId(value);
        } catch (IllegalArgumentException exception) {
            source.sendErrorKey("commands.backups.errors.invalid_id");
            return null;
        }
    }

    private static String playerSummary(BackupCatalogEntry entry) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.backups.player_summary", Map.of(
                "created_at", entry.createdAt().toString(), "reason", friendlyReason(entry.reason()),
                "size", humanBytes(entry.sizeBytes())));
    }

    private static String adminSummary(BackupCatalogEntry entry) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text("commands.backups.admin_summary", Map.of(
                "backup_id", entry.backupId().value(), "realm_id", Long.toString(entry.realmId()),
                "owner", entry.ownerNameSnapshot(), "created_at", entry.createdAt().toString(),
                "size", humanBytes(entry.sizeBytes())));
    }

    private static String friendlyState(eu.avalanche7.paradigmrealms.backup.BackupLifecycleState state) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                "backup_states." + state.name().toLowerCase(Locale.ROOT));
    }

    private static String friendlyIntegrity(
            eu.avalanche7.paradigmrealms.backup.BackupIntegrityStatus status) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                "backup_integrity." + status.name().toLowerCase(Locale.ROOT));
    }

    private static String friendlyReason(eu.avalanche7.paradigmrealms.backup.BackupReason reason) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                "backup_reasons." + reason.name().toLowerCase(Locale.ROOT));
    }

    private static String duration(Duration value) {
        long minutes = Math.max(1, value.toMinutes());
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                minutes == 1 ? "common.minute" : "common.minutes",
                Map.of("minutes", Long.toString(minutes)));
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                    "common.bytes", Map.of("value", Long.toString(bytes)));
        }
        if (bytes < 1024L * 1024L) {
            return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                    "common.kibibytes", Map.of("value", String.format(Locale.ROOT, "%.1f", bytes / 1024.0)));
        }
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(
                "common.mebibytes", Map.of("value",
                        String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0))));
    }

    private static RealmBackupCommandRuntime requireRuntime(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = supplier.get();
        if (runtime == null) {
            source.sendErrorKey("errors.startup_incomplete");
        }
        return runtime;
    }

    private static PlayerReference requirePlayer(CommandSource source) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) {
            source.sendErrorKey("errors.player_only");
        }
        return player;
    }

    private static java.util.UUID actorUuid(CommandSource source) {
        return source.player()
                .map(PlayerReference::uuid)
                .orElse(new java.util.UUID(0, 0));
    }

    private static boolean allowed(
            CommandSource source,
            CommandPermissionGate permissions,
            RealmPermissionNode permission) {
        return permissions.allowed(source, permission);
    }
}
