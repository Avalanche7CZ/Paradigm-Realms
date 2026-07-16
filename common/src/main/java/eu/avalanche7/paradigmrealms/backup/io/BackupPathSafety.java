package eu.avalanche7.paradigmrealms.backup.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class BackupPathSafety {
    private BackupPathSafety() {}

    public static Path resolveInside(Path canonicalRoot, String relative, boolean mayNotExist)
            throws IOException {
        if (relative == null || relative.isBlank() || relative.startsWith("/")
                || relative.contains("\\") || relative.indexOf('\0') >= 0) {
            throw new IOException("unsafe relative path");
        }

        Path candidate = canonicalRoot.resolve(relative).normalize().toAbsolutePath();
        if (candidate.equals(canonicalRoot) || !candidate.startsWith(canonicalRoot)) {
            throw new IOException("path escapes or equals its allowed root");
        }

        rejectSymlinks(canonicalRoot, candidate);
        if (!mayNotExist && !Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("required path is missing: " + relative);
        }
        return candidate;
    }

    public static void rejectSymlinks(Path canonicalRoot, Path candidate) throws IOException {
        Path cursor = candidate;
        while (cursor != null && !cursor.equals(canonicalRoot)) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new IOException("symlink rejected: " + canonicalRoot.relativize(cursor));
            }
            cursor = cursor.getParent();
        }
    }
}
