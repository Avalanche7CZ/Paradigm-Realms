package eu.avalanche7.paradigmrealms.platform.backup;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.backup.BackupActor;
import eu.avalanche7.paradigmrealms.backup.BackupFailure;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupLifecycleState;
import eu.avalanche7.paradigmrealms.backup.BackupOperation;
import eu.avalanche7.paradigmrealms.backup.BackupReason;

final class FabricBackupOperationStore {
    private static final int MAXIMUM_OPERATIONS = 2_000;
    private final Path directory;

    FabricBackupOperationStore(Path directory) {
        this.directory = directory;
    }

    synchronized void save(BackupOperation operation) throws IOException {
        Path target = path(operation.operationId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.deleteIfExists(temporary);

        try (OutputStream output = Files.newOutputStream(
                temporary,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            properties(operation).store(output, "Paradigm Realms backup operation");
        }
        force(temporary);
        atomicReplace(temporary, target);
    }

    synchronized List<BackupOperation> load() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }

        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .limit(MAXIMUM_OPERATIONS + 1L)
                    .toList();
        }
        if (files.size() > MAXIMUM_OPERATIONS) {
            throw new IOException("backup operation recovery limit exceeded");
        }

        List<BackupOperation> operations = new ArrayList<>(files.size());
        for (Path file : files) {
            operations.add(read(file));
        }
        return List.copyOf(operations);
    }

    private BackupOperation read(Path file) throws IOException {
        if (Files.isSymbolicLink(file) || Files.size(file) > 64 * 1024) {
            throw new IOException("unsafe backup operation file " + file.getFileName());
        }

        Properties value = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            value.load(input);
        }

        try {
            BackupActor.Type actorType = BackupActor.Type.valueOf(required(value, "actorType"));
            Optional<UUID> actorUuid = optional(value, "actorUuid").map(UUID::fromString);
            Optional<BackupFailure> failure = optional(value, "failure").map(BackupFailure::valueOf);
            return new BackupOperation(
                    UUID.fromString(required(value, "operationId")),
                    new BackupId(required(value, "backupId")),
                    Long.parseLong(required(value, "realmId")),
                    BackupReason.valueOf(required(value, "reason")),
                    new BackupActor(actorType, actorUuid, required(value, "actorName")),
                    BackupLifecycleState.valueOf(required(value, "state")),
                    Instant.parse(required(value, "createdAt")),
                    Instant.parse(required(value, "updatedAt")),
                    Integer.parseInt(required(value, "attempt")),
                    required(value, "stagingRelativePath"),
                    failure,
                    optional(value, "failureDetail"));
        } catch (RuntimeException exception) {
            throw new IOException("malformed backup operation " + file.getFileName(), exception);
        }
    }

    private static Properties properties(BackupOperation operation) {
        Properties value = new Properties();
        value.setProperty("operationId", operation.operationId().toString());
        value.setProperty("backupId", operation.backupId().value());
        value.setProperty("realmId", Long.toString(operation.realmId()));
        value.setProperty("reason", operation.reason().name());
        value.setProperty("actorType", operation.actor().type().name());
        operation.actor().uuid().ifPresent(uuid -> value.setProperty("actorUuid", uuid.toString()));
        value.setProperty("actorName", operation.actor().nameSnapshot());
        value.setProperty("state", operation.state().name());
        value.setProperty("createdAt", operation.createdAt().toString());
        value.setProperty("updatedAt", operation.updatedAt().toString());
        value.setProperty("attempt", Integer.toString(operation.attempt()));
        value.setProperty("stagingRelativePath", operation.stagingRelativePath());
        operation.failure().ifPresent(failure -> value.setProperty("failure", failure.name()));
        operation.failureDetail().ifPresent(detail -> value.setProperty("failureDetail", detail));
        return value;
    }

    private Path path(UUID operationId) {
        return directory.resolve(operationId + ".properties");
    }

    private static String required(Properties value, String key) {
        String result = value.getProperty(key);
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("missing " + key);
        }
        return result;
    }

    private static Optional<String> optional(Properties value, String key) {
        return Optional.ofNullable(value.getProperty(key)).filter(text -> !text.isBlank());
    }

    private static void force(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("backup operations require atomic file moves", exception);
        }
    }
}
