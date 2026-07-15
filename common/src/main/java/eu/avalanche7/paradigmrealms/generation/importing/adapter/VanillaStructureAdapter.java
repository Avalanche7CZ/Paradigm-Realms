package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.generation.importing.ImportedTemplateFormat;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

public final class VanillaStructureAdapter implements SchematicFormatAdapter {
    @Override public boolean supports(ImportedTemplateFormat format) { return format == ImportedTemplateFormat.VANILLA_STRUCTURE; }
    @Override public NormalizedImportedTemplate parse(
            NbtCompoundTag root, ImportedTemplateFormat format, ImportPolicy policy) {
        NbtListTag size = root.list("size", NbtType.INT);
        if (size.size() != 3) throw new IllegalArgumentException("vanilla structure size must have three integers");
        int width = size.intValue(0), height = size.intValue(1), depth = size.intValue(2);
        List<String> warnings = new ArrayList<>();
        ImportPolicyActions.entities(policy, root.list("entities", NbtType.COMPOUND).size(),
                "vanilla structure source", warnings);
        if (root.contains("block_ticks") || root.contains("fluid_ticks") || root.contains("TileTicks")) {
            throw new IllegalArgumentException("scheduled ticks are not allowed");
        }
        NbtListTag paletteTag = root.list("palette", NbtType.COMPOUND);
        if (paletteTag.isEmpty() || root.contains("palettes")) throw new IllegalArgumentException("one non-empty palette is required");
        List<eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState> palette = new ArrayList<>();
        for (int index = 0; index < paletteTag.size(); index++) palette.add(ImportNbtSupport.paletteState(paletteTag.compound(index)));
        List<NormalizedImportedTemplate.NormalizedBlock> blocks = new ArrayList<>();
        NbtListTag source = root.list("blocks", NbtType.COMPOUND);
        for (int index = 0; index < source.size(); index++) {
            NbtCompoundTag block = source.compound(index); int state = block.intValue("state");
            if (state < 0 || state >= palette.size()) throw new IllegalArgumentException("invalid palette index " + state);
            NbtListTag pos = block.list("pos", NbtType.INT);
            if (pos.size() != 3) throw new IllegalArgumentException("block position must have three integers");
            int x = pos.intValue(0), y = pos.intValue(1), z = pos.intValue(2);
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= depth) throw new IllegalArgumentException("block outside structure dimensions");
            blocks.add(new NormalizedImportedTemplate.NormalizedBlock(x, y, z, palette.get(state),
                    block.contains("nbt", NbtType.COMPOUND) ? Optional.of(block.compound("nbt")) : Optional.empty()));
        }
        return new NormalizedImportedTemplate(format, root.contains("DataVersion", NbtType.NUMBER) ? root.intValue("DataVersion") : -1,
                width, height, depth, blocks,
                List.of(new NormalizedImportedTemplate.ImportedRegion("Structure", 0, 0, 0, width, height, depth)), warnings);
    }
}
