package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;

final class FabricBackupScheduleState {
    private FabricBackupScheduleState() {}

    static Instant loadOrCreate(Path path, Clock clock) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("backup schedule state is a symlink");
            }
            try {
                return Instant.parse(Files.readString(path, StandardCharsets.UTF_8).strip());
            } catch (RuntimeException malformed) {
                throw new IOException("backup schedule state is malformed", malformed);
            }
        }

        Instant enabledAt = clock.instant();
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                enabledAt + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.deleteIfExists(temporary);
            throw new IOException("filesystem does not support atomic schedule-state creation", exception);
        }
        return enabledAt;
    }
}
