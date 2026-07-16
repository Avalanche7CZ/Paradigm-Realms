package eu.avalanche7.paradigmrealms.backup.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

import eu.avalanche7.paradigmrealms.backup.BackupCatalog;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogJsonCodec;
import eu.avalanche7.paradigmrealms.backup.BackupIntegrityStatus;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;

public final class BackupCatalogFile {
    public static final int MAXIMUM_REBUILD_ARCHIVES = 10_000;
    private static final String CATALOG_PATH = "catalog/catalog.json";
    private final BackupCatalogJsonCodec codec = new BackupCatalogJsonCodec();
    private final BackupArchiveVerifier verifier = new BackupArchiveVerifier();

    public LoadResult load(Path backupRoot) {
        Path path = backupRoot.resolve(CATALOG_PATH);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new LoadResult(new BackupCatalog(List.of()), List.of("backup catalog is missing"));
        }
        try {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("backup catalog is a symlink");
            }
            return new LoadResult(
                    new BackupCatalog(codec.decode(Files.readString(path, StandardCharsets.UTF_8))),
                    List.of());
        } catch (IOException | RuntimeException exception) {
            return new LoadResult(
                    new BackupCatalog(List.of()),
                    List.of("backup catalog could not be read: " + exception.getMessage()));
        }
    }

    public void save(Path backupRoot, BackupCatalog catalog) throws IOException {
        Path path = backupRoot.resolve(CATALOG_PATH);
        Files.createDirectories(path.getParent());
        BackupPathSafety.rejectSymlinks(backupRoot, path);

        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        Files.writeString(
                temporary,
                codec.encode(catalog.list()),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        atomicReplace(temporary, path);
    }

    public RebuildResult rebuild(Path backupRoot) throws IOException {
        Path realmsRoot = backupRoot.resolve("realms");
        if (!Files.isDirectory(realmsRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(realmsRoot);
            BackupPathSafety.rejectSymlinks(backupRoot, realmsRoot);
            BackupCatalog empty = new BackupCatalog(List.of());
            save(backupRoot, empty);
            return new RebuildResult(empty, List.of(), 0);
        }
        BackupPathSafety.rejectSymlinks(backupRoot, realmsRoot);

        List<Path> archives;
        try (var paths = Files.walk(realmsRoot)) {
            archives = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.naturalOrder())
                    .limit(MAXIMUM_REBUILD_ARCHIVES + 1L)
                    .toList();
        }
        if (archives.size() > MAXIMUM_REBUILD_ARCHIVES) {
            throw new IOException("backup catalog rebuild limit exceeded");
        }

        BackupCatalog catalog = new BackupCatalog(List.of());
        List<String> warnings = new ArrayList<>();
        for (Path archive : archives) {
            var result = verifier.verify(archive);
            if (!result.valid()) {
                warnings.add(backupRoot.relativize(archive) + ": " + result.failures());
                continue;
            }
            var manifest = result.manifest().orElseThrow();
            EnumMap<BackupStorageKind, Integer> counts = new EnumMap<>(BackupStorageKind.class);
            for (BackupStorageKind kind : BackupStorageKind.values()) {
                counts.put(kind, manifest.chunkCount(kind));
            }
            BackupCatalogEntry entry = new BackupCatalogEntry(
                    manifest.backupId(),
                    manifest.realmId(),
                    manifest.ownerUuid(),
                    manifest.ownerNameSnapshot(),
                    manifest.createdAt(),
                    manifest.reason(),
                    Files.size(archive),
                    backupRoot.relativize(archive).toString().replace('\\', '/'),
                    BackupIntegrityStatus.VERIFIED,
                    manifest.pinned(),
                    false,
                    manifest.formatVersion(),
                    manifest.minecraftVersion(),
                    manifest.realmsVersion(),
                    counts);
            try {
                catalog.put(entry);
            } catch (IllegalArgumentException duplicate) {
                warnings.add(backupRoot.relativize(archive)
                        + ": duplicate backup ID " + manifest.backupId().value());
            }
        }

        save(backupRoot, catalog);
        return new RebuildResult(catalog, List.copyOf(warnings), archives.size());
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support atomic catalog updates", exception);
        }
    }

    public record LoadResult(BackupCatalog catalog, List<String> warnings) {}
    public record RebuildResult(BackupCatalog catalog, List<String> warnings, int scannedArchives) {}
}
