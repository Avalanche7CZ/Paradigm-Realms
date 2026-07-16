package eu.avalanche7.paradigmrealms.backup.offline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import eu.avalanche7.paradigmrealms.backup.BackupManifest;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.backup.io.BackupArchiveVerifier;

final class RestoreArchiveExtractor {
    ExtractedChunks extract(Path archive, BackupManifest manifest, Path stagingDirectory)
            throws IOException {
        if (Files.exists(stagingDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("unique restore staging directory already exists");
        }
        Files.createDirectories(stagingDirectory);

        Map<String, Target> expected = expectedEntries(manifest, stagingDirectory);
        EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> extracted =
                new EnumMap<>(BackupStorageKind.class);
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            extracted.put(kind, new HashMap<>());
        }

        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Target target = expected.remove(entry.getName());
                if (target != null) {
                    Path path = target.path();
                    Files.createDirectories(path.getParent());
                    copyBounded(zip, path);
                    extracted.get(target.kind()).put(target.coordinate(), path);
                }
                zip.closeEntry();
            }
        }

        if (!expected.isEmpty()) {
            throw new IOException("verified archive changed while it was being extracted");
        }
        return new ExtractedChunks(copy(extracted), stagingDirectory);
    }

    private static Map<String, Target> expectedEntries(
            BackupManifest manifest,
            Path stagingDirectory) {
        Map<String, Target> expected = new HashMap<>();
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            for (ChunkCoordinate coordinate : manifest.chunks().get(kind)) {
                Path destination = stagingDirectory
                        .resolve(kind.directory())
                        .resolve(coordinate.x() + "_" + coordinate.z() + ".nbt");
                expected.put(
                        coordinate.archiveName(kind),
                        new Target(kind, coordinate, destination));
            }
        }
        return expected;
    }

    private static void copyBounded(ZipInputStream input, Path destination) throws IOException {
        try (var output = Files.newOutputStream(
                destination,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[32 * 1024];
            long copied = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                copied += read;
                if (copied > BackupArchiveVerifier.MAX_CHUNK_BYTES) {
                    throw new IOException("chunk payload exceeds the restore limit");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static Map<BackupStorageKind, Map<ChunkCoordinate, Path>> copy(
            EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> values) {
        EnumMap<BackupStorageKind, Map<ChunkCoordinate, Path>> result =
                new EnumMap<>(BackupStorageKind.class);
        values.forEach((kind, chunks) -> result.put(kind, Map.copyOf(chunks)));
        return Map.copyOf(result);
    }

    private record Target(
            BackupStorageKind kind,
            ChunkCoordinate coordinate,
            Path path) {}

    record ExtractedChunks(
            Map<BackupStorageKind, Map<ChunkCoordinate, Path>> chunks,
            Path stagingDirectory) {}
}
