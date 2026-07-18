package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

final class ForgeWorldIdentity {
    private ForgeWorldIdentity() {}

    static String loadOrCreate(Path file) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return read(file);
        }

        String identity = UUID.randomUUID().toString();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                temporary,
                identity + '\n',
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        force(temporary);

        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
            return identity;
        } catch (java.nio.file.FileAlreadyExistsException race) {
            Files.deleteIfExists(temporary);
            return read(file);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.deleteIfExists(temporary);
            throw new IOException("world identity requires atomic file moves", exception);
        }
    }

    private static String read(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("world identity file is unsafe");
        }
        String value = Files.readString(file, StandardCharsets.UTF_8).strip();
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new IOException("world identity file is malformed", exception);
        }
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }
}
