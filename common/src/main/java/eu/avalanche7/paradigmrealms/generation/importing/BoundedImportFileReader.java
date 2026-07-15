package eu.avalanche7.paradigmrealms.generation.importing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.GenericNbtReader;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;

public final class BoundedImportFileReader {
    private static final Set<String> EXTENSIONS = Set.of(".schem", ".litematic", ".nbt", ".schematic", ".schematics");
    private final Path root;
    private final GenericNbtReader nbtReader = new GenericNbtReader();

    public BoundedImportFileReader(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public Path root() { return root; }

    public LoadedImportFile read(String fileName) throws IOException {
        Path file = resolve(fileName);
        long size = Files.size(file);
        if (size < 1 || size > ImportLimits.MAX_COMPRESSED_BYTES) throw new IOException("compressed file size must be between 1 byte and 16 MiB");
        byte[] source = Files.readAllBytes(file);
        byte[] expanded = gzip(source) ? decompress(source) : source;
        NbtCompoundTag rootTag = nbtReader.read(expanded);
        return new LoadedImportFile(file, rootTag, sha256(source));
    }

    public Path resolve(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) throw new IOException("filename is required");
        Path relative;
        try { relative = Path.of(fileName); } catch (RuntimeException exception) { throw new IOException("invalid filename", exception); }
        if (relative.isAbsolute()) throw new IOException("absolute paths are not allowed");
        for (Path part : relative) if (part.toString().equals("..")) throw new IOException("parent traversal is not allowed");
        String lower = relative.getFileName().toString().toLowerCase(Locale.ROOT);
        if (EXTENSIONS.stream().noneMatch(lower::endsWith)) throw new IOException("unsupported schematic extension");
        Files.createDirectories(root);
        Path normalized = root.resolve(relative).normalize();
        if (!normalized.startsWith(root)) throw new IOException("path escapes the import directory");
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) throw new IOException("file is missing, not regular, or is a symbolic link");
        Path realRoot = root.toRealPath();
        Path realFile = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realFile.startsWith(realRoot)) throw new IOException("symbolic link escapes the import directory");
        return realFile;
    }

    public static boolean supportedExtension(String value) {
        String lower = value.toLowerCase(Locale.ROOT); return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }
    private static byte[] decompress(byte[] source) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(source));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int total = 0, read;
            while ((read = gzip.read(buffer)) >= 0) {
                total = Math.addExact(total, read);
                if (total > ImportLimits.MAX_DECOMPRESSED_BYTES) throw new IOException("decompressed data exceeds 64 MiB limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (ArithmeticException exception) { throw new IOException("decompressed size overflow", exception); }
    }
    private static boolean gzip(byte[] value) { return value.length >= 2 && (value[0] & 255) == 0x1f && (value[1] & 255) == 0x8b; }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record LoadedImportFile(Path file, NbtCompoundTag root, String fingerprint) {}
}
