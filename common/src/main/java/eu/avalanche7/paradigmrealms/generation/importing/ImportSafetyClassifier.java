package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.Set;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtTag;

public final class ImportSafetyClassifier {
    private static final Set<String> FORBIDDEN_BLOCKS = Set.of(
            "minecraft:command_block", "minecraft:chain_command_block", "minecraft:repeating_command_block",
            "minecraft:structure_block", "minecraft:jigsaw", "minecraft:nether_portal", "minecraft:end_portal",
            "minecraft:end_gateway", "minecraft:end_portal_frame");
    private static final Set<String> VANILLA_CONTAINERS = Set.of(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:furnace",
            "minecraft:blast_furnace", "minecraft:smoker", "minecraft:hopper", "minecraft:dispenser",
            "minecraft:dropper", "minecraft:brewing_stand", "minecraft:shulker_box");
    public boolean forbiddenBlock(SymbolicBlockState state) { return FORBIDDEN_BLOCKS.contains(state.blockId()); }
    public boolean likelyContainer(SymbolicBlockState state) {
        return VANILLA_CONTAINERS.contains(state.blockId()) || state.blockId().endsWith("_shulker_box");
    }
    public boolean containsLootData(NbtCompoundTag tag) { return containsKey(tag, "LootTable", 0) || containsKey(tag, "LootTableSeed", 0); }
    public boolean containsInventoryData(NbtCompoundTag tag) { return containsKey(tag, "Items", 0); }
    private boolean containsKey(NbtTag tag, String key, int depth) {
        if (depth > 32) return true;
        if (tag instanceof NbtCompoundTag compound) {
            if (compound.contains(key)) return true;
            for (String child : compound.keys()) if (containsKey(compound.get(child), key, depth + 1)) return true;
        } else if (tag instanceof NbtListTag list) {
            for (NbtTag child : list.values()) if (containsKey(child, key, depth + 1)) return true;
        }
        return false;
    }
}
