package eu.avalanche7.paradigmrealms.modules.command;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
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
                                                context.longValue("realmId"))))))
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
        source.sendFeedback("Verified backups for your realm: " + backups.size());
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
        return reportRequest(source, runtime.requestAdminBackup(realmId, actorId, actorName));
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
            source.sendError("There are no active realms available for backup.");
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
        source.sendFeedback("Queued backups for " + queued + " of " + realmIds.size() + " active realms.");
        if (rejected > 0) {
            source.sendError(rejected + " realms were busy or the backup queue was full. Check backup status and retry.");
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
        source.sendFeedback("Realm backups: " + status.catalogSize() + " verified, "
                + status.queueLength() + " queued, " + status.activeLocks() + " capture locks");
        source.sendFeedback("Active: " + status.activeOperation()
                .map(operation -> "realm " + operation.realmId() + " (" + friendlyState(operation.state()) + ")")
                .orElse("none"));
        source.sendFeedback("Next automatic backup due: "
                + status.nextDue().map(Object::toString).orElse("not scheduled"));
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
        source.sendFeedback("Backup catalog entries: " + backups.size());
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
            source.sendError("No backup exists with that ID.");
            return 0;
        }
        source.sendFeedback(adminSummary(entry));
        source.sendFeedback("Integrity: " + friendlyIntegrity(entry.integrityStatus())
                + " | reason: " + friendlyReason(entry.reason())
                + " | pinned: " + (entry.pinned() ? "yes" : "no"));
        source.sendFeedback("Captured storage records: " + entry.chunkCounts().values().stream()
                .mapToInt(Integer::intValue).sum());
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
        source.sendFeedback("Verifying backup " + backupId.value() + "...");
        runtime.verifyBackup(backupId).whenComplete((result, failure) ->
                source.executeOnServerThread(() -> {
                    if (failure != null) {
                        source.sendError("The backup could not be verified. Check the server log for details.");
                    } else if (result.valid()) {
                        source.sendFeedback("Backup verification completed successfully.");
                    } else {
                        source.sendError("Backup verification failed: " + String.join("; ", result.failures()));
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
            source.sendError("The backup could not be found or the catalog update failed.");
            return 0;
        }
        source.sendFeedback(pinned
                ? "Backup pinned. Automatic retention will preserve it."
                : "Backup unpinned. Normal retention rules now apply.");
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
        source.sendFeedback("Queued " + queued + " due realm backup(s).");
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
            source.sendError(result.message());
            return 0;
        }
        source.sendFeedback(result.message());
        source.sendFeedback("/realms admin backups delete confirm "
                + result.confirmationToken().orElseThrow());
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
            source.sendError(result.message());
            return 0;
        }
        source.sendFeedback(result.message());
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
        source.sendFeedback("Verifying the backup and creating a rollback backup of the current realm...");
        runtime.prepareBackupRestore(
                        backupId,
                        eu.avalanche7.paradigmrealms.backup.RestoreMode.WORLD_ONLY,
                        actorId,
                        actorName)
                .whenComplete((result, failure) -> source.executeOnServerThread(() -> {
                    if (failure != null) {
                        source.sendError("Restore preparation failed safely. The target realm was not changed.");
                        return;
                    }
                    if (result.status()
                            != eu.avalanche7.paradigmrealms.backup.RestorePreparationResult.Status.PREPARED) {
                        source.sendError(result.message());
                        return;
                    }
                    source.sendFeedback(result.message());
                    source.sendFeedback("Operation ID: " + result.operationId().orElseThrow());
                    source.sendFeedback("Run the documented realm-backup-tool command only after the server stops.");
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
            source.sendError("No prepared restore for that backup can be cancelled safely.");
            return 0;
        }
        source.sendFeedback("Prepared restore cancelled. The realm is open again.");
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
        source.sendFeedback((run ? "Pruned " : "Would prune ")
                + result.selected().size() + " backup(s), reclaiming "
                + humanBytes(result.reclaimableBytes()) + '.');
        result.selected().forEach(entry -> source.sendFeedback(
                entry.backupId().value() + " | realm " + entry.realmId()
                        + " | " + entry.createdAt()));
        if (!result.storageLimitsSatisfied()) {
            source.sendError("Storage limits cannot be satisfied without deleting protected backups.");
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
        source.sendFeedback("Catalog contains " + runtime.backups().size()
                + " indexed backup(s). Use verify <backupId> for archive integrity.");
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
            source.sendError("The backup catalog could not be rebuilt. Existing archives were not deleted.");
            return 0;
        }
        source.sendFeedback("Backup catalog rebuilt: " + result.catalogEntries()
                + " valid backup(s) from " + result.scannedArchives() + " archive(s).");
        result.warnings().forEach(warning -> source.sendError("Catalog warning: " + warning));
        return 1;
    }

    private static int reportRequest(CommandSource source, BackupRequestResult result) {
        if (!result.accepted()) {
            source.sendError(result.message());
            result.cooldownRemaining().ifPresent(remaining ->
                    source.sendFeedback("Try again in " + duration(remaining) + '.'));
            return 0;
        }
        source.sendFeedback(result.message());
        source.sendFeedback("Queue position: " + result.queuePosition());
        return 1;
    }

    private static BackupId parseId(CommandSource source, String value) {
        try {
            return new BackupId(value);
        } catch (IllegalArgumentException exception) {
            source.sendError("That backup ID is not valid.");
            return null;
        }
    }

    private static String playerSummary(BackupCatalogEntry entry) {
        return entry.createdAt() + " | " + friendlyReason(entry.reason())
                + " | " + humanBytes(entry.sizeBytes());
    }

    private static String adminSummary(BackupCatalogEntry entry) {
        return entry.backupId().value() + " | realm " + entry.realmId()
                + " | " + entry.ownerNameSnapshot() + " | " + entry.createdAt()
                + " | " + humanBytes(entry.sizeBytes());
    }

    private static String friendlyState(eu.avalanche7.paradigmrealms.backup.BackupLifecycleState state) {
        return state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String friendlyIntegrity(
            eu.avalanche7.paradigmrealms.backup.BackupIntegrityStatus status) {
        return status.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String friendlyReason(eu.avalanche7.paradigmrealms.backup.BackupReason reason) {
        return reason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String duration(Duration value) {
        long minutes = Math.max(1, value.toMinutes());
        return minutes == 1 ? "1 minute" : minutes + " minutes";
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    private static RealmBackupCommandRuntime requireRuntime(
            CommandSource source,
            Supplier<? extends RealmBackupCommandRuntime> supplier) {
        RealmBackupCommandRuntime runtime = supplier.get();
        if (runtime == null) {
            source.sendError("Paradigm Realms has not completed server startup.");
        }
        return runtime;
    }

    private static PlayerReference requirePlayer(CommandSource source) {
        PlayerReference player = source.player().orElse(null);
        if (player == null) {
            source.sendError("This command can only be used by a player.");
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
