package eu.avalanche7.paradigmrealms.generation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public final class BuiltinPresetDefinitions {
    public static final RealmPresetId STARTER_ISLAND_ID = id("paradigm_realms:starter_island_v1");
    public static final RealmPresetId FLAT_GRASS_ID = id("paradigm_realms:flat_grass_v1");
    public static final RealmPresetId EMPTY_PLATFORM_ID = id("paradigm_realms:empty_platform_v1");
    public static final RealmPresetId LEGACY_PLATFORM_ID = id("paradigm_realms:platform_v1");

    public static final RealmPresetDefinition STARTER_ISLAND = definition(
            STARTER_ISLAND_ID, "starter-island-v1-r1", "Starter Island",
            "A natural permanent survival-base island.",
            new PresetRelativeSpawn(0.5, 1, 0.5, 0, 0),
            new PresetRelativeBounds(-24, -8, -24, 24, 7, 24), 4, 4,
            true, false, Set.of(), "builtin_starter_island");
    public static final RealmPresetDefinition FLAT_GRASS = definition(
            FLAT_GRASS_ID, "flat-grass-v1-r1", "Flat Grass",
            "A predictable grass plot for technical or creative building.",
            new PresetRelativeSpawn(0.5, 1, 0.5, 0, 0),
            new PresetRelativeBounds(-24, -3, -24, 23, 1, 23), 3, 3,
            true, false, Set.of(), "builtin_flat_grass");
    public static final RealmPresetDefinition EMPTY_PLATFORM = definition(
            EMPTY_PLATFORM_ID, "empty-platform-v1-r1", "Empty Platform",
            "A minimal entity-free platform for server-defined progression.",
            new PresetRelativeSpawn(0.5, 0, 0.5, 0, 0),
            new PresetRelativeBounds(-4, -1, -4, 4, 0, 4), 1, 1,
            true, false, Set.of(), "builtin_empty_platform");
    public static final RealmPresetDefinition LEGACY_PLATFORM = definition(
            LEGACY_PLATFORM_ID, "platform-v1", "Legacy Platform",
            "The original platform retained only for existing realm recovery.",
            new PresetRelativeSpawn(0.5, 0, 0.5, 0, 0),
            new PresetRelativeBounds(-4, -2, -4, 4, 0, 4), 1, 1,
            false, true, Set.of(id("platform"), id("platform_v1")), "legacy_platform");

    private BuiltinPresetDefinitions() {}

    public static List<RealmPresetDefinition> all() {
        return List.of(STARTER_ISLAND, FLAT_GRASS, EMPTY_PLATFORM, LEGACY_PLATFORM);
    }

    private static RealmPresetDefinition definition(
            RealmPresetId id,
            String revision,
            String displayName,
            String description,
            PresetRelativeSpawn spawn,
            PresetRelativeBounds bounds,
            int chunksX,
            int chunksZ,
            boolean selectable,
            boolean legacy,
            Set<RealmPresetId> aliases,
            String format) {
        return new RealmPresetDefinition(id, 1, revision, displayName, description, spawn, bounds,
                chunksX, chunksZ, selectable, legacy, aliases, Set.of(), Optional.empty(), format,
                Optional.empty(), PresetSourceType.BUILT_IN, List.of());
    }

    private static RealmPresetId id(String value) { return new RealmPresetId(value); }
}
