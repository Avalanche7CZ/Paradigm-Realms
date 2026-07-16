package eu.avalanche7.paradigmrealms.backup.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import eu.avalanche7.paradigmrealms.backup.RestoreOperationManifest;
import eu.avalanche7.paradigmrealms.backup.RestoreOperationManifestJsonCodec;

public final class RestoreManifestFile {
    private final RestoreOperationManifestJsonCodec codec = new RestoreOperationManifestJsonCodec();

    public RestoreOperationManifest read(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) {
            throw new IOException("restore manifest is missing or is a symlink");
        }
        return codec.decode(Files.readString(path, StandardCharsets.UTF_8));
    }

    public void write(Path path, RestoreOperationManifest manifest) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path.getParent())) {
            throw new IOException("restore manifest directory is a symlink");
        }

        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);
        Files.writeString(
                temporary,
                codec.encode(manifest),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }

        try {
            Files.move(
                    temporary,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support atomic restore-manifest updates", exception);
        }
    }
}
