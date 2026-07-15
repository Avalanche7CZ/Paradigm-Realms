package eu.avalanche7.paradigmrealms.platform.wilds.offline;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import eu.avalanche7.paradigmrealms.wilds.WildsManifestCodec;
import eu.avalanche7.paradigmrealms.wilds.WildsResetManifest;

public final class WildsManifestFile {
    public static final String RELATIVE_PATH = "paradigm-realms/wilds-reset.properties";
    private final WildsManifestCodec codec = new WildsManifestCodec();

    public Path path(Path worldRoot) { return worldRoot.resolve(RELATIVE_PATH); }

    public Optional<WildsResetManifest> read(Path worldRoot) throws IOException {
        Path path = path(worldRoot);
        if (!Files.exists(path)) return Optional.empty();
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("Wilds reset manifest is not a regular non-symlink file");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        properties.stringPropertyNames().stream().sorted()
                .forEach(key -> values.put(key, properties.getProperty(key)));
        try {
            return Optional.of(codec.decode(values));
        } catch (RuntimeException exception) {
            throw new IOException("Malformed Wilds reset manifest: " + exception.getMessage(), exception);
        }
    }

    public void write(Path worldRoot, WildsResetManifest manifest) throws IOException {
        Path target = path(worldRoot);
        Files.createDirectories(target.getParent());
        if (Files.isSymbolicLink(target.getParent())) {
            throw new IOException("manifest directory is a symlink");
        }
        Properties properties = new Properties();
        for (Map.Entry<String, String> entry : codec.encode(manifest).entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
        }
        StringWriter text = new StringWriter();
        properties.store(text, "Paradigm Realms Wilds reset transaction - do not edit while pending");
        byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
