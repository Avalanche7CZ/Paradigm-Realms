package eu.avalanche7.paradigmrealms.backup.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import eu.avalanche7.paradigmrealms.backup.RestoreOperationManifest;
import eu.avalanche7.paradigmrealms.backup.RestoreOperationManifestJsonCodec;
import eu.avalanche7.paradigmrealms.backup.RestoreRecoveryPolicy;

public final class RestoreManifestFile {
    private final RestoreOperationManifestJsonCodec codec = new RestoreOperationManifestJsonCodec();

    public Map<Path, RestoreOperationManifest> readPending(Path directory, int maximumPending) throws IOException {
        if (maximumPending < 1) throw new IllegalArgumentException("pending manifest limit must be positive");
        Map<Path, RestoreOperationManifest> pending = new TreeMap<>();
        try (var files = Files.list(directory)) {
            var paths = files.filter(path -> path.getFileName().toString().endsWith(".json")).iterator();
            while (paths.hasNext()) {
                Path path = paths.next();
                RestoreOperationManifest operation = read(path);
                if (!RestoreRecoveryPolicy.requiresProtection(operation)) continue;
                pending.put(path, operation);
                if (pending.size() > maximumPending) throw new IOException("restore manifest recovery limit exceeded");
            }
        }
        return Collections.unmodifiableMap(pending);
    }

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
