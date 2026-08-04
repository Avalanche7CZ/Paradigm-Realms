package eu.avalanche7.paradigmrealms.platform.generation.importer;

import java.util.List;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.generation.importing.ImportedTemplateFormat;
import net.minecraft.block.BlockState;

public record CompiledImportedTemplate(
        RealmPresetDefinition preset,
        String sourceFile,
        ImportedTemplateFormat format,
        List<CompiledBlock> blocks,
        int sanitizedBlockEntityCount,
        List<String> warnings) {
    public CompiledImportedTemplate {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(warnings, "warnings");
        blocks = List.copyOf(blocks);
        warnings = List.copyOf(warnings);
    }

    public record CompiledBlock(int x, int y, int z, BlockState state) {
        public CompiledBlock { Objects.requireNonNull(state, "state"); }
    }
}
