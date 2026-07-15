package eu.avalanche7.paradigmrealms.platform.generation.importer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.generation.importing.BoundedImportFileReader;
import eu.avalanche7.paradigmrealms.generation.importing.ImportedPresetBindingStore;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.LegacyMappingFile;
import eu.avalanche7.paradigmrealms.generation.importing.SchematicImportParser;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportResult;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportStatus;

public final class FabricSchematicImportService {
    private static final RealmPresetId INSPECTION_ID = new RealmPresetId("paradigm_realms:inspection_only");
    private final Path importRoot;
    private final SchematicImportParser parser;
    private final ImportedPresetBindingStore bindings;
    private final ImportedTemplateCompiler compiler = new ImportedTemplateCompiler();
    private volatile ImportPolicy policy;

    public FabricSchematicImportService(Path configDirectory, ImportPolicy policy) {
        Path root = configDirectory.resolve("paradigm-realms");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.importRoot = root.resolve("imports").toAbsolutePath().normalize();
        this.bindings = new ImportedPresetBindingStore(root.resolve("imported-presets.bindings"));
        try {
            this.parser = new SchematicImportParser(importRoot,
                    new LegacyMappingFile().load(root.resolve("legacy-block-mappings.properties")));
        } catch (IOException exception) {
            throw new IllegalStateException("cannot load legacy schematic mappings: " + exception.getMessage(), exception);
        }
    }

    public Path importDirectory() { return importRoot; }

    public List<String> listFiles() {
        try {
            Files.createDirectories(importRoot);
            try (Stream<Path> files = Files.walk(importRoot, 8)) {
                return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .map(importRoot::relativize).map(Path::toString)
                        .filter(this::supportedExtension)
                        .sorted().toList();
            }
        } catch (IOException exception) {
            return List.of();
        }
    }

    public Map<RealmPresetId, String> bindings() throws IOException { return bindings.load(); }

    public PresetImportResult inspect(String sourceFile) {
        ImportAttempt attempt = compile(sourceFile, INSPECTION_ID);
        return attempt.result();
    }

    public ImportAttempt compile(String sourceFile, RealmPresetId presetId) {
        try {
            presetId.requireNamespaced();
            ImportPolicy currentPolicy = policy;
            SchematicImportParser.ParsedSchematic parsed = parser.parse(sourceFile, currentPolicy);
            CompiledImportedTemplate compiled = compiler.compile(
                    parsed.validated(), presetId, sourceFile, parsed.fingerprint(), currentPolicy);
            PresetImportResult result = result(PresetImportStatus.VALID,
                    presetId.equals(INSPECTION_ID) ? Optional.empty() : Optional.of(presetId), sourceFile,
                    compiled, List.of());
            return new ImportAttempt(result, Optional.of(compiled));
        } catch (Exception exception) {
            return new ImportAttempt(new PresetImportResult(PresetImportStatus.REJECTED,
                    presetId.equals(INSPECTION_ID) ? Optional.empty() : Optional.of(presetId), sourceFile,
                    Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, List.of(),
                    List.of(safeMessage(exception))), Optional.empty());
        }
    }

    public LoadedImports loadBoundImports() {
        List<String> issues = new ArrayList<>();
        Map<RealmPresetId, CompiledImportedTemplate> compiled = new LinkedHashMap<>();
        Map<RealmPresetId, String> current;
        try { current = bindings.load(); }
        catch (IOException exception) { return new LoadedImports(Map.of(), List.of(exception.getMessage())); }
        current.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(RealmPresetId::value)))
                .forEach(entry -> {
                    ImportAttempt attempt = compile(entry.getValue(), entry.getKey());
                    if (attempt.compiled().isPresent()) compiled.put(entry.getKey(), attempt.compiled().orElseThrow());
                    else issues.add(entry.getKey().value() + " from " + entry.getValue() + ": "
                            + String.join("; ", attempt.result().errors()));
                });
        return new LoadedImports(Map.copyOf(compiled), List.copyOf(issues));
    }

    public void bind(RealmPresetId id, String sourceFile) throws IOException { bindings.put(id, sourceFile); }
    public boolean unbind(RealmPresetId id) throws IOException { return bindings.remove(id); }
    public void updatePolicy(ImportPolicy policy) { this.policy = java.util.Objects.requireNonNull(policy, "policy"); }

    public PresetImportResult published(CompiledImportedTemplate compiled) {
        return result(PresetImportStatus.PUBLISHED, Optional.of(compiled.preset().id()),
                compiled.sourceFile(), compiled, List.of());
    }

    public PresetImportResult removed(RealmPresetId id, String sourceFile) {
        return new PresetImportResult(PresetImportStatus.REMOVED, Optional.of(id), sourceFile,
                Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, List.of(), List.of());
    }

    private static PresetImportResult result(
            PresetImportStatus status, Optional<RealmPresetId> id, String file,
            CompiledImportedTemplate compiled, List<String> errors) {
        return new PresetImportResult(status, id, file, Optional.of(compiled.format()),
                compiled.preset().fingerprint(), Optional.of(compiled.preset().bounds()), compiled.blocks().size(),
                compiled.sanitizedBlockEntityCount(), compiled.warnings(), errors);
    }

    private boolean supportedExtension(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return BoundedImportFileReader.supportedExtension(lower);
    }
    private static String safeMessage(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
        value = value.replaceAll("[\\r\\n\\t]", " ");
        return value.length() > 400 ? value.substring(0, 400) : value;
    }

    public record ImportAttempt(PresetImportResult result, Optional<CompiledImportedTemplate> compiled) {}
    public record LoadedImports(Map<RealmPresetId, CompiledImportedTemplate> compiled, List<String> issues) {}
}
