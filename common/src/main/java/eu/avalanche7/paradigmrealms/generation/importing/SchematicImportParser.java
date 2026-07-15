package eu.avalanche7.paradigmrealms.generation.importing;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.generation.importing.adapter.LitematicaSchematicAdapter;
import eu.avalanche7.paradigmrealms.generation.importing.adapter.MCEditSchematicAdapter;
import eu.avalanche7.paradigmrealms.generation.importing.adapter.SchematicFormatAdapter;
import eu.avalanche7.paradigmrealms.generation.importing.adapter.SpongeSchematicAdapter;
import eu.avalanche7.paradigmrealms.generation.importing.adapter.VanillaStructureAdapter;

public final class SchematicImportParser {
    private final BoundedImportFileReader files;
    private final SchematicFormatDetector detector = new SchematicFormatDetector();
    private final NormalizedTemplateValidator validator = new NormalizedTemplateValidator();
    private final List<SchematicFormatAdapter> adapters;
    public SchematicImportParser(Path importRoot, LegacyBlockMapping legacyMapping) {
        this.files = new BoundedImportFileReader(importRoot);
        this.adapters = List.of(new SpongeSchematicAdapter(), new LitematicaSchematicAdapter(),
                new VanillaStructureAdapter(), new MCEditSchematicAdapter(legacyMapping));
    }
    public ParsedSchematic parse(String sourceFile) throws IOException {
        return parse(sourceFile, ImportPolicy.STRICT);
    }
    public ParsedSchematic parse(String sourceFile, ImportPolicy policy) throws IOException {
        Objects.requireNonNull(policy, "policy");
        BoundedImportFileReader.LoadedImportFile loaded = files.read(sourceFile);
        SchematicFormatDetector.DetectedFormat detected = detector.detect(loaded.root());
        SchematicFormatAdapter adapter = adapters.stream().filter(value -> value.supports(detected.format()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("no adapter for detected format"));
        return new ParsedSchematic(sourceFile, loaded.fingerprint(),
                validator.validate(adapter.parse(detected.root(), detected.format(), policy), policy));
    }
    public Path importRoot() { return files.root(); }
    public record ParsedSchematic(String sourceFile, String fingerprint, StructurallyValidatedTemplate validated) {
        public NormalizedImportedTemplate template() { return validated.template(); }
    }
}
