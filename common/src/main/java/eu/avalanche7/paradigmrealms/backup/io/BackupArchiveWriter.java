package eu.avalanche7.paradigmrealms.backup.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import eu.avalanche7.paradigmrealms.backup.BackupManifest;
import eu.avalanche7.paradigmrealms.backup.BackupManifestJsonCodec;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.BackupVerificationResult;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.backup.RealmMetadataJsonCodec;
import eu.avalanche7.paradigmrealms.backup.RealmMetadataSnapshot;

public final class BackupArchiveWriter {
    private final BackupArchiveVerifier verifier = new BackupArchiveVerifier();

    public Result write(Path destination, BackupManifest manifest, RealmMetadataSnapshot metadata,
            Map<BackupStorageKind, Map<ChunkCoordinate, Path>> chunks, int compressionLevel) throws IOException {
        return write(destination, manifest, metadata, chunks, Map.of(), compressionLevel);
    }

    public Result write(Path destination, BackupManifest manifest, RealmMetadataSnapshot metadata,
            Map<BackupStorageKind, Map<ChunkCoordinate, Path>> chunks,
            Map<String, Path> regionFiles, int compressionLevel) throws IOException {
        if (Files.exists(destination) || Files.isSymbolicLink(destination)) {
            throw new IOException("backup destination already exists");
        }
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp-" + manifest.backupId());
        if (Files.exists(temporary)) {
            throw new IOException("unique temporary backup already exists");
        }
        LinkedHashMap<String, String> checksums = new LinkedHashMap<>();
        try {
            try (OutputStream file = Files.newOutputStream(
                            temporary,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                    ZipOutputStream zip = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
                zip.setLevel(Math.max(Deflater.NO_COMPRESSION, Math.min(Deflater.BEST_COMPRESSION, compressionLevel)));
                writeMetadataEntries(zip, manifest, metadata, checksums);
                writeChunkEntries(zip, chunks, checksums);
                writeRegionEntries(zip, regionFiles, checksums);
                writeChecksumEntry(zip, checksums);
            } catch (ArchiveWriteException exception) {
                throw (IOException) exception.getCause();
            }
            BackupVerificationResult verification = verifier.verify(temporary);
            if (!verification.valid()) {
                throw new IOException("written archive failed verification: " + verification.failures());
            }
            atomicMove(temporary, destination);
            Path sidecar = writeSidecar(destination);
            return new Result(destination, sidecar, Files.size(destination), verification);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private static void writeRegionEntries(
            ZipOutputStream zip, Map<String, Path> files, Map<String, String> checksums) {
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try {
                writePath(zip, entry.getKey(), entry.getValue(), checksums,
                        BackupArchiveVerifier.MAX_REGION_FILE_BYTES);
            } catch (IOException exception) {
                throw new ArchiveWriteException(exception);
            }
        });
    }

    private static void writeMetadataEntries(
            ZipOutputStream zip,
            BackupManifest manifest,
            RealmMetadataSnapshot metadata,
            Map<String, String> checksums) throws IOException {
        writeBytes(
                zip,
                "manifest.json",
                new BackupManifestJsonCodec().encode(manifest).getBytes(StandardCharsets.UTF_8),
                checksums);
        writeBytes(
                zip,
                "realm-metadata.json",
                new RealmMetadataJsonCodec().encode(metadata).getBytes(StandardCharsets.UTF_8),
                checksums);
        writeBytes(
                zip,
                "format.txt",
                ("Paradigm Realms realm backup format " + manifest.formatVersion() + "\n")
                        .getBytes(StandardCharsets.UTF_8),
                checksums);
    }

    private static void writeChunkEntries(
            ZipOutputStream zip,
            Map<BackupStorageKind, Map<ChunkCoordinate, Path>> chunks,
            Map<String, String> checksums) {
        List<Item> items = new ArrayList<>();
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            chunks.getOrDefault(kind, Map.of()).forEach((coordinate, path) ->
                    items.add(new Item(coordinate.archiveName(kind), path)));
        }
        items.stream().sorted(Comparator.comparing(Item::name)).forEach(item -> {
            try {
                writePath(zip, item.name(), item.path(), checksums);
            } catch (IOException exception) {
                throw new ArchiveWriteException(exception);
            }
        });
    }

    private static void writeChecksumEntry(
            ZipOutputStream zip,
            Map<String, String> checksums) throws IOException {
        StringBuilder value = new StringBuilder();
        checksums.forEach((name, digest) -> value
                .append(digest)
                .append("  ")
                .append(name)
                .append('\n'));
        writeUnchecked(
                zip,
                "checksums.sha256",
                value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(
            ZipOutputStream zip,
            String name,
            byte[] value,
            Map<String, String> checksums) throws IOException {
        checksums.put(name, HexFormat.of().formatHex(sha256().digest(value)));
        writeUnchecked(zip, name, value);
    }

    private static void writeUnchecked(
            ZipOutputStream zip,
            String name,
            byte[] value) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        zip.write(value);
        zip.closeEntry();
    }

    private static void writePath(
            ZipOutputStream zip,
            String name,
            Path source,
            Map<String, String> checksums) throws IOException {
        writePath(zip, name, source, checksums, BackupArchiveVerifier.MAX_CHUNK_BYTES);
    }

    private static void writePath(
            ZipOutputStream zip, String name, Path source, Map<String, String> checksums, long limit)
            throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source)) {
            throw new IOException("captured chunk file is missing or a symlink");
        }

        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(source)) {
            copyBounded(input, zip, digest, limit);
        }
        zip.closeEntry();
        checksums.put(name, HexFormat.of().formatHex(digest.digest()));
    }

    private static void copyBounded(
            InputStream input,
            ZipOutputStream output,
            MessageDigest digest,
            long limit) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > limit) {
                throw new IOException("captured payload exceeds limit");
            }
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
    }

    private static Path writeSidecar(Path destination) throws IOException {
        String sidecarDigest = digest(destination);
        Path sidecarTemporary = destination.resolveSibling(destination.getFileName() + ".sha256.tmp");
        Path sidecar = destination.resolveSibling(destination.getFileName() + ".sha256");
        Files.writeString(
                sidecarTemporary,
                sidecarDigest + "  " + destination.getFileName() + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        atomicMove(sidecarTemporary, sidecar);
        return sidecar;
    }

    private static String digest(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("filesystem does not support required atomic backup publication", exception);
        }
    }

    private record Item(String name, Path path) {}

    private static final class ArchiveWriteException extends RuntimeException {
        private ArchiveWriteException(IOException cause) {
            super(cause);
        }
    }

    public record Result(Path archive, Path sidecar, long sizeBytes, BackupVerificationResult verification) {}
}
