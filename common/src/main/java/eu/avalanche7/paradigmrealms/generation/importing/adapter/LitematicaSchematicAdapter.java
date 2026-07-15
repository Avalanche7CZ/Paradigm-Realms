package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.generation.importing.ImportLimits;
import eu.avalanche7.paradigmrealms.generation.importing.ImportedTemplateFormat;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate;
import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

public final class LitematicaSchematicAdapter implements SchematicFormatAdapter {
    @Override public boolean supports(ImportedTemplateFormat format) { return format == ImportedTemplateFormat.LITEMATICA; }
    @Override public NormalizedImportedTemplate parse(
            NbtCompoundTag root, ImportedTemplateFormat format, ImportPolicy policy) {
        NbtCompoundTag regions = root.compound("Regions");
        if (regions.isEmpty()) throw new IllegalArgumentException("Litematica file has no regions");
        int version = root.intValue("Version"); if (version < 1) throw new IllegalArgumentException("unsupported Litematica version " + version);
        int dataVersion = root.contains("MinecraftDataVersion", NbtType.NUMBER) ? root.intValue("MinecraftDataVersion") : -1;
        Map<Position, NormalizedImportedTemplate.NormalizedBlock> merged = new HashMap<>();
        List<NormalizedImportedTemplate.ImportedRegion> metadata = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int[] enclosing = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (String name : regions.keys().stream().sorted().toList()) {
            if (!regions.contains(name, NbtType.COMPOUND)) throw new IllegalArgumentException("region " + name + " is not a compound");
            NbtCompoundTag region = regions.compound(name); rejectDynamic(region, name, policy, warnings);
            int[] position = ImportNbtSupport.vector(region, "Position"), signed = ImportNbtSupport.vector(region, "Size");
            int width = dimension(signed[0], name), height = dimension(signed[1], name), depth = dimension(signed[2], name);
            long volume = Math.multiplyExact(Math.multiplyExact((long) width, height), depth);
            if (volume > ImportLimits.MAX_VOLUME) throw new IllegalArgumentException("region " + name + " exceeds volume limit");
            metadata.add(new NormalizedImportedTemplate.ImportedRegion(name, position[0], position[1], position[2], width, height, depth));
            NbtListTag paletteTag = region.list("BlockStatePalette", NbtType.COMPOUND);
            if (paletteTag.isEmpty()) throw new IllegalArgumentException("region " + name + " has empty palette");
            List<SymbolicBlockState> palette = new ArrayList<>();
            for (int index = 0; index < paletteTag.size(); index++) palette.add(ImportNbtSupport.paletteState(paletteTag.compound(index)));
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long[] packed = region.longArray("BlockStates");
            long expected = Math.floorDiv(Math.addExact(Math.multiplyExact(volume, bits), 63L), 64L);
            if (packed.length != expected) throw new IllegalArgumentException("region " + name + " has invalid packed length");
            Map<ImportNbtSupport.Position, NbtCompoundTag> blockEntities = ImportNbtSupport.positionedCompounds(
                    region.list("TileEntities", NbtType.COMPOUND));
            for (long index = 0; index < volume; index++) {
                int stateIndex = unpack(packed, index, bits);
                if (stateIndex >= palette.size()) throw new IllegalArgumentException("region " + name + " has invalid palette index " + stateIndex);
                int x = (int) (index % width), z = (int) ((index / width) % depth), y = (int) (index / ((long) width * depth));
                int targetX = Math.addExact(position[0], signed[0] < 0 ? -x : x);
                int targetY = Math.addExact(position[1], signed[1] < 0 ? -y : y);
                int targetZ = Math.addExact(position[2], signed[2] < 0 ? -z : z);
                enclosing[0] = Math.min(enclosing[0], targetX); enclosing[3] = Math.max(enclosing[3], targetX);
                enclosing[1] = Math.min(enclosing[1], targetY); enclosing[4] = Math.max(enclosing[4], targetY);
                enclosing[2] = Math.min(enclosing[2], targetZ); enclosing[5] = Math.max(enclosing[5], targetZ);
                NormalizedImportedTemplate.NormalizedBlock value = new NormalizedImportedTemplate.NormalizedBlock(
                        targetX, targetY, targetZ, palette.get(stateIndex),
                        Optional.ofNullable(blockEntities.remove(new ImportNbtSupport.Position(x, y, z))));
                Position key = new Position(targetX, targetY, targetZ);
                var previous = merged.putIfAbsent(key, value);
                if (previous != null && (!previous.state().equals(value.state()) || !previous.blockEntity().equals(value.blockEntity()))) {
                    throw new IllegalArgumentException("conflicting Litematica overlap at " + targetX + "," + targetY + "," + targetZ);
                }
            }
            ImportPolicyActions.outOfBoundsBlockEntities(
                    policy, blockEntities.size(), "Litematica region " + name, warnings);
        }
        int width = Math.addExact(Math.subtractExact(enclosing[3], enclosing[0]), 1);
        int height = Math.addExact(Math.subtractExact(enclosing[4], enclosing[1]), 1);
        int depth = Math.addExact(Math.subtractExact(enclosing[5], enclosing[2]), 1);
        List<NormalizedImportedTemplate.NormalizedBlock> normalized = merged.values().stream()
                .map(block -> new NormalizedImportedTemplate.NormalizedBlock(block.x() - enclosing[0], block.y() - enclosing[1],
                        block.z() - enclosing[2], block.state(), block.blockEntity()))
                .sorted(Comparator.comparingInt(NormalizedImportedTemplate.NormalizedBlock::y)
                        .thenComparingInt(NormalizedImportedTemplate.NormalizedBlock::z)
                        .thenComparingInt(NormalizedImportedTemplate.NormalizedBlock::x)).toList();
        warnings.add("normalized " + regions.size() + " Litematica region(s)");
        return new NormalizedImportedTemplate(format, dataVersion, width, height, depth, normalized, metadata, warnings);
    }
    private static int dimension(int value, String name) {
        if (value == 0 || value == Integer.MIN_VALUE) throw new IllegalArgumentException("region " + name + " has invalid size"); return Math.abs(value);
    }
    private static int unpack(long[] data, long index, int bits) {
        long start = index * bits; int first = (int) (start >>> 6), offset = (int) (start & 63); long mask = (1L << bits) - 1;
        long value = data[first] >>> offset; if (offset + bits > 64) value |= data[first + 1] << (64 - offset); return (int) (value & mask);
    }
    private static void rejectDynamic(
            NbtCompoundTag region, String name, ImportPolicy policy, List<String> warnings) {
        ImportPolicyActions.entities(policy, region.list("Entities", NbtType.COMPOUND).size(),
                "Litematica region " + name, warnings);
        for (String key : List.of("PendingBlockTicks", "PendingFluidTicks", "BlockTicks", "FluidTicks")) {
            if (region.contains(key, NbtType.LIST) && !region.list(key, NbtType.COMPOUND).isEmpty()) {
                throw new IllegalArgumentException("region " + name + " contains scheduled ticks");
            }
        }
    }
    private record Position(int x, int y, int z) {}
}
