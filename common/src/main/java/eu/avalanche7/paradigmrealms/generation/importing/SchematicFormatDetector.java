package eu.avalanche7.paradigmrealms.generation.importing;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

public final class SchematicFormatDetector {
    public DetectedFormat detect(NbtCompoundTag outer) {
        NbtCompoundTag root = outer.contains("Schematic", NbtType.COMPOUND) ? outer.compound("Schematic") : outer;
        if (root.contains("Regions", NbtType.COMPOUND) && root.contains("Version", NbtType.NUMBER)) {
            return new DetectedFormat(ImportedTemplateFormat.LITEMATICA, root);
        }
        if (root.contains("size", NbtType.LIST) && root.contains("palette", NbtType.LIST)
                && root.contains("blocks", NbtType.LIST)) return new DetectedFormat(ImportedTemplateFormat.VANILLA_STRUCTURE, root);
        if (root.contains("Materials", NbtType.STRING) && root.contains("Blocks", NbtType.BYTE_ARRAY)
                && root.contains("Data", NbtType.BYTE_ARRAY)) return new DetectedFormat(ImportedTemplateFormat.MCEDIT_SCHEMATIC, root);
        if (root.contains("Version", NbtType.NUMBER)) {
            int version = root.intValue("Version");
            if (version == 3 && root.contains("Blocks", NbtType.COMPOUND)) return new DetectedFormat(ImportedTemplateFormat.SPONGE_V3, root);
            if ((version == 1 || version == 2) && root.contains("Palette", NbtType.COMPOUND)
                    && root.contains("BlockData", NbtType.BYTE_ARRAY)) {
                return new DetectedFormat(version == 1 ? ImportedTemplateFormat.SPONGE_V1 : ImportedTemplateFormat.SPONGE_V2, root);
            }
        }
        throw new IllegalArgumentException("unsupported or unrecognized NBT schematic structure");
    }
    public record DetectedFormat(ImportedTemplateFormat format, NbtCompoundTag root) {}
}
