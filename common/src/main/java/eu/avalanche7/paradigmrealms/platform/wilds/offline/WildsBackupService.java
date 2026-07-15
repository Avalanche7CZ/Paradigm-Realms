package eu.avalanche7.paradigmrealms.platform.wilds.offline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class WildsBackupService {
    private static final String BACKUP_ROOT = "paradigm-realms-backups/wilds";
    private static final int MAX_REMOVALS_PER_RUN = 16;

    public List<String> list(Path canonicalWorldRoot) throws IOException {
        Path base = WildsPathSafety.resolve(canonicalWorldRoot, BACKUP_ROOT, true);
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var stream = Files.list(base)) {
            return stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(path))
                    .map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    public int prune(Path canonicalWorldRoot, int retentionCount) throws IOException {
        if (retentionCount < 1 || retentionCount > 100) {
            throw new IllegalArgumentException("retention count must be 1-100");
        }
        Path base = WildsPathSafety.resolve(canonicalWorldRoot, BACKUP_ROOT, true);
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) return 0;
        List<Path> backups;
        try (var stream = Files.list(base)) {
            backups = stream.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(path))
                    .filter(path -> path.getFileName().toString()
                            .matches("epoch-[0-9]+-operation-[0-9a-f-]{36}"))
                    .sorted(Comparator.comparingLong(WildsBackupService::lastModified).reversed())
                    .toList();
        }
        int removed = 0;
        for (int index = retentionCount;
                index < backups.size() && removed < MAX_REMOVALS_PER_RUN; index++) {
            deleteTreeNoLinks(backups.get(index));
            removed++;
        }
        return removed;
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private static void deleteTreeNoLinks(Path root) throws IOException {
        if (Files.isSymbolicLink(root)) throw new IOException("refusing to prune symlink");
        try (var stream = Files.walk(root)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("backup contains symlink: " + path.getFileName());
                }
                Files.delete(path);
            }
        }
    }
}
