package eu.avalanche7.paradigmrealms.generation.importing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public final class ImportedPresetBindingStore {
    private static final String HEADER = "paradigm-realms-import-bindings-v1";
    private static final long MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_BINDINGS = 10_000;
    private final Path file;

    public ImportedPresetBindingStore(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public synchronized Map<RealmPresetId, String> load() throws IOException {
        if (!Files.isRegularFile(file)) return Map.of();
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("imported-preset binding file exceeds 1 MiB");
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !HEADER.equals(lines.getFirst())) {
            throw new IOException("unsupported imported-preset binding schema");
        }
        if (lines.size() - 1 > MAX_BINDINGS) throw new IOException("imported-preset binding count exceeds limit");
        Map<RealmPresetId, String> result = new TreeMap<>(Comparator.comparing(RealmPresetId::value));
        try {
            for (int index = 1; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.isBlank()) continue;
                int delimiter = line.indexOf('\t');
                if (delimiter < 1 || delimiter != line.lastIndexOf('\t')) {
                    throw new IOException("malformed imported-preset binding at line " + (index + 1));
                }
                RealmPresetId id = new RealmPresetId(line.substring(0, delimiter));
                id.requireNamespaced();
                String source = decode(line.substring(delimiter + 1));
                if (source.isBlank()) throw new IOException("blank imported-preset source at line " + (index + 1));
                if (result.putIfAbsent(id, source) != null) {
                    throw new IOException("duplicate imported-preset binding " + id.value());
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new IOException("malformed imported-preset binding file: " + exception.getMessage(), exception);
        }
        return Map.copyOf(result);
    }

    public synchronized void put(RealmPresetId id, String sourceFile) throws IOException {
        id.requireNamespaced();
        if (sourceFile == null || sourceFile.isBlank()) throw new IOException("imported-preset source is required");
        Map<RealmPresetId, String> values = new LinkedHashMap<>(load());
        values.put(id, sourceFile);
        if (values.size() > MAX_BINDINGS) throw new IOException("imported-preset binding count exceeds limit");
        save(values);
    }

    public synchronized boolean remove(RealmPresetId id) throws IOException {
        Map<RealmPresetId, String> values = new LinkedHashMap<>(load());
        boolean removed = values.remove(id) != null;
        if (removed) save(values);
        return removed;
    }

    private void save(Map<RealmPresetId, String> values) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder output = new StringBuilder(HEADER).append('\n');
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(RealmPresetId::value)))
                .forEach(entry -> output.append(entry.getKey().value()).append('\t')
                        .append(Base64.getUrlEncoder().withoutPadding().encodeToString(
                                entry.getValue().getBytes(StandardCharsets.UTF_8)))
                        .append('\n'));
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, output, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String decode(String encoded) throws IOException {
        byte[] bytes;
        try { bytes = Base64.getUrlDecoder().decode(encoded); }
        catch (IllegalArgumentException exception) { throw new IOException("invalid source encoding", exception); }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("source filename is not valid UTF-8", exception);
        }
    }
}
