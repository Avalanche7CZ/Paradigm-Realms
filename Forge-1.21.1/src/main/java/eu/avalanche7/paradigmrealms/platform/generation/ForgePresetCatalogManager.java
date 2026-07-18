package eu.avalanche7.paradigmrealms.platform.generation;

import java.time.Instant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionConfig;
import eu.avalanche7.paradigmrealms.generation.PresetSourceType;
import eu.avalanche7.paradigmrealms.generation.RealmPresetCatalog;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportResult;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportStatus;
import eu.avalanche7.paradigmrealms.config.RealmsConfig;
import eu.avalanche7.paradigmrealms.platform.generation.importer.CompiledImportedTemplate;
import eu.avalanche7.paradigmrealms.platform.generation.importer.ForgeSchematicImportService;
import eu.avalanche7.paradigmrealms.platform.ForgeLoaderServices;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

public final class ForgePresetCatalogManager {
    private static final int MAX_PINNED_IMPORT_REVISIONS = 64;
    private final AtomicReference<Snapshot> snapshot;
    private final ForgeSchematicImportService imports;
    private final LinkedHashMap<CompiledKey, CompiledImportedTemplate> pinnedImports = new LinkedHashMap<>();
    private volatile RealmsConfig config;
    private volatile ResourceManager resources;

    public ForgePresetCatalogManager(RealmsConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.imports = new ForgeSchematicImportService(
                ForgeLoaderServices.getInstance().getConfigDir(), config.schematicImportPolicy());
        this.snapshot = new AtomicReference<>(new Snapshot(
                RealmPresetCatalog.builtIns(), config.presetSelection(), config.allowExternalPresets(), Map.of(), Instant.EPOCH));
    }

    public Snapshot snapshot() { return snapshot.get(); }

    public RealmPresetCatalog catalog() { return snapshot.get().catalog(); }

    public synchronized Snapshot reload(ResourceManager resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
        RealmsConfig current = config;
        RealmPresetCatalog resourceCatalog = new ForgePresetResourceLoader(
                resources, current.allowExternalPresets()).load();
        ForgeSchematicImportService.LoadedImports imported = imports.loadBoundImports();
        List<eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition> definitions =
                new ArrayList<>(resourceCatalog.all());
        List<String> issues = new ArrayList<>(resourceCatalog.loadIssues());
        issues.addAll(imported.issues());
        LinkedHashMap<RealmPresetId, CompiledImportedTemplate> accepted = new LinkedHashMap<>();
        imported.compiled().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RealmPresetId::value)))
                .forEach(entry -> {
                    try {
                        new RealmPresetCatalog(concat(definitions, entry.getValue().preset()));
                        definitions.add(entry.getValue().preset());
                        accepted.put(entry.getKey(), entry.getValue());
                    } catch (IllegalArgumentException conflict) {
                        issues.add(entry.getKey().value() + ": imported preset conflict: " + conflict.getMessage());
                    }
                });
        RealmPresetCatalog loaded = new RealmPresetCatalog(definitions, issues);
        Snapshot replacement = new Snapshot(
                loaded, current.presetSelection(), current.allowExternalPresets(), accepted, Instant.now());
        snapshot.set(replacement);
        accepted.forEach((id, compiled) -> pin(compiled));
        return replacement;
    }

    public List<String> importFiles() { return imports.listFiles(); }

    public Map<RealmPresetId, String> importBindings() throws IOException { return imports.bindings(); }

    public PresetImportResult inspectImport(String sourceFile) { return imports.inspect(sourceFile); }

    public synchronized PresetImportResult importPreset(String sourceFile, RealmPresetId presetId) {
        try {
            if (imports.bindings().containsKey(presetId)) {
                return rejected(presetId, sourceFile, "imported preset binding already exists; use reimport or remove");
            }
        } catch (IOException exception) {
            return rejected(presetId, sourceFile, "cannot read imported preset bindings: " + safe(exception));
        }
        var existing = snapshot().catalog().findExact(presetId);
        if (existing.isPresent()) return rejected(presetId, sourceFile,
                existing.orElseThrow().sourceType() == PresetSourceType.IMPORTED
                        ? "imported preset already exists; use reimport"
                        : "preset ID conflicts with an existing catalog definition");
        var attempt = imports.compile(sourceFile, presetId);
        if (attempt.compiled().isEmpty()) return attempt.result();
        try {
            imports.bind(presetId, sourceFile);
            reload(requiredResources());
            CompiledImportedTemplate compiled = snapshot().compiledImports().get(presetId);
            if (compiled == null) {
                imports.unbind(presetId);
                reload(requiredResources());
                return rejected(presetId, sourceFile, "compiled import could not be published without a catalog conflict");
            }
            return imports.published(compiled);
        } catch (IOException | RuntimeException exception) {
            String rollback = rollbackBinding(presetId, Optional.empty());
            return rejected(presetId, sourceFile, "publication failed: " + safe(exception) + rollback);
        }
    }

    public synchronized PresetImportResult removeImport(RealmPresetId presetId) {
        String source = "unknown";
        try {
            source = imports.bindings().get(presetId);
            if (source == null) return notFound(presetId, "unknown");
            imports.unbind(presetId);
            reload(requiredResources());
            return imports.removed(presetId, source);
        } catch (IOException | RuntimeException exception) {
            String rollback = "unknown".equals(source) ? "" : rollbackBinding(presetId, Optional.of(source));
            return rejected(presetId, source, "removal failed: " + safe(exception) + rollback);
        }
    }

    public synchronized PresetImportResult reimport(RealmPresetId presetId) {
        try {
            String source = imports.bindings().get(presetId);
            if (source == null) return notFound(presetId, "unknown");
            var attempt = imports.compile(source, presetId);
            if (attempt.compiled().isEmpty()) return attempt.result();
            reload(requiredResources());
            CompiledImportedTemplate compiled = snapshot().compiledImports().get(presetId);
            return compiled == null ? rejected(presetId, source, "recompiled preset was not published")
                    : imports.published(compiled);
        } catch (IOException | RuntimeException exception) {
            return rejected(presetId, "unknown", "reimport failed: " + safe(exception));
        }
    }

    public synchronized Optional<CompiledImportedTemplate> compiledImport(RealmPresetId id, String revision) {
        return Optional.ofNullable(pinnedImports.get(new CompiledKey(id, revision)));
    }

    public void updateConfiguration(RealmsConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.imports.updatePolicy(config.schematicImportPolicy());
    }

    private ResourceManager requiredResources() {
        if (resources == null) throw new IllegalStateException("preset resources are not initialized");
        return resources;
    }
    private void pin(CompiledImportedTemplate compiled) {
        CompiledKey key = new CompiledKey(compiled.preset().id(), compiled.preset().revision());
        pinnedImports.remove(key);
        pinnedImports.put(key, compiled);
        while (pinnedImports.size() > MAX_PINNED_IMPORT_REVISIONS) {
            pinnedImports.remove(pinnedImports.keySet().iterator().next());
        }
    }
    private String rollbackBinding(RealmPresetId id, Optional<String> source) {
        try {
            if (source.isPresent()) imports.bind(id, source.orElseThrow());
            else imports.unbind(id);
            reload(requiredResources());
            return "";
        } catch (IOException | RuntimeException rollbackFailure) {
            return "; binding rollback also failed: " + safe(rollbackFailure);
        }
    }
    private static List<eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition> concat(
            List<eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition> values,
            eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition value) {
        List<eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition> copy = new ArrayList<>(values);
        copy.add(value);
        return copy;
    }
    private static PresetImportResult rejected(RealmPresetId id, String file, String reason) {
        return new PresetImportResult(PresetImportStatus.REJECTED, Optional.of(id), file,
                Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, List.of(), List.of(reason));
    }
    private static PresetImportResult notFound(RealmPresetId id, String file) {
        return new PresetImportResult(PresetImportStatus.NOT_FOUND, Optional.of(id), file,
                Optional.empty(), Optional.empty(), Optional.empty(), 0, 0, List.of(), List.of("imported preset is not bound"));
    }
    private static String safe(Exception exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }
    private record CompiledKey(RealmPresetId id, String revision) {}

    public record Snapshot(
            RealmPresetCatalog catalog,
            PresetSelectionConfig selection,
            boolean allowExternalPresets,
            Map<RealmPresetId, CompiledImportedTemplate> compiledImports,
            Instant loadedAt) {
        public Snapshot {
            Objects.requireNonNull(catalog, "catalog");
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(compiledImports, "compiledImports");
            Objects.requireNonNull(loadedAt, "loadedAt");
            compiledImports = Map.copyOf(compiledImports);
        }
    }

    public static final class ReloadListener extends SinglePreparationResourceReloader<Void> {
        private final ForgePresetCatalogManager manager;
        public ReloadListener(ForgePresetCatalogManager manager) { this.manager = manager; }

        public Identifier id() {
            return Identifier.of(ParadigmRealms.MOD_ID, "realm_presets");
        }

        @Override protected Void prepare(ResourceManager resourceManager, Profiler profiler) {
            return null;
        }

        @Override protected void apply(Void prepared, ResourceManager resourceManager, Profiler profiler) {
            Snapshot snapshot = manager.reload(resourceManager);
            ParadigmRealms.LOGGER.info(
                    "Published {} realm preset definition(s), with {} isolated issue(s)",
                    snapshot.catalog().all().size(), snapshot.catalog().loadIssues().size());
            snapshot.catalog().loadIssues().forEach(issue ->
                    ParadigmRealms.LOGGER.warn("Preset: {}", issue));
        }

        @Override public String getName() { return id().toString(); }
    }
}
