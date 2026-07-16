package eu.avalanche7.paradigmrealms.backup.io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import eu.avalanche7.paradigmrealms.backup.BackupManifest;
import eu.avalanche7.paradigmrealms.backup.BackupManifestJsonCodec;
import eu.avalanche7.paradigmrealms.backup.BackupStorageKind;
import eu.avalanche7.paradigmrealms.backup.BackupVerificationResult;
import eu.avalanche7.paradigmrealms.backup.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.backup.RealmMetadataJsonCodec;
import eu.avalanche7.paradigmrealms.backup.RealmMetadataSnapshot;

public final class BackupArchiveVerifier {
    public static final long MAX_MANIFEST_BYTES = 1_048_576;
    public static final long MAX_METADATA_BYTES = 4_194_304;
    public static final long MAX_CHUNK_BYTES = 128L * 1024 * 1024;
    public static final int MAX_ENTRIES = 800;
    private final BackupManifestJsonCodec manifestCodec = new BackupManifestJsonCodec();
    private final RealmMetadataJsonCodec metadataCodec = new RealmMetadataJsonCodec();

    public BackupVerificationResult verify(Path archive) {
        ArrayList<String> failures = new ArrayList<>();
        long streamed = 0;
        if (Files.isSymbolicLink(archive) || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) {
            return BackupVerificationResult.invalid(List.of("backup archive is missing or is a symlink"), 0);
        }

        Map<String, String> calculated = new HashMap<>();
        Set<String> names = new HashSet<>();
        byte[] manifestBytes = null;
        byte[] metadataBytes = null;
        byte[] checksumBytes = null;
        int entries = 0;

        try (InputStream input = Files.newInputStream(archive); ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("archive has too many entries");
                }
                String name = entry.getName();
                validateName(name);
                if (entry.isDirectory() || !names.add(name)) {
                    throw new IOException("duplicate or directory entry " + name);
                }

                long limit = limit(name);
                MessageDigest digest = sha256();
                ByteArrayOutputStream retained = retained(name) ? new ByteArrayOutputStream() : null;
                byte[] buffer = new byte[32 * 1024];
                long entryBytes = 0;
                int read;
                while ((read = zip.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    entryBytes += read;
                    streamed += read;
                    if (entryBytes > limit) {
                        throw new IOException("entry exceeds bounded size: " + name);
                    }
                    digest.update(buffer, 0, read);
                    if (retained != null) {
                        retained.write(buffer, 0, read);
                    }
                }
                if (!"checksums.sha256".equals(name)) {
                    calculated.put(name, HexFormat.of().formatHex(digest.digest()));
                }
                if ("manifest.json".equals(name)) {
                    manifestBytes = retained.toByteArray();
                } else if ("realm-metadata.json".equals(name)) {
                    metadataBytes = retained.toByteArray();
                } else if ("checksums.sha256".equals(name)) {
                    checksumBytes = retained.toByteArray();
                }
                zip.closeEntry();
            }
        } catch (Exception exception) {
            failures.add("archive stream failed: " + exception.getMessage());
            return BackupVerificationResult.invalid(failures, streamed);
        }
        if (manifestBytes == null) {
            failures.add("manifest.json is missing");
        }
        if (metadataBytes == null) {
            failures.add("realm-metadata.json is missing");
        }
        if (checksumBytes == null) {
            failures.add("checksums.sha256 is missing");
        }

        BackupManifest manifest = null;
        if (manifestBytes != null) {
            try {
                manifest = manifestCodec.decode(new String(
                        manifestBytes,
                        java.nio.charset.StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                failures.add("manifest is malformed: " + exception.getMessage());
            }
        }

        RealmMetadataSnapshot metadata = null;
        if (metadataBytes != null) {
            try {
                metadata = metadataCodec.decode(new String(
                        metadataBytes,
                        java.nio.charset.StandardCharsets.UTF_8));
            } catch (RuntimeException exception) {
                failures.add("realm metadata is malformed: " + exception.getMessage());
            }
        }
        if (checksumBytes != null) {
            try {
                verifyChecksums(
                        new String(checksumBytes, java.nio.charset.StandardCharsets.UTF_8),
                        calculated,
                        failures);
            } catch (RuntimeException exception) {
                failures.add("checksum file is malformed: " + exception.getMessage());
            }
        }
        if (manifest != null) {
            verifyExpected(manifest, names, failures);
        }
        if (manifest != null && metadata != null) {
            verifyMetadataIdentity(manifest, metadata, failures);
        }
        return new BackupVerificationResult(
                failures.isEmpty(),
                java.util.Optional.ofNullable(manifest),
                failures,
                streamed);
    }

    private static void verifyChecksums(String source, Map<String, String> calculated, List<String> failures) {
        Map<String, String> expected = new HashMap<>();
        for (String line : source.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            int split = line.indexOf("  ");
            if (split != 64) {
                throw new IllegalArgumentException("invalid checksum line");
            }
            String digest = line.substring(0, split);
            String name = line.substring(split + 2);
            validateName(name);
            if (!digest.matches("[0-9a-f]{64}") || expected.putIfAbsent(name, digest) != null) {
                throw new IllegalArgumentException("invalid or duplicate checksum entry");
            }
        }
        if (!expected.keySet().equals(calculated.keySet())) {
            failures.add("checksum entry set does not match archive entries");
        }
        calculated.forEach((name, digest) -> {
            if (!digest.equals(expected.get(name))) {
                failures.add("checksum mismatch for " + name);
            }
        });
    }

    private static void verifyExpected(BackupManifest manifest, Set<String> names, List<String> failures) {
        Set<String> expected = new HashSet<>(Set.of("manifest.json", "realm-metadata.json", "format.txt", "checksums.sha256"));
        for (BackupStorageKind kind : BackupStorageKind.values()) {
            for (ChunkCoordinate coordinate : manifest.chunks().get(kind)) {
                expected.add(coordinate.archiveName(kind));
            }
        }
        if (!names.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(names);
            Set<String> unexpected = new HashSet<>(names);
            unexpected.removeAll(expected);
            if (!missing.isEmpty()) {
                failures.add("missing expected entries: " + missing);
            }
            if (!unexpected.isEmpty()) {
                failures.add("unexpected entries: " + unexpected);
            }
        }
        for (String name : names) {
            for (BackupStorageKind kind : BackupStorageKind.values()) {
                String prefix = "chunks/" + kind.directory() + '/';
                if (name.startsWith(prefix)) {
                    try {
                        ChunkCoordinate coordinate = ChunkCoordinate.parseArchiveName(name, kind);
                        if (!manifest.cellBounds().contains(coordinate)) {
                            failures.add("out-of-cell chunk entry " + name);
                        }
                    } catch (RuntimeException exception) {
                        failures.add("malformed chunk entry " + name);
                    }
                }
            }
        }
    }

    private static void verifyMetadataIdentity(
            BackupManifest manifest,
            RealmMetadataSnapshot metadata,
            List<String> failures) {
        if (manifest.realmId() != metadata.realmId()) {
            failures.add("realm metadata has a different realm ID");
        }
        if (!manifest.ownerUuid().equals(metadata.ownerUuid())) {
            failures.add("realm metadata has a different owner UUID");
        }
        if (!manifest.dimension().equals(metadata.dimension())) {
            failures.add("realm metadata has a different dimension");
        }
        if (!manifest.cellBounds().equals(metadata.allocation())) {
            failures.add("realm metadata has a different allocation");
        }
    }

    private static boolean retained(String name) {
        return "manifest.json".equals(name)
                || "realm-metadata.json".equals(name)
                || "checksums.sha256".equals(name);
    }

    private static long limit(String name) {
        if ("manifest.json".equals(name)
                || "checksums.sha256".equals(name)
                || "format.txt".equals(name)) {
            return MAX_MANIFEST_BYTES;
        }
        if ("realm-metadata.json".equals(name)) {
            return MAX_METADATA_BYTES;
        }
        if (name.startsWith("chunks/")) {
            return MAX_CHUNK_BYTES;
        }
        return MAX_MANIFEST_BYTES;
    }

    private static void validateName(String name) {
        if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains("..") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("unsafe ZIP entry name");
        }
    }
    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
