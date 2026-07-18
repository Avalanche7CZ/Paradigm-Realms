package eu.avalanche7.paradigmrealms.backup.offline;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import eu.avalanche7.paradigmrealms.backup.BackupCellBounds;
import eu.avalanche7.paradigmrealms.backup.BackupManifest;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.BackupStrategy;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.backup.RestoreManifestStage;
import eu.avalanche7.paradigmrealms.backup.RestoreOperationManifest;
import eu.avalanche7.paradigmrealms.backup.io.BackupArchiveVerifier;
import eu.avalanche7.paradigmrealms.backup.io.BackupPathSafety;
import eu.avalanche7.paradigmrealms.backup.io.RestoreManifestFile;
import eu.avalanche7.paradigmrealms.backup.region.RegionFileRewriter;
import eu.avalanche7.paradigmrealms.backup.region.RegionCopyCapture;

public final class RealmBackupRestoreTool {
    private static final String REALMS_DIMENSION = "paradigm_realms:realms";
    private static final String REALMS_DIMENSION_PATH = "dimensions/paradigm_realms/realms";
    private static final String BACKUP_ROOT = "backups/paradigm-realms";
    private static final String MANIFEST_ROOT = "paradigm-realms/restore-manifests";

    private RealmBackupRestoreTool() {}

    public static void main(String[] arguments) {
        int exitCode = runSafely(arguments);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int runSafely(String[] arguments) {
        try {
            return run(arguments);
        } catch (IllegalArgumentException exception) {
            System.err.println("Realm restore refused: " + exception.getMessage());
            return 2;
        } catch (IOException exception) {
            System.err.println("Realm restore failed safely: " + exception.getMessage());
            return 3;
        } catch (Exception exception) {
            System.err.println("Realm restore failed safely: " + exception.getClass().getSimpleName());
            return 4;
        }
    }

    static int run(String[] arguments) throws Exception {
        Arguments parsed = Arguments.parse(arguments);
        rejectSymlinkChain(parsed.world());
        Path worldRoot = parsed.world().toRealPath(LinkOption.NOFOLLOW_LINKS);

        Path manifestRoot = BackupPathSafety.resolveInside(worldRoot, MANIFEST_ROOT, false);
        Path manifestPath = parsed.manifest().toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!manifestPath.startsWith(manifestRoot) || Files.isSymbolicLink(parsed.manifest())) {
            throw new IOException("restore manifest is outside the bounded manifest directory");
        }

        Path sessionLock = worldRoot.resolve("session.lock");
        try (FileChannel channel = FileChannel.open(
                    sessionLock,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
             FileLock ignored = lockWorld(channel)) {
            return restore(worldRoot, manifestPath);
        }
    }

    private static int restore(Path worldRoot, Path manifestPath) throws IOException {
        RestoreManifestFile manifestFile = new RestoreManifestFile();
        RestoreOperationManifest operation = manifestFile.read(manifestPath);
        validateOperation(worldRoot, operation);

        if (operation.stage() == RestoreManifestStage.OFFLINE_VERIFIED) {
            System.out.println("Realm restore is already offline-verified for operation "
                    + operation.operationId());
            return 0;
        }
        if (!canRunOffline(operation.stage())) {
            throw new IOException("restore manifest stage is " + operation.stage());
        }

        Path backupRoot = BackupPathSafety.resolveInside(worldRoot, BACKUP_ROOT, false);
        Path archive = BackupPathSafety.resolveInside(
                backupRoot,
                operation.archiveRelativePath(),
                false);
        BackupManifest backup = verifyBackup(archive, operation);

        Path staging = worldRoot
                .resolve("paradigm-realms/restore-staging")
                .resolve(operation.operationId().toString());
        BackupPathSafety.rejectSymlinks(worldRoot, staging);
        deleteStagingIfPresent(staging, worldRoot);

        RestoreArchiveExtractor.ExtractedChunks extracted =
                new RestoreArchiveExtractor().extract(archive, backup, staging);
        try {
            if (backup.strategy() == BackupStrategy.REGION_COPY) {
                quarantineRegionFiles(worldRoot, operation);
                operation = operation.withStage(RestoreManifestStage.TARGET_QUARANTINED, Instant.now());
                manifestFile.write(manifestPath, operation);
                replaceRegionFiles(worldRoot, operation, extracted);
            } else {
                rewriteTargetCell(worldRoot, operation, extracted);
            }
            operation = operation.withStage(
                    RestoreManifestStage.REGION_FILES_REWRITTEN,
                    Instant.now());
            manifestFile.write(manifestPath, operation);

            verifyBackup(archive, operation);
            operation = operation.withStage(
                    RestoreManifestStage.OFFLINE_VERIFIED,
                    Instant.now());
            manifestFile.write(manifestPath, operation);
        } finally {
            deleteStagingIfPresent(staging, worldRoot);
        }

        System.out.println("Realm " + operation.realmId()
                + " restored into its original cell. Start the server for runtime verification.");
        return 0;
    }

    private static void quarantineRegionFiles(
            Path worldRoot, RestoreOperationManifest operation) throws IOException {
        Path dimension = BackupPathSafety.resolveInside(worldRoot, operation.dimensionRelativePath(), false);
        Path quarantine = BackupPathSafety.resolveInside(worldRoot, operation.quarantineRelativePath(), true);
        Files.createDirectories(quarantine);
        int regionX = Math.floorDiv(operation.targetBounds().minimumChunkX(), 32);
        int regionZ = Math.floorDiv(operation.targetBounds().minimumChunkZ(), 32);

        for (BackupStorageKind kind : BackupStorageKind.values()) {
            String directoryName = RegionCopyCapture.storageDirectory(kind);
            Path directory = dimension.resolve(directoryName);
            if (Files.isSymbolicLink(directory)) throw new IOException("symlink in target region storage");
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;
            try (var files = Files.list(directory)) {
                for (Path source : files.filter(path -> belongsToRegion(path, regionX, regionZ)).toList()) {
                    if (Files.isSymbolicLink(source)) throw new IOException("symlink in target region storage");
                    Path target = BackupPathSafety.resolveInside(quarantine,
                            directoryName + '/' + source.getFileName(), true);
                    Files.createDirectories(target.getParent());
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        Files.deleteIfExists(source);
                    } else {
                        atomicMove(source, target);
                    }
                }
            }
        }
    }

    private static void replaceRegionFiles(
            Path worldRoot,
            RestoreOperationManifest operation,
            RestoreArchiveExtractor.ExtractedChunks extracted) throws IOException {
        Path dimension = BackupPathSafety.resolveInside(worldRoot, operation.dimensionRelativePath(), false);
        for (Map.Entry<String, Path> entry : extracted.regionFiles().entrySet()) {
            Path target = BackupPathSafety.resolveInside(dimension, entry.getKey(), true);
            Files.createDirectories(target.getParent());
            Path temporary = target.resolveSibling(target.getFileName() + ".restore-" + operation.operationId());
            Files.copy(entry.getValue(), temporary, StandardCopyOption.COPY_ATTRIBUTES);
            if (!sha256(temporary).equals(sha256(entry.getValue()))) {
                Files.deleteIfExists(temporary);
                throw new IOException("restored region payload failed verification: " + entry.getKey());
            }
            atomicMove(temporary, target);
            if (!sha256(target).equals(sha256(entry.getValue()))) {
                throw new IOException("published region payload failed verification: " + entry.getKey());
            }
        }
    }

    private static boolean belongsToRegion(Path path, int regionX, int regionZ) {
        String name = path.getFileName().toString();
        if (name.equals("r." + regionX + '.' + regionZ + ".mca")) return true;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("c\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mcc")
                .matcher(name);
        return matcher.matches()
                && Math.floorDiv(Integer.parseInt(matcher.group(1)), 32) == regionX
                && Math.floorDiv(Integer.parseInt(matcher.group(2)), 32) == regionZ;
    }

    private static void rewriteTargetCell(
            Path worldRoot,
            RestoreOperationManifest operation,
            RestoreArchiveExtractor.ExtractedChunks extracted) throws IOException {
        Path dimension = BackupPathSafety.resolveInside(
                worldRoot,
                operation.dimensionRelativePath(),
                false);
        Path quarantine = BackupPathSafety.resolveInside(
                worldRoot,
                operation.quarantineRelativePath(),
                true);
        Files.createDirectories(quarantine);

        Set<ChunkCoordinate> target = Set.copyOf(operation.targetBounds().coordinates());
        RegionFileRewriter rewriter = new RegionFileRewriter();
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            Path storage = dimension.resolve(storageDirectory(kind));
            Map<ChunkCoordinate, Path> chunks = extracted.chunks().get(kind);
            for (RegionCoordinate region : touchedRegions(target)) {
                Set<ChunkCoordinate> regionTargets = new HashSet<>();
                Map<ChunkCoordinate, Path> regionChunks = new HashMap<>();
                for (ChunkCoordinate coordinate : target) {
                    if (region.contains(coordinate)) {
                        regionTargets.add(coordinate);
                        Path payload = chunks.get(coordinate);
                        if (payload != null) {
                            regionChunks.put(coordinate, payload);
                        }
                    }
                }
                rewriter.rewrite(
                        storage,
                        region.x(),
                        region.z(),
                        regionTargets,
                        regionChunks,
                        quarantine,
                        kind.directory());
            }
        }
    }

    private static Set<RegionCoordinate> touchedRegions(Set<ChunkCoordinate> coordinates) {
        Set<RegionCoordinate> regions = new HashSet<>();
        for (ChunkCoordinate coordinate : coordinates) {
            regions.add(new RegionCoordinate(
                    Math.floorDiv(coordinate.x(), 32),
                    Math.floorDiv(coordinate.z(), 32)));
        }
        return regions;
    }

    private static BackupManifest verifyBackup(
            Path archive,
            RestoreOperationManifest operation) throws IOException {
        var verification = new BackupArchiveVerifier().verify(archive);
        if (!verification.valid()) {
            throw new IOException("backup integrity verification failed: " + verification.failures());
        }

        BackupManifest backup = verification.manifest().orElseThrow();
        if (!backup.backupId().equals(operation.backupId())
                || backup.realmId() != operation.realmId()
                || !backup.worldIdentity().equals(operation.worldIdentity())
                || !backup.dimension().equals(operation.dimension())
                || !backup.allocationProfile().equals(operation.allocationProfile())
                || backup.strategy() != operation.strategy()
                || !backup.cellBounds().equals(operation.targetBounds())) {
            throw new IOException("backup identity or allocation does not match the restore manifest");
        }
        return backup;
    }

    private static void validateOperation(
            Path worldRoot,
            RestoreOperationManifest operation) throws IOException {
        if (!REALMS_DIMENSION.equals(operation.dimension())) {
            throw new IOException("restore target is not the Realms dimension");
        }
        if (!REALMS_DIMENSION_PATH.equals(operation.dimensionRelativePath())) {
            throw new IOException("restore target path is not the exact Realms dimension path");
        }
        if (!operation.quarantineRelativePath().startsWith(BACKUP_ROOT + "/quarantine/")) {
            throw new IOException("restore quarantine is outside the backup root");
        }

        Path identityFile = BackupPathSafety.resolveInside(
                worldRoot,
                "paradigm-realms/world-identity.txt",
                false);
        String currentIdentity = Files.readString(identityFile, StandardCharsets.UTF_8).strip();
        if (!currentIdentity.equals(operation.worldIdentity())) {
            throw new IOException("restore manifest belongs to a different world");
        }
        Path state = BackupPathSafety.resolveInside(worldRoot, "data/paradigm_realms.dat", false);
        if (!sha256(state).equals(operation.realmStateSha256())) {
            throw new IOException("Realms persistent state changed after restore preparation");
        }
        if (operation.strategy() == BackupStrategy.REGION_COPY
                && (!"region-aligned-32-v1".equals(operation.allocationProfile())
                || operation.targetBounds().maximumChunkX() - operation.targetBounds().minimumChunkX() + 1 != 32
                || operation.targetBounds().maximumChunkZ() - operation.targetBounds().minimumChunkZ() + 1 != 32
                || Math.floorMod(operation.targetBounds().minimumChunkX(), 32) != 0
                || Math.floorMod(operation.targetBounds().minimumChunkZ(), 32) != 0)) {
            throw new IOException("REGION_COPY target no longer describes exactly one owned region");
        }
    }

    private static boolean canRunOffline(RestoreManifestStage stage) {
        return stage == RestoreManifestStage.PREPARED
                || stage == RestoreManifestStage.SERVER_STOPPED_EXPECTED
                || stage == RestoreManifestStage.TARGET_QUARANTINED
                || stage == RestoreManifestStage.REGION_FILES_REWRITTEN;
    }

    private static String storageDirectory(BackupStorageKind kind) {
        return switch (kind) {
            case TERRAIN -> "region";
            case ENTITIES -> "entities";
            case POI -> "poi";
        };
    }

    private static FileLock lockWorld(FileChannel channel) throws IOException {
        try {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                throw new IOException("world session.lock is held; the server may still be running");
            }
            return lock;
        } catch (OverlappingFileLockException exception) {
            throw new IOException("world session.lock is held in this process", exception);
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support required atomic region replacement", exception);
        }
    }

    private static String sha256(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static void rejectSymlinkChain(Path configuredPath) throws IOException {
        Path cursor = configuredPath.toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(cursor)) {
                throw new IOException("world path must not contain a symlink");
            }
            cursor = cursor.getParent();
        }
    }

    private static void deleteStagingIfPresent(Path staging, Path worldRoot) throws IOException {
        if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BackupPathSafety.rejectSymlinks(worldRoot, staging);
        try (var paths = Files.walk(staging)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symlink found in restore staging");
                }
                Files.delete(path);
            }
        }
    }

    private record RegionCoordinate(int x, int z) {
        boolean contains(ChunkCoordinate coordinate) {
            return Math.floorDiv(coordinate.x(), 32) == x
                    && Math.floorDiv(coordinate.z(), 32) == z;
        }
    }

    private record Arguments(Path world, Path manifest) {
        static Arguments parse(String[] arguments) {
            if (arguments.length != 5
                    || !"restore".equals(arguments[0])
                    || !"--world".equals(arguments[1])
                    || !"--manifest".equals(arguments[3])) {
                throw new IllegalArgumentException(
                        "usage: restore --world <world> --manifest <restore-manifest.json>");
            }
            return new Arguments(Path.of(arguments[2]), Path.of(arguments[4]));
        }
    }
}
