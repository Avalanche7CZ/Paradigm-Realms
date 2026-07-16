package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.backup.BackupActor;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupDeleteConfirmationService;
import eu.avalanche7.paradigmrealms.backup.BackupDeletionResult;
import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupFailure;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupLifecycleState;
import eu.avalanche7.paradigmrealms.backup.BackupOperation;
import eu.avalanche7.paradigmrealms.backup.BackupQueuePolicy;
import eu.avalanche7.paradigmrealms.backup.BackupReason;
import eu.avalanche7.paradigmrealms.backup.BackupRequestResult;
import eu.avalanche7.paradigmrealms.backup.BackupStatusSnapshot;
import eu.avalanche7.paradigmrealms.backup.RealmBackupConfig;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditEvent;
import eu.avalanche7.paradigmrealms.operations.OperationalAuditSink;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessenger;
import eu.avalanche7.paradigmrealms.platform.protection.RealmPresenceService;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public final class FabricRealmBackupService implements AutoCloseable {
    private static final int SCHEDULER_INTERVAL_TICKS = 20 * 30;

    private final MinecraftServer server;
    private final RealmRepository realms;
    private final RealmBackupConfig config;
    private final RealmBackupMutationLocks locks;
    private final OperationalAuditSink audit;
    private final Clock clock;
    private final FabricBackupPaths paths;
    private final FabricBackupOperationStore operations;
    private final RealmBackupSnapshotFactory snapshots;
    private final BackupQueuePolicy queue;
    private final ExecutorService fileExecutor;
    private final ExecutorService captureExecutor;
    private final FabricBackupStorageAccess storage;
    private final FabricBackupCatalogService catalog;
    private final FabricAutomaticBackupScheduler scheduler;
    private final FabricBackupNotifier notifier;
    private final FabricBackupPackager packager;
    private final FabricRestoreCoordinator restores;
    private final BackupDeleteConfirmationService deleteConfirmations;
    private final Map<Long, BackupOperation> queuedOperations = new HashMap<>();
    private final Map<BackupId, BackupOperation> packagingOperations = new HashMap<>();
    private final Map<UUID, Instant> lastPlayerRequests = new HashMap<>();
    private final Map<BackupId, CompletionHandler> completionHandlers = new HashMap<>();
    private final String worldIdentity;

    private BackupOperation activeOperation;
    private int schedulerTicks;
    private boolean closed;

    public FabricRealmBackupService(
            MinecraftServer server,
            RealmRepository realms,
            RealmBackupConfig config,
            RealmBackupMutationLocks locks,
            OperationalAuditSink audit,
            RealmPresenceService presence,
            CommandMessenger messages) throws IOException {
        this.server = server;
        this.realms = realms;
        this.config = config;
        this.locks = locks;
        this.audit = audit;
        this.clock = Clock.systemUTC();
        this.paths = new FabricBackupPaths(server);
        this.operations = new FabricBackupOperationStore(paths.operationDirectory());
        this.snapshots = new RealmBackupSnapshotFactory(server);
        this.queue = new BackupQueuePolicy(
                config.maximumQueueLength(),
                config.manual().maximumQueuedPlayerRequests());
        this.fileExecutor = Executors.newFixedThreadPool(
                config.maximumPackagingJobs(),
                backupThreadFactory("package"));
        this.captureExecutor = Executors.newFixedThreadPool(
                2,
                backupThreadFactory("capture"));
        this.storage = new FabricBackupStorageAccess(server, captureExecutor);
        this.catalog = new FabricBackupCatalogService(paths, config, clock);
        Instant schedulerEnabledAt = FabricBackupScheduleState.loadOrCreate(
                paths.scheduleStateFile(),
                clock);
        this.scheduler = new FabricAutomaticBackupScheduler(
                realms,
                config,
                catalog,
                clock,
                schedulerEnabledAt);
        this.notifier = new FabricBackupNotifier(server, config, messages, this::realmsWorld);
        this.worldIdentity = FabricWorldIdentity.loadOrCreate(paths.worldIdentityFile());
        this.packager = new FabricBackupPackager(paths, config, worldIdentity);
        this.deleteConfirmations = new BackupDeleteConfirmationService(clock);
        this.restores = new FabricRestoreCoordinator(
                server,
                realms,
                paths,
                catalog,
                locks,
                presence,
                clock,
                worldIdentity,
                fileExecutor,
                audit,
                (realm, reason, actor, completion) -> request(
                        realm, reason, actor, completion));
        recoverOperations();
        quarantineTemporaryArchives();
    }

    public BackupRequestResult request(Realm realm, BackupReason reason, BackupActor actor) {
        return request(realm, reason, actor, null);
    }

    public BackupRequestResult requestRequiredBackup(
            Realm realm,
            BackupReason reason,
            BackupActor actor,
            java.util.function.Consumer<Boolean> completion) {
        return request(
                realm,
                reason,
                actor,
                (successful, entry) -> completion.accept(successful));
    }

    BackupRequestResult request(
            Realm realm,
            BackupReason reason,
            BackupActor actor,
            CompletionHandler completionHandler) {
        if (!config.enabled()) {
            return BackupRequestResult.rejected(BackupFailure.CANCELLED, "Realm backups are disabled.");
        }
        if (!eligible(realm, reason)) {
            return BackupRequestResult.rejected(
                    realm.state() == RealmLifecycleState.ACTIVE
                            ? BackupFailure.REALM_BUSY
                            : BackupFailure.REALM_NOT_ACTIVE,
                    "This realm is not available for a backup right now.");
        }
        if (realmInProgress(realm.id().value())) {
            return BackupRequestResult.rejected(
                    BackupFailure.REALM_BUSY,
                    BackupFailure.REALM_BUSY.playerMessage());
        }
        if (reason == BackupReason.PLAYER_REQUESTED) {
            BackupRequestResult cooldown = checkPlayerCooldown(actor);
            if (cooldown != null) {
                return cooldown;
            }
        }

        Instant now = clock.instant();
        BackupQueuePolicy.Request request = new BackupQueuePolicy.Request(
                realm.id().value(),
                reason,
                actor,
                now,
                0,
                actor.uuid());
        BackupQueuePolicy.Decision decision = queue.offer(request);
        if (decision.status() != BackupQueuePolicy.Status.QUEUED) {
            return queueRejection(decision);
        }

        BackupOperation operation = newOperation(request);
        try {
            operations.save(operation);
        } catch (IOException exception) {
            queue.remove(realm.id().value());
            return BackupRequestResult.rejected(
                    BackupFailure.INTERNAL_ERROR,
                    BackupFailure.INTERNAL_ERROR.playerMessage());
        }

        queuedOperations.put(realm.id().value(), operation);
        if (completionHandler != null) {
            completionHandlers.put(operation.backupId(), completionHandler);
        }
        actor.uuid().filter(ignored -> reason == BackupReason.PLAYER_REQUESTED)
                .ifPresent(uuid -> lastPlayerRequests.put(uuid, now));
        audit(operation, "BACKUP_QUEUED", "QUEUED", Map.of("reason", reason.name()), true);
        return BackupRequestResult.queued(operation.backupId(), decision.position());
    }

    public void tick() {
        if (closed || !config.enabled()) {
            return;
        }

        restores.tick();

        if (++schedulerTicks >= SCHEDULER_INTERVAL_TICKS) {
            schedulerTicks = 0;
            scheduleDueRealms();
        }

        if (activeOperation == null && hasConcurrentCapacity()) {
            queue.poll().ifPresent(this::beginCapture);
        }
    }

    public BackupStatusSnapshot status() {
        return new BackupStatusSnapshot(
                queue.size(),
                visibleActiveOperation(),
                scheduler.nextDue(),
                catalog.size(),
                locks.activeCount());
    }

    public List<BackupCatalogEntry> list() {
        return catalog.list();
    }

    public List<BackupCatalogEntry> listForRealm(long realmId) {
        return catalog.list().stream()
                .filter(entry -> entry.realmId() == realmId)
                .toList();
    }

    public List<BackupCatalogEntry> listForOwner(UUID owner) {
        return catalog.list().stream()
                .filter(entry -> entry.ownerUuid().equals(owner))
                .toList();
    }

    public Optional<BackupCatalogEntry> find(BackupId backupId) {
        return catalog.find(backupId);
    }

    public synchronized boolean pin(
            BackupId backupId,
            boolean pinned,
            UUID actor,
            String actorName) throws IOException {
        boolean changed = catalog.pin(backupId, pinned);
        if (changed) {
            catalog.find(backupId).ifPresent(entry -> auditCatalog(
                    entry,
                    pinned ? "BACKUP_PINNED" : "BACKUP_UNPINNED",
                    Optional.of(actor),
                    Optional.of(actorName),
                    Map.of(),
                    true));
        }
        return changed;
    }

    public BackupDeletionResult requestDeletion(BackupId backupId, UUID actorUuid) {
        BackupCatalogEntry entry = catalog.find(backupId).orElse(null);
        if (entry == null) {
            return BackupDeletionResult.failed("No backup exists with that ID.");
        }
        if (entry.pinned()) {
            return BackupDeletionResult.failed("Unpin this backup before deleting it.");
        }
        if (entry.restoreInUse()) {
            return BackupDeletionResult.failed("This backup is required by an active restore operation.");
        }
        return BackupDeletionResult.confirmation(
                deleteConfirmations.issue(actorUuid, backupId));
    }

    public BackupDeletionResult confirmDeletion(String token, UUID actorUuid) {
        BackupId backupId = deleteConfirmations.consume(actorUuid, token).orElse(null);
        if (backupId == null) {
            return BackupDeletionResult.failed("That backup deletion confirmation is invalid or has expired.");
        }
        try {
            BackupCatalogEntry entry = catalog.find(backupId).orElse(null);
            if (!catalog.delete(backupId)) {
                return BackupDeletionResult.failed(
                        "The backup is missing, pinned, or currently required by a restore.");
            }
            if (entry != null) {
                auditCatalog(
                        entry,
                        "BACKUP_MANUALLY_DELETED",
                        Optional.of(actorUuid),
                        Optional.empty(),
                        Map.of(),
                        true);
            }
            return BackupDeletionResult.completed();
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.error("Backup deletion failed: {}", exception.getMessage());
            return BackupDeletionResult.failed(
                    "The backup could not be deleted. Existing catalog data was preserved where possible.");
        }
    }

    public CompletableFuture<eu.avalanche7.paradigmrealms.backup.BackupVerificationResult> verify(
            BackupId backupId) {
        return catalog.verify(backupId, fileExecutor);
    }

    public int runDueNow() {
        return scheduleDueRealms();
    }

    public CompletableFuture<eu.avalanche7.paradigmrealms.backup.RestorePreparationResult>
            prepareRestore(
                    BackupId backupId,
                    eu.avalanche7.paradigmrealms.backup.RestoreMode mode,
                    BackupActor actor) {
        return restores.prepare(backupId, mode, actor);
    }

    public boolean cancelRestore(BackupId backupId) {
        return restores.cancel(backupId);
    }

    public eu.avalanche7.paradigmrealms.backup.BackupPruneResult previewPrune()
            throws IOException {
        var plan = catalog.previewPrune(activeRealmIds());
        return pruneResult(plan, false);
    }

    public eu.avalanche7.paradigmrealms.backup.BackupPruneResult runPrune(
            UUID actor,
            String actorName)
            throws IOException {
        var plan = catalog.prune(activeRealmIds());
        auditMaintenance(
                "BACKUP_MANUAL_PRUNE",
                Optional.of(actor),
                Optional.of(actorName),
                Map.of(
                        "deleted", Integer.toString(plan.deletions().size()),
                        "reclaimedBytes", Long.toString(plan.reclaimableBytes())),
                true);
        return pruneResult(plan, true);
    }

    public eu.avalanche7.paradigmrealms.backup.BackupCatalogRepairResult rebuildCatalog(
            UUID actor,
            String actorName)
            throws IOException {
        var result = catalog.rebuild();
        auditMaintenance(
                "BACKUP_CATALOG_REBUILT",
                Optional.of(actor),
                Optional.of(actorName),
                Map.of(
                        "entries", Integer.toString(result.catalog().size()),
                        "scannedArchives", Integer.toString(result.scannedArchives()),
                        "warnings", Integer.toString(result.warnings().size())),
                true);
        return new eu.avalanche7.paradigmrealms.backup.BackupCatalogRepairResult(
                true,
                result.catalog().size(),
                result.scannedArchives(),
                result.warnings());
    }

    @Override
    public void close() {
        closed = true;
        locks.clear();
        captureExecutor.shutdown();
        fileExecutor.shutdown();
    }

    private void beginCapture(BackupQueuePolicy.Request request) {
        BackupOperation queued = queuedOperations.remove(request.realmId());
        Realm realm = realms.findById(new RealmId(request.realmId())).orElse(null);
        if (queued == null || realm == null) {
            if (queued != null) {
                fail(queued, BackupFailure.REALM_NOT_FOUND, "realm disappeared before capture");
            }
            return;
        }
        if (!eligible(realm, request.reason())) {
            retryOrFail(request, queued, BackupFailure.REALM_BUSY, "realm became busy before capture");
            return;
        }
        if (!hasEnoughFreeSpace()) {
            fail(queued, BackupFailure.INSUFFICIENT_SPACE, "minimum free-space requirement was not met");
            return;
        }

        BackupCellBounds bounds = BackupCellBounds.from(realm.allocation().cellBounds());
        activeOperation = transition(queued, BackupLifecycleState.LOCKING);
        Optional<RealmBackupMutationLocks.Handle> handle = locks.tryAcquire(
                realm.id().value(),
                bounds,
                queued.operationId());
        if (handle.isEmpty()) {
            retryOrFail(request, queued, BackupFailure.REALM_BUSY, "realm backup lock is already held");
            activeOperation = null;
            return;
        }

        notifier.captureStarted(realm);
        FabricBackupCaptureContext context;
        try {
            context = captureContext(realm, queued, bounds, handle.orElseThrow());
        } catch (IOException | RuntimeException exception) {
            handle.orElseThrow().close();
            fail(queued, BackupFailure.CAPTURE_FAILED, exception.getMessage());
            activeOperation = null;
            return;
        }

        activeOperation = transition(activeOperation, BackupLifecycleState.FLUSHING);
        activeOperation = transition(activeOperation, BackupLifecycleState.CAPTURING);
        audit(activeOperation, "BACKUP_CAPTURE_STARTED", "STARTED", Map.of(), false);

        storage.capture(
                        context.world(),
                        bounds,
                        context.stagingDirectory(),
                        config.captureTimeout(),
                        (captured, total) -> notifier.progress(realm, captured, total))
                .whenComplete((captured, failure) -> server.execute(() ->
                        finishCapture(context, captured, failure)));
    }

    private FabricBackupCaptureContext captureContext(
            Realm realm,
            BackupOperation operation,
            BackupCellBounds bounds,
            RealmBackupMutationLocks.Handle lock) throws IOException {
        ServerWorld world = realmsWorld();
        String ownerName = snapshots.ownerName(realm);
        var metadata = snapshots.create(realm, ownerName);
        Path staging = paths.stagingDirectory(operation.stagingRelativePath());
        return new FabricBackupCaptureContext(
                realm, operation, bounds, ownerName, metadata, staging, world, lock);
    }

    private void finishCapture(
            FabricBackupCaptureContext context,
            FabricBackupStorageAccess.CapturedChunks captured,
            Throwable failure) {
        context.lock().close();
        if (failure != null) {
            fail(context.operation(), BackupFailure.CAPTURE_FAILED, rootMessage(failure));
            activeOperation = null;
            notifier.failed(context.realm(), context.operation().reason());
            return;
        }

        BackupOperation packaging = transition(activeOperation, BackupLifecycleState.PACKAGING);
        packagingOperations.put(packaging.backupId(), packaging);
        activeOperation = null;
        CompletableFuture
                .supplyAsync(() -> packageBackup(context, captured), fileExecutor)
                .whenComplete((result, packageFailure) -> server.execute(() ->
                        finishPackaging(context, packaging, captured, result, packageFailure)));
    }

    private FabricBackupPackager.PackagedBackup packageBackup(
            FabricBackupCaptureContext context,
            FabricBackupStorageAccess.CapturedChunks captured) {
        try {
            return packager.packageBackup(context, captured);
        } catch (IOException | RuntimeException exception) {
            throw new BackupPackagingException(exception);
        }
    }

    private void finishPackaging(
            FabricBackupCaptureContext context,
            BackupOperation operation,
            FabricBackupStorageAccess.CapturedChunks captured,
            FabricBackupPackager.PackagedBackup packaged,
            Throwable failure) {
        if (failure != null) {
            fail(operation, BackupFailure.PACKAGE_FAILED, rootMessage(failure));
            notifier.failed(context.realm(), operation.reason());
            packagingOperations.remove(operation.backupId());
            cleanupStaging(context.stagingDirectory());
            return;
        }

        BackupOperation verifying = transition(operation, BackupLifecycleState.VERIFYING);
        try {
            BackupCatalogEntry entry = packager.catalogEntry(packaged);
            catalog.add(entry);
            BackupOperation completed = transition(verifying, BackupLifecycleState.COMPLETED);
            audit(completed, "BACKUP_COMPLETED", "COMPLETED", Map.of(
                    "sizeBytes", Long.toString(entry.sizeBytes()),
                    "chunks", Integer.toString(packaged.manifest().totalChunkCount()),
                    "captureMillis", Long.toString(captured.captureDuration().toMillis())), true);
            notifier.completed(context.realm(), entry, captured.captureDuration());
            complete(completed.backupId(), true, Optional.of(entry));
            prune(false);
        } catch (IOException | RuntimeException exception) {
            fail(verifying, BackupFailure.CATALOG_FAILED, exception.getMessage());
            notifier.failed(context.realm(), operation.reason());
        } finally {
            packagingOperations.remove(operation.backupId());
            cleanupStaging(context.stagingDirectory());
        }
    }

    private int scheduleDueRealms() {
        int queued = 0;
        Set<Long> busy = new java.util.HashSet<>(queuedOperations.keySet());
        if (activeOperation != null) {
            busy.add(activeOperation.realmId());
        }
        packagingOperations.values().stream()
                .map(BackupOperation::realmId)
                .forEach(busy::add);
        for (Realm realm : scheduler.due(busy)) {
            if (request(realm, BackupReason.AUTOMATIC, BackupActor.system()).accepted()) {
                queued++;
            }
        }
        return queued;
    }

    private void retryOrFail(
            BackupQueuePolicy.Request request,
            BackupOperation operation,
            BackupFailure failure,
            String detail) {
        if (request.attempt() < config.maximumRetries()) {
            BackupQueuePolicy.Request retry = request.retry(clock.instant());
            BackupQueuePolicy.Decision decision = queue.offer(retry);
            if (decision.status() == BackupQueuePolicy.Status.QUEUED) {
                queuedOperations.put(request.realmId(), operation);
                return;
            }
        }
        fail(operation, failure, detail);
    }

    private boolean eligible(Realm realm, BackupReason reason) {
        if (realm.lifecycleOperation().isPresent()
                || !realm.dimension().equals(DimensionId.REALMS)) {
            return false;
        }
        if (realm.state() == RealmLifecycleState.ACTIVE) {
            return true;
        }
        return reason == BackupReason.AUTOMATIC
                && realm.state() == RealmLifecycleState.ARCHIVED
                && !config.automatic().activeRealmsOnly()
                && config.automatic().includeArchivedRealms();
    }

    private boolean realmInProgress(long realmId) {
        if (activeOperation != null && activeOperation.realmId() == realmId) {
            return true;
        }
        return packagingOperations.values().stream()
                .anyMatch(operation -> operation.realmId() == realmId);
    }

    private boolean hasConcurrentCapacity() {
        return packagingOperations.size()
                < config.automatic().maximumConcurrentBackups();
    }

    private Optional<BackupOperation> visibleActiveOperation() {
        if (activeOperation != null) {
            return Optional.of(activeOperation);
        }
        return packagingOperations.values().stream().findFirst();
    }

    private BackupRequestResult checkPlayerCooldown(BackupActor actor) {
        if (!config.manual().allowPlayerSelfBackup()) {
            return BackupRequestResult.rejected(
                    BackupFailure.CANCELLED,
                    "Player-requested realm backups are disabled.");
        }
        UUID player = actor.uuid().orElse(null);
        if (player == null) {
            return BackupRequestResult.rejected(
                    BackupFailure.INTERNAL_ERROR,
                    BackupFailure.INTERNAL_ERROR.playerMessage());
        }
        Instant next = Optional.ofNullable(lastPlayerRequests.get(player))
                .map(previous -> previous.plus(config.manual().playerCooldown()))
                .orElse(Instant.MIN);
        if (next.isAfter(clock.instant())) {
            return new BackupRequestResult(
                    false,
                    Optional.empty(),
                    -1,
                    Optional.of(BackupFailure.COOLDOWN_ACTIVE),
                    BackupFailure.COOLDOWN_ACTIVE.playerMessage(),
                    Optional.of(Duration.between(clock.instant(), next)));
        }
        return null;
    }

    private BackupRequestResult queueRejection(BackupQueuePolicy.Decision decision) {
        BackupFailure failure = decision.status() == BackupQueuePolicy.Status.DUPLICATE
                ? BackupFailure.REALM_BUSY
                : BackupFailure.QUEUE_FULL;
        return BackupRequestResult.rejected(failure, failure.playerMessage());
    }

    private BackupOperation newOperation(BackupQueuePolicy.Request request) {
        UUID operationId = UUID.randomUUID();
        return new BackupOperation(
                operationId,
                BackupId.generate(),
                request.realmId(),
                request.reason(),
                request.actor(),
                BackupLifecycleState.QUEUED,
                request.requestedAt(),
                request.requestedAt(),
                request.attempt(),
                "staging/" + operationId,
                Optional.empty(),
                Optional.empty());
    }

    private BackupOperation transition(BackupOperation operation, BackupLifecycleState state) {
        BackupOperation changed = operation.transition(state, clock.instant());
        try {
            operations.save(changed);
        } catch (IOException exception) {
            throw new IllegalStateException("could not persist backup operation", exception);
        }
        return changed;
    }

    private void fail(BackupOperation operation, BackupFailure failure, String detail) {
        BackupOperation failed = failure == BackupFailure.CANCELLED
                ? operation.cancel(detail, clock.instant())
                : operation.fail(failure, detail, clock.instant());
        try {
            operations.save(failed);
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.error("Could not persist failed backup operation {}", operation.operationId());
        }
        String event = failure == BackupFailure.CANCELLED
                ? "BACKUP_CANCELLED"
                : "BACKUP_FAILED";
        String outcome = failure == BackupFailure.CANCELLED
                ? "CANCELLED"
                : "FAILED";
        audit(failed, event, outcome, Map.of(
                "failure", failure.name(),
                "detail", safeDetail(detail)), true);
        complete(failed.backupId(), false, Optional.empty());
    }

    private void recoverOperations() throws IOException {
        for (BackupOperation operation : operations.load()) {
            if (operation.state().terminal()) {
                continue;
            }
            if (operation.state() == BackupLifecycleState.QUEUED) {
                BackupQueuePolicy.Request request = new BackupQueuePolicy.Request(
                        operation.realmId(),
                        operation.reason(),
                        operation.actor(),
                        operation.createdAt(),
                        operation.attempt(),
                        operation.actor().uuid());
                if (queue.offer(request).status() == BackupQueuePolicy.Status.QUEUED) {
                    queuedOperations.put(operation.realmId(), operation);
                    continue;
                }
            }
            fail(operation, BackupFailure.CANCELLED, "server stopped before the online backup completed");
        }
    }

    private void quarantineTemporaryArchives() throws IOException {
        Path failed = paths.backupRoot().resolve("failed");
        Files.createDirectories(failed);
        try (var pathsInRoot = Files.walk(paths.backupRoot(), 4)) {
            List<Path> temporary = pathsInRoot
                    .filter(Files::isRegularFile)
                    .filter(paths::isTemporaryArchive)
                    .toList();
            for (Path file : temporary) {
                Path target = failed.resolve(UUID.randomUUID() + "-" + file.getFileName());
                Files.move(file, target);
            }
        }
    }

    private boolean hasEnoughFreeSpace() {
        try {
            FileStore store = Files.getFileStore(paths.backupRoot());
            return store.getUsableSpace() >= gibibytes(config.storage().minimumFreeSpaceGiB());
        } catch (IOException exception) {
            return false;
        }
    }

    private void prune(boolean previewOnly) {
        try {
            Set<Long> activeRealms = realms.list().stream()
                    .filter(realm -> realm.state() == RealmLifecycleState.ACTIVE)
                    .map(realm -> realm.id().value())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var plan = previewOnly
                    ? catalog.previewPrune(activeRealms)
                    : catalog.prune(activeRealms);
            if (previewOnly) {
                return;
            }
            if (!plan.deletions().isEmpty()) {
                auditMaintenance(
                        "BACKUP_AUTOMATIC_PRUNE",
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(
                                "deleted", Integer.toString(plan.deletions().size()),
                                "reclaimedBytes", Long.toString(plan.reclaimableBytes())),
                        true);
            }
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.warn("Backup retention failed: {}", exception.getMessage());
        }
    }

    private Set<Long> activeRealmIds() {
        return realms.list().stream()
                .filter(realm -> realm.state() == RealmLifecycleState.ACTIVE)
                .map(realm -> realm.id().value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static eu.avalanche7.paradigmrealms.backup.BackupPruneResult pruneResult(
            eu.avalanche7.paradigmrealms.backup.RetentionPlanner.Plan plan,
            boolean applied) {
        return new eu.avalanche7.paradigmrealms.backup.BackupPruneResult(
                plan.deletions(),
                plan.reclaimableBytes(),
                plan.retentionReasons(),
                plan.storageLimitsSatisfied(),
                applied);
    }

    private ServerWorld realmsWorld() {
        RegistryKey<World> key = RegistryKey.of(
                RegistryKeys.WORLD,
                Identifier.of(DimensionId.REALMS.namespace(), DimensionId.REALMS.path()));
        ServerWorld world = server.getWorld(key);
        if (world == null) {
            throw new IllegalStateException("Realms dimension is not loaded");
        }
        return world;
    }

    private void audit(
            BackupOperation operation,
            String event,
            String outcome,
            Map<String, String> details,
            boolean durable) {
        audit.append(new OperationalAuditEvent(
                1,
                clock.instant(),
                event,
                outcome,
                Optional.of(operation.operationId()),
                operation.actor().uuid(),
                Optional.of(operation.actor().nameSnapshot()),
                Optional.empty(),
                Optional.of(new RealmId(operation.realmId())),
                Optional.empty(),
                details), durable);
    }

    private void auditCatalog(
            BackupCatalogEntry entry,
            String event,
            Optional<UUID> actor,
            Optional<String> actorName,
            Map<String, String> details,
            boolean durable) {
        Map<String, String> values = new HashMap<>(details);
        values.put("backupId", entry.backupId().value());
        values.put("reason", entry.reason().name());
        values.put("sizeBytes", Long.toString(entry.sizeBytes()));
        audit.append(new OperationalAuditEvent(
                1,
                clock.instant(),
                event,
                "COMPLETED",
                Optional.empty(),
                actor,
                actorName,
                Optional.empty(),
                Optional.of(new RealmId(entry.realmId())),
                Optional.empty(),
                values),
                durable);
    }

    private void auditMaintenance(
            String event,
            Optional<UUID> actor,
            Optional<String> actorName,
            Map<String, String> details,
            boolean durable) {
        audit.append(new OperationalAuditEvent(
                1,
                clock.instant(),
                event,
                "COMPLETED",
                Optional.empty(),
                actor,
                actorName,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                details),
                durable);
    }

    private void cleanupStaging(Path root) {
        try {
            if (!root.startsWith(paths.backupRoot()) || Files.isSymbolicLink(root)) {
                return;
            }
            try (var values = Files.walk(root)) {
                values.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        ParadigmRealms.LOGGER.warn("Could not remove backup staging file {}", path.getFileName());
                    }
                });
            }
        } catch (IOException exception) {
            ParadigmRealms.LOGGER.warn("Could not clean backup staging directory: {}", exception.getMessage());
        }
    }

    private static ThreadFactory backupThreadFactory(String role) {
        AtomicInteger number = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(
                    task,
                    "paradigm-realms-backup-" + role + '-' + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static long gibibytes(long value) {
        try {
            return Math.multiplyExact(value, 1024L * 1024L * 1024L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "no additional detail";
        }
        String safe = detail.replace('\n', ' ').replace('\r', ' ').strip();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }

    private static final class BackupPackagingException extends RuntimeException {
        private BackupPackagingException(Throwable cause) {
            super(cause);
        }
    }

    private void complete(
            BackupId backupId,
            boolean successful,
            Optional<BackupCatalogEntry> entry) {
        CompletionHandler handler = completionHandlers.remove(backupId);
        if (handler != null) {
            handler.completed(successful, entry);
        }
    }

    @FunctionalInterface
    interface CompletionHandler {
        void completed(boolean successful, Optional<BackupCatalogEntry> entry);
    }
}
