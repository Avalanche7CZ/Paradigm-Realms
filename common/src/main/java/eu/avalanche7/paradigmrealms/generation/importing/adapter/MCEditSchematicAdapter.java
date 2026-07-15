package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import eu.avalanche7.paradigmrealms.generation.importing.ImportedTemplateFormat;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.LegacyBlockMapping;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate;
import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

public final class MCEditSchematicAdapter implements SchematicFormatAdapter {
    private final LegacyBlockMapping mapping;
    public MCEditSchematicAdapter(LegacyBlockMapping mapping) { this.mapping = mapping; }
    @Override public boolean supports(ImportedTemplateFormat format) { return format == ImportedTemplateFormat.MCEDIT_SCHEMATIC; }
    @Override public NormalizedImportedTemplate parse(
            NbtCompoundTag root, ImportedTemplateFormat format, ImportPolicy policy) {
        if (!"Alpha".equals(root.string("Materials"))) throw new IllegalArgumentException("only MCEdit Alpha materials are supported");
        int width = ImportNbtSupport.unsignedShort(root, "Width"), height = ImportNbtSupport.unsignedShort(root, "Height");
        int depth = ImportNbtSupport.unsignedShort(root, "Length");
        int volume = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) width, height), depth));
        byte[] ids = root.byteArray("Blocks"), data = root.byteArray("Data");
        if (ids.length != volume || data.length != volume) throw new IllegalArgumentException("legacy Blocks/Data length does not match dimensions");
        byte[] high = root.contains("AddBlocks", NbtType.BYTE_ARRAY) ? root.byteArray("AddBlocks") : new byte[0];
        if (high.length != 0 && high.length != (volume + 1) / 2) throw new IllegalArgumentException("legacy AddBlocks length is invalid");
        List<String> warnings = new ArrayList<>();
        ImportPolicyActions.entities(policy, root.list("Entities", NbtType.COMPOUND).size(),
                "MCEdit/Schematica source", warnings);
        for (String key : List.of("TileTicks", "BlockTicks", "FluidTicks")) if (root.contains(key, NbtType.LIST)
                && !root.list(key, NbtType.COMPOUND).isEmpty()) throw new IllegalArgumentException("scheduled ticks are not allowed");
        Map<ImportNbtSupport.Position, NbtCompoundTag> blockEntities = ImportNbtSupport.positionedCompounds(
                root.list("TileEntities", NbtType.COMPOUND));
        Map<String, Integer> unmapped = new TreeMap<>(); List<NormalizedImportedTemplate.NormalizedBlock> blocks = new ArrayList<>(volume);
        for (int index = 0; index < volume; index++) {
            int id = legacyId(ids, high, index);
            int metadata = data[index] & 15;
            Optional<SymbolicBlockState> state = mapping.override(id, metadata);
            if (state.isEmpty() && door(id)) state = Optional.of(doorState(ids, data, high, width, depth, index, id));
            if (state.isEmpty()) state = mapping.resolve(id, metadata);
            if (state.isEmpty()) { unmapped.merge(id + ":" + metadata, 1, Integer::sum); continue; }
            int x = index % width, z = (index / width) % depth, y = index / (width * depth);
            blocks.add(new NormalizedImportedTemplate.NormalizedBlock(x, y, z, state.orElseThrow(),
                    Optional.ofNullable(blockEntities.remove(new ImportNbtSupport.Position(x, y, z)))));
        }
        if (!unmapped.isEmpty()) throw new IllegalArgumentException("unmapped legacy block states: " + unmapped);
        ImportPolicyActions.outOfBoundsBlockEntities(
                policy, blockEntities.size(), "MCEdit/Schematica volume", warnings);
        warnings.add("MCEdit/Schematica numeric conversion is best-effort legacy compatibility");
        return new NormalizedImportedTemplate(format, -1, width, height, depth, blocks,
                List.of(new NormalizedImportedTemplate.ImportedRegion("Schematic", 0, 0, 0, width, height, depth)),
                warnings);
    }
    private static int legacyId(byte[] ids, byte[] high, int index) {
        int id = ids[index] & 255;
        if (high.length != 0) {
            int packed = high[index >>> 1] & 255;
            id |= ((index & 1) == 0 ? packed & 15 : (packed >>> 4) & 15) << 8;
        }
        return id;
    }
    private static boolean door(int id) { return id == 64 || id == 71 || (id >= 193 && id <= 197); }
    private static SymbolicBlockState doorState(
            byte[] ids, byte[] data, byte[] high, int width, int depth, int index, int id) {
        int layer = width * depth;
        int metadata = data[index] & 15;
        boolean upper = (metadata & 8) != 0;
        int other = upper ? index - layer : index + layer;
        if (other < 0 || other >= ids.length || legacyId(ids, high, other) != id) {
            throw new IllegalArgumentException("ambiguous legacy door half for block " + id + " at index " + index);
        }
        int lowerData = data[upper ? other : index] & 15;
        int upperData = data[upper ? index : other] & 15;
        if ((lowerData & 8) != 0 || (upperData & 8) == 0) {
            throw new IllegalArgumentException("invalid legacy door half pairing for block " + id + " at index " + index);
        }
        String facing = switch (lowerData & 3) { case 0 -> "east"; case 1 -> "south"; case 2 -> "west"; default -> "north"; };
        String block = switch (id) {
            case 64 -> "oak_door"; case 71 -> "iron_door"; case 193 -> "spruce_door";
            case 194 -> "birch_door"; case 195 -> "jungle_door"; case 196 -> "acacia_door";
            case 197 -> "dark_oak_door"; default -> throw new IllegalArgumentException("unknown legacy door " + id);
        };
        return SymbolicBlockState.parse("minecraft:" + block + "[facing=" + facing + ",half="
                + (upper ? "upper" : "lower") + ",hinge=" + ((upperData & 1) != 0 ? "right" : "left")
                + ",open=" + ((lowerData & 4) != 0) + ",powered=" + ((upperData & 2) != 0) + "]");
    }
}
