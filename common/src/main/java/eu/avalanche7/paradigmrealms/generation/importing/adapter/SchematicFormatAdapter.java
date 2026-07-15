package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import eu.avalanche7.paradigmrealms.generation.importing.ImportedTemplateFormat;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;

public interface SchematicFormatAdapter {
    boolean supports(ImportedTemplateFormat format);
    NormalizedImportedTemplate parse(NbtCompoundTag root, ImportedTemplateFormat format, ImportPolicy policy);
}
