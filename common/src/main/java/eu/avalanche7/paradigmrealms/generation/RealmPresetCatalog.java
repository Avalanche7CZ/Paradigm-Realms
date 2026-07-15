package eu.avalanche7.paradigmrealms.generation;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public final class RealmPresetCatalog {
    private final Map<RealmPresetId, RealmPresetDefinition> definitions;
    private final Map<RealmPresetId, RealmPresetId> aliases;
    private final List<String> loadIssues;

    public RealmPresetCatalog(Collection<RealmPresetDefinition> definitions, Collection<String> loadIssues) {
        Objects.requireNonNull(definitions, "definitions");
        LinkedHashMap<RealmPresetId, RealmPresetDefinition> indexed = new LinkedHashMap<>();
        LinkedHashMap<RealmPresetId, RealmPresetId> aliasIndex = new LinkedHashMap<>();
        definitions.stream().sorted(Comparator.comparing(value -> value.id().value())).forEach(definition -> {
            if (indexed.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("duplicate preset ID " + definition.id());
            }
        });
        indexed.values().forEach(definition -> {
            for (RealmPresetId alias : definition.aliases()) {
                if (indexed.containsKey(alias) || aliasIndex.putIfAbsent(alias, definition.id()) != null) {
                    throw new IllegalArgumentException("ambiguous preset alias " + alias);
                }
            }
        });
        if (indexed.isEmpty()) throw new IllegalArgumentException("at least one preset is required");
        this.definitions = Map.copyOf(indexed);
        this.aliases = Map.copyOf(aliasIndex);
        this.loadIssues = List.copyOf(Objects.requireNonNull(loadIssues, "loadIssues"));
    }

    public RealmPresetCatalog(Collection<RealmPresetDefinition> definitions) {
        this(definitions, List.of());
    }

    public static RealmPresetCatalog builtIns() {
        return new RealmPresetCatalog(BuiltinPresetDefinitions.all());
    }

    public Optional<RealmPresetDefinition> findExact(RealmPresetId id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Optional<RealmPresetDefinition> resolve(RealmPresetId idOrAlias) {
        RealmPresetDefinition exact = definitions.get(idOrAlias);
        if (exact != null) return Optional.of(exact);
        RealmPresetId canonical = aliases.get(idOrAlias);
        return canonical == null ? Optional.empty() : Optional.ofNullable(definitions.get(canonical));
    }

    public List<RealmPresetDefinition> all() {
        return definitions.values().stream().sorted(Comparator.comparing(value -> value.id().value())).toList();
    }

    public List<String> loadIssues() { return loadIssues; }
}
