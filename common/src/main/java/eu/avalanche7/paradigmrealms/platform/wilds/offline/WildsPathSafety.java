package eu.avalanche7.paradigmrealms.platform.wilds.offline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class WildsPathSafety {
    private WildsPathSafety() {}

    public static Path resolve(Path canonicalRoot, String relative, boolean mayNotExist) throws IOException {
        Path candidate = canonicalRoot.resolve(relative).normalize().toAbsolutePath();
        if (candidate.equals(canonicalRoot) || !candidate.startsWith(canonicalRoot)) {
            throw new IOException("target escapes or equals world root");
        }
        Path cursor = candidate;
        while (cursor != null && !cursor.equals(canonicalRoot)) {
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new IOException("symlink rejected in Wilds path: " + canonicalRoot.relativize(cursor));
            }
            cursor = cursor.getParent();
        }
        if (!mayNotExist && !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("expected Wilds directory is missing: " + relative);
        }
        return candidate;
    }
}
