package eu.avalanche7.paradigmrealms.platform.generation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.generation.BuiltinPresetDefinitions;
import eu.avalanche7.paradigmrealms.generation.ExternalPresetDefinitionFactory;
import eu.avalanche7.paradigmrealms.generation.ExternalPresetMetadata;
import eu.avalanche7.paradigmrealms.generation.PresetRelativeBounds;
import eu.avalanche7.paradigmrealms.generation.PresetRelativeSpawn;
import eu.avalanche7.paradigmrealms.generation.RealmPresetCatalog;
import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.generation.StructureResourceValidation;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.state.property.Property;

public final class FabricPresetResourceLoader {
    public static final String METADATA_ROOT = "realm_presets";
    public static final String STRUCTURE_ROOT = "structure";
    private static final int MAX_STRUCTURE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_STRUCTURE_BLOCKS = 250_000;
    private static final Set<String> FORBIDDEN_BLOCKS = Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:end_portal",
            "minecraft:end_gateway", "minecraft:end_portal_frame");

    private final ResourceManager resources;
    private final boolean allowExternalPresets;
    private final Predicate<String> modLoaded;
    private final boolean validateMinecraftRegistries;
    private final ExternalPresetDefinitionFactory definitions = new ExternalPresetDefinitionFactory();

    public FabricPresetResourceLoader(ResourceManager resources, boolean allowExternalPresets) {
        this(resources, allowExternalPresets, FabricLoader.getInstance()::isModLoaded, true);
    }

    FabricPresetResourceLoader(
            ResourceManager resources,
            boolean allowExternalPresets,
            Predicate<String> modLoaded,
            boolean validateMinecraftRegistries) {
        this.resources = resources;
        this.allowExternalPresets = allowExternalPresets;
        this.modLoaded = modLoaded;
        this.validateMinecraftRegistries = validateMinecraftRegistries;
    }

    public RealmPresetCatalog load() {
        List<RealmPresetDefinition> definitions = new ArrayList<>(BuiltinPresetDefinitions.all());
        List<String> issues = new ArrayList<>();
        var found = resources.findResources(METADATA_ROOT,
                id -> id.getPath().endsWith(".json"));
        found.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            String source = entry.getKey().toString();
            try {
                RealmPresetDefinition definition = parse(entry.getKey(), entry.getValue());
                try {
                    new RealmPresetCatalog(concat(definitions, definition));
                    definitions.add(definition);
                } catch (IllegalArgumentException conflict) {
                    issues.add(source + ": ignored conflicting definition: " + conflict.getMessage());
                }
            } catch (RuntimeException | IOException exception) {
                issues.add(source + ": malformed preset isolated: " + safeMessage(exception));
            }
        });
        return new RealmPresetCatalog(definitions, issues);
    }

    private RealmPresetDefinition parse(Identifier metadataId, Resource metadataResource) throws IOException {
        JsonObject json;
        try (Reader reader = metadataResource.getReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("metadata root must be an object");
            json = parsed.getAsJsonObject();
        }
        ExternalPresetMetadata metadata = new ExternalPresetMetadata(
                id(requiredString(json, "id")), requiredInt(json, "version"),
                requiredString(json, "displayName"), requiredString(json, "description"),
                id(requiredString(json, "structure")), spawn(json.getAsJsonObject("spawn")),
                bounds(json.getAsJsonObject("bounds")), ids(json.getAsJsonArray("aliases")),
                strings(json.getAsJsonArray("requiredMods")),
                optionalBoolean(json, "selectable", true), optionalBoolean(json, "legacy", false));
        StructureResourceValidation validation = validateStructure(
                metadata.structure(), metadata.bounds());
        return definitions.create(metadata, metadataId.getNamespace(), allowExternalPresets,
                modLoaded, validation);
    }

    private StructureResourceValidation validateStructure(
            RealmPresetId structure, PresetRelativeBounds bounds) {
        Identifier resourceId = Identifier.of(structure.namespace(),
                STRUCTURE_ROOT + "/" + structure.path() + ".nbt");
        Optional<Resource> resource = resources.getResource(resourceId);
        if (resource.isEmpty()) {
            return StructureResourceValidation.failed("missing structure resource data/" + structure.namespace()
                    + "/structure/" + structure.path() + ".nbt");
        }
        try (var stream = resource.orElseThrow().getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_STRUCTURE_BYTES + 1);
            if (bytes.length > MAX_STRUCTURE_BYTES) {
                return StructureResourceValidation.failed("structure exceeds 8 MiB limit");
            }
            String fingerprint = sha256(bytes);
            NbtCompound root = NbtIo.readCompressed(
                    new ByteArrayInputStream(bytes), NbtSizeTracker.of(MAX_STRUCTURE_BYTES));
            List<String> reasons = new ArrayList<>(inspectStructure(root, bounds));
            if (validateMinecraftRegistries) reasons.addAll(inspectRegistryPalette(root));
            return new StructureResourceValidation(Optional.of(fingerprint), List.copyOf(reasons));
        } catch (Exception exception) {
            return StructureResourceValidation.failed(
                    "structure could not be decoded safely: " + safeMessage(exception));
        }
    }

    static List<String> inspectStructure(NbtCompound root, PresetRelativeBounds bounds) {
        List<String> reasons = new ArrayList<>();
        NbtList size = root.getList("size", NbtElement.INT_TYPE);
        if (size.size() != 3 || size.getInt(0) != bounds.width()
                || size.getInt(1) != bounds.height() || size.getInt(2) != bounds.depth()) {
            reasons.add("structure size does not exactly match declared inclusive bounds");
        }
        if (!root.getList("entities", NbtElement.COMPOUND_TYPE).isEmpty()) {
            reasons.add("entities and passenger trees are not allowed");
        }
        if (root.contains("palettes")) reasons.add("multi-palette structures are not supported");
        NbtList palette = root.getList("palette", NbtElement.COMPOUND_TYPE);
        if (palette.isEmpty()) reasons.add("structure palette is missing or empty");
        for (int index = 0; index < palette.size(); index++) {
            String blockName = palette.getCompound(index).getString("Name");
            Identifier blockId = Identifier.tryParse(blockName);
            if (blockId == null) {
                reasons.add("invalid block identifier in palette: " + blockName);
            } else if (FORBIDDEN_BLOCKS.contains(blockId.toString())) {
                reasons.add("forbidden block in palette: " + blockId);
            }
        }
        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        if (blocks.size() > MAX_STRUCTURE_BLOCKS) {
            reasons.add("structure exceeds the 250000-block synchronous placement limit");
        }
        for (int index = 0; index < blocks.size(); index++) {
            NbtCompound block = blocks.getCompound(index);
            int state = block.getInt("state");
            if (state < 0 || state >= palette.size()) reasons.add("block references invalid palette state " + state);
            NbtList position = block.getList("pos", NbtElement.INT_TYPE);
            if (position.size() != 3 || position.getInt(0) < 0 || position.getInt(0) >= bounds.width()
                    || position.getInt(1) < 0 || position.getInt(1) >= bounds.height()
                    || position.getInt(2) < 0 || position.getInt(2) >= bounds.depth()) {
                reasons.add("structure contains a block position outside declared dimensions");
            }
            if (block.contains("nbt")) {
                reasons.add("block-entity NBT is rejected (including containers, inventories and loot tables)");
                break;
            }
        }
        return reasons.stream().distinct().sorted().toList();
    }

    private static List<String> inspectRegistryPalette(NbtCompound root) {
        List<String> reasons = new ArrayList<>();
        NbtList palette = root.getList("palette", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < palette.size(); index++) {
            NbtCompound entry = palette.getCompound(index);
            String blockName = entry.getString("Name");
            Identifier blockId = Identifier.tryParse(blockName);
            if (blockId == null || !Registries.BLOCK.containsId(blockId)) {
                reasons.add("unknown block in palette: " + blockName);
                continue;
            }
            if (entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                NbtCompound properties = entry.getCompound("Properties");
                var stateManager = Registries.BLOCK.get(blockId).getStateManager();
                for (String name : properties.getKeys()) {
                    Property<?> property = stateManager.getProperty(name);
                    if (property == null || !validProperty(property, properties.getString(name))) {
                        reasons.add("invalid block-state property " + blockId + "[" + name + "]");
                    }
                }
            }
        }
        return reasons;
    }

    private static PresetRelativeSpawn spawn(JsonObject value) {
        if (value == null) throw new IllegalArgumentException("missing spawn object");
        return new PresetRelativeSpawn(requiredDouble(value, "x"), requiredDouble(value, "y"),
                requiredDouble(value, "z"), optionalFloat(value, "yaw", 0), optionalFloat(value, "pitch", 0));
    }

    private static PresetRelativeBounds bounds(JsonObject value) {
        if (value == null) throw new IllegalArgumentException("missing bounds object");
        return new PresetRelativeBounds(requiredInt(value, "minX"), requiredInt(value, "minY"),
                requiredInt(value, "minZ"), requiredInt(value, "maxX"), requiredInt(value, "maxY"),
                requiredInt(value, "maxZ"));
    }

    private static Set<RealmPresetId> ids(JsonArray values) {
        if (values == null) return Set.of();
        Set<RealmPresetId> result = new HashSet<>();
        values.forEach(value -> result.add(id(value.getAsString())));
        return Set.copyOf(result);
    }

    private static Set<String> strings(JsonArray values) {
        if (values == null) return Set.of();
        Set<String> result = new HashSet<>();
        values.forEach(value -> result.add(value.getAsString()));
        return Set.copyOf(result);
    }

    private static RealmPresetId id(String value) { return new RealmPresetId(value); }
    private static <T extends Comparable<T>> boolean validProperty(Property<T> property, String value) {
        return property.parse(value).isPresent();
    }
    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing " + key);
        return json.get(key).getAsString();
    }
    private static int requiredInt(JsonObject json, String key) {
        if (json == null || !json.has(key)) throw new IllegalArgumentException("missing " + key);
        return json.get(key).getAsInt();
    }
    private static double requiredDouble(JsonObject json, String key) {
        if (json == null || !json.has(key)) throw new IllegalArgumentException("missing " + key);
        return json.get(key).getAsDouble();
    }
    private static boolean optionalBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
    private static float optionalFloat(JsonObject json, String key, float fallback) {
        return json.has(key) ? json.get(key).getAsFloat() : fallback;
    }
    private static List<RealmPresetDefinition> concat(
            List<RealmPresetDefinition> values, RealmPresetDefinition additional) {
        List<RealmPresetDefinition> copy = new ArrayList<>(values);
        copy.add(additional);
        return copy;
    }
    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String safeMessage(Exception exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
        value = value.replaceAll("[\\r\\n\\t]", " ");
        return value.length() > 180 ? value.substring(0, 180) : value;
    }

}
