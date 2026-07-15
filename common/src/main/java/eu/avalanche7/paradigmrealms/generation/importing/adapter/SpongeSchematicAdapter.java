package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.generation.importing.ImportedTemplateFormat;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate.ImportedRegion;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate.NormalizedBlock;
import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

public final class SpongeSchematicAdapter implements SchematicFormatAdapter {
    @Override public boolean supports(ImportedTemplateFormat format) { return format == ImportedTemplateFormat.SPONGE_V1
            || format == ImportedTemplateFormat.SPONGE_V2 || format == ImportedTemplateFormat.SPONGE_V3; }
    @Override public NormalizedImportedTemplate parse(
            NbtCompoundTag root, ImportedTemplateFormat format, ImportPolicy policy) {
        int width = ImportNbtSupport.unsignedShort(root, "Width"), height = ImportNbtSupport.unsignedShort(root, "Height");
        int depth = ImportNbtSupport.unsignedShort(root, "Length");
        int volume = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) width, height), depth));
        NbtCompoundTag container = format == ImportedTemplateFormat.SPONGE_V3 ? root.compound("Blocks") : root;
        Map<Integer, SymbolicBlockState> palette = ImportNbtSupport.spongePalette(container.compound("Palette"));
        String dataKey = format == ImportedTemplateFormat.SPONGE_V3 ? "Data" : "BlockData";
        if (!container.contains(dataKey, NbtType.BYTE_ARRAY)) throw new IllegalArgumentException("missing " + dataKey);
        int[] indices = ImportNbtSupport.decodeVarInts(container.byteArray(dataKey), volume);
        List<String> warnings = new ArrayList<>();
        ImportPolicyActions.entities(policy, root.list("Entities", NbtType.COMPOUND).size(),
                "Sponge " + format.name().substring("SPONGE_".length()).toLowerCase() + " source", warnings);
        for (String key : List.of("BlockTicks", "FluidTicks", "TileTicks")) if (entries(root, key)) {
            throw new IllegalArgumentException("scheduled ticks are not allowed");
        }
        String beKey = format == ImportedTemplateFormat.SPONGE_V1 ? "TileEntities" : "BlockEntities";
        NbtListTag beList = container.contains(beKey, NbtType.LIST) ? container.list(beKey, NbtType.COMPOUND)
                : root.list(beKey, NbtType.COMPOUND);
        Map<ImportNbtSupport.Position, NbtCompoundTag> blockEntities = ImportNbtSupport.positionedCompounds(beList);
        List<NormalizedBlock> blocks = new ArrayList<>(volume);
        for (int index = 0; index < volume; index++) {
            SymbolicBlockState state = palette.get(indices[index]);
            if (state == null) throw new IllegalArgumentException("block data references unknown palette index " + indices[index]);
            int x = index % width, z = (index / width) % depth, y = index / (width * depth);
            blocks.add(new NormalizedBlock(x, y, z, state,
                    Optional.ofNullable(blockEntities.remove(new ImportNbtSupport.Position(x, y, z)))));
        }
        ImportPolicyActions.outOfBoundsBlockEntities(
                policy, blockEntities.size(), "Sponge schematic volume", warnings);
        int dataVersion = root.contains("DataVersion", NbtType.NUMBER) ? root.intValue("DataVersion") : -1;
        if (format == ImportedTemplateFormat.SPONGE_V1) {
            warnings.add("Sponge v1 is supported as legacy compatibility");
        }
        return new NormalizedImportedTemplate(format, dataVersion, width, height, depth, blocks,
                List.of(new ImportedRegion("Schematic", 0, 0, 0, width, height, depth)), warnings);
    }
    private static boolean entries(NbtCompoundTag root, String key) {
        return root.contains(key, NbtType.LIST) && !root.list(key, NbtType.COMPOUND).isEmpty();
    }
}
