package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.backup.BackupCatalog;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupIntegrityStatus;
import eu.avalanche7.paradigmrealms.backup.BackupVerificationResult;
import eu.avalanche7.paradigmrealms.backup.RealmBackupConfig;
import eu.avalanche7.paradigmrealms.backup.RetentionPlanner;
import eu.avalanche7.paradigmrealms.backup.io.BackupArchiveVerifier;
import eu.avalanche7.paradigmrealms.backup.io.BackupCatalogFile;

final class FabricBackupCatalogService {
    private static final long GIBIBYTE = 1024L * 1024L * 1024L;

    private final FabricBackupPaths paths;
    private final RealmBackupConfig config;
    private final Clock clock;
    private final BackupCatalogFile catalogFile = new BackupCatalogFile();
    private final BackupArchiveVerifier verifier = new BackupArchiveVerifier();
    private final RetentionPlanner retention = new RetentionPlanner();
    private BackupCatalog catalog;

    FabricBackupCatalogService(
            FabricBackupPaths paths,
            RealmBackupConfig config,
            Clock clock) throws IOException {
        this.paths = paths;
        this.config = config;
        this.clock = clock;
        this.catalog = loadOrRebuild();
    }

    synchronized List<BackupCatalogEntry> list() {
        return catalog.list();
    }

    synchronized Optional<BackupCatalogEntry> find(BackupId backupId) {
        return catalog.find(backupId);
    }

    synchronized int size() {
        return catalog.size();
    }

    synchronized Optional<Instant> latestVerified(long realmId) {
        return catalog.forRealm(realmId).stream()
                .filter(entry -> entry.integrityStatus() == BackupIntegrityStatus.VERIFIED)
                .map(BackupCatalogEntry::createdAt)
                .max(Instant::compareTo);
    }

    synchronized void add(BackupCatalogEntry entry) throws IOException {
        catalog.put(entry);
        catalogFile.save(paths.backupRoot(), catalog);
    }

    synchronized boolean pin(BackupId backupId, boolean pinned) throws IOException {
        BackupCatalogEntry current = catalog.find(backupId).orElse(null);
        if (current == null) {
            return false;
        }
        catalog.put(current.withPinned(pinned));
        catalogFile.save(paths.backupRoot(), catalog);
        return true;
    }

    synchronized boolean markRestoreInUse(BackupId backupId, boolean inUse) throws IOException {
        BackupCatalogEntry current = catalog.find(backupId).orElse(null);
        if (current == null) {
            return false;
        }
        catalog.put(current.withRestoreInUse(inUse));
        catalogFile.save(paths.backupRoot(), catalog);
        return true;
    }

    synchronized boolean delete(BackupId backupId) throws IOException {
        BackupCatalogEntry entry = catalog.find(backupId).orElse(null);
        if (entry == null || entry.pinned() || entry.restoreInUse()) {
            return false;
        }

        Path archive = paths.backupRoot()
                .resolve(entry.archiveRelativePath())
                .normalize();
        if (!archive.startsWith(paths.backupRoot()) || Files.isSymbolicLink(archive)) {
            throw new IOException("catalog archive path is unsafe");
        }

        Files.deleteIfExists(archive);
        Files.deleteIfExists(archive.resolveSibling(archive.getFileName() + ".sha256"));
        catalog.remove(backupId);
        catalogFile.save(paths.backupRoot(), catalog);
        return true;
    }

    CompletableFuture<BackupVerificationResult> verify(BackupId backupId, Executor executor) {
        BackupCatalogEntry entry;
        synchronized (this) {
            entry = catalog.find(backupId).orElse(null);
        }
        if (entry == null) {
            return CompletableFuture.completedFuture(BackupVerificationResult.invalid(
                    List.of("backup ID is not in the catalog"),
                    0));
        }
        Path archive = paths.backupRoot().resolve(entry.archiveRelativePath());
        return CompletableFuture.supplyAsync(() -> verifier.verify(archive), executor);
    }

    synchronized RetentionPlanner.Plan previewPrune(Set<Long> activeRealms) throws IOException {
        FileStore store = Files.getFileStore(paths.backupRoot());
        return retention.plan(
                catalog.list(),
                config.retention(),
                clock.instant(),
                activeRealms,
                bytes(config.storage().maximumTotalSizeGiB()),
                store.getUsableSpace(),
                bytes(config.storage().minimumFreeSpaceGiB()));
    }

    synchronized RetentionPlanner.Plan prune(Set<Long> activeRealms) throws IOException {
        RetentionPlanner.Plan plan = previewPrune(activeRealms);
        for (BackupCatalogEntry entry : plan.deletions()) {
            Path archive = paths.backupRoot()
                    .resolve(entry.archiveRelativePath())
                    .normalize();
            if (!archive.startsWith(paths.backupRoot()) || Files.isSymbolicLink(archive)) {
                continue;
            }
            Files.deleteIfExists(archive);
            Files.deleteIfExists(archive.resolveSibling(archive.getFileName() + ".sha256"));
            catalog.remove(entry.backupId());
        }
        if (!plan.deletions().isEmpty()) {
            catalogFile.save(paths.backupRoot(), catalog);
        }
        return plan;
    }

    synchronized BackupCatalogFile.RebuildResult rebuild() throws IOException {
        BackupCatalogFile.RebuildResult result = catalogFile.rebuild(paths.backupRoot());
        catalog = result.catalog();
        return result;
    }

    private BackupCatalog loadOrRebuild() throws IOException {
        BackupCatalogFile.LoadResult loaded = catalogFile.load(paths.backupRoot());
        if (loaded.warnings().isEmpty()) {
            return loaded.catalog();
        }

        loaded.warnings().forEach(warning ->
                ParadigmRealms.LOGGER.warn("Backup catalog: {}", warning));
        BackupCatalogFile.RebuildResult rebuilt = catalogFile.rebuild(paths.backupRoot());
        rebuilt.warnings().forEach(warning ->
                ParadigmRealms.LOGGER.warn("Backup catalog rebuild: {}", warning));
        return rebuilt.catalog();
    }

    private static long bytes(long gibibytes) {
        try {
            return Math.multiplyExact(gibibytes, GIBIBYTE);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
