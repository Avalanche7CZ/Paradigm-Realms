package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;

public record NormalizedImportedTemplate(
        ImportedTemplateFormat format, int dataVersion, int width, int height, int depth,
        List<NormalizedBlock> blocks, List<ImportedRegion> regions, List<String> warnings) {
    public NormalizedImportedTemplate {
        Objects.requireNonNull(format, "format"); Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(regions, "regions"); Objects.requireNonNull(warnings, "warnings");
        if (width < 1 || height < 1 || depth < 1) throw new IllegalArgumentException("dimensions must be positive");
        if (Math.multiplyExact(Math.multiplyExact((long) width, height), depth) > ImportLimits.MAX_VOLUME) {
            throw new IllegalArgumentException("template volume exceeds limit");
        }
        blocks = List.copyOf(blocks); regions = List.copyOf(regions); warnings = List.copyOf(warnings);
    }
    public record NormalizedBlock(int x, int y, int z, SymbolicBlockState state, Optional<NbtCompoundTag> blockEntity) {
        public NormalizedBlock { Objects.requireNonNull(state, "state"); Objects.requireNonNull(blockEntity, "blockEntity"); }
    }
    public record ImportedRegion(String name, int offsetX, int offsetY, int offsetZ, int width, int height, int depth) {
        public ImportedRegion { if (name == null || name.isBlank()) throw new IllegalArgumentException("region name required"); }
    }
}
