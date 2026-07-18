package eu.avalanche7.paradigmrealms.platform.generation.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.generation.PresetRelativeBounds;
import eu.avalanche7.paradigmrealms.generation.PresetRelativeSpawn;
import eu.avalanche7.paradigmrealms.generation.PresetSourceType;
import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.generation.importing.ImportSafetyClassifier;
import eu.avalanche7.paradigmrealms.generation.importing.ImportPolicy;
import eu.avalanche7.paradigmrealms.generation.importing.BlockEntitySanitizationPort;
import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.NormalizedImportedTemplate;
import eu.avalanche7.paradigmrealms.generation.importing.StructurallyValidatedTemplate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView;

final class ImportedTemplateCompiler {
    private static final ImportSafetyClassifier COMMON_SAFETY = new ImportSafetyClassifier();
    private final ForgeBlockStateResolver states = new ForgeBlockStateResolver();
    private final BlockEntities blockEntities = new BlockEntities();

    CompiledImportedTemplate compile(
            StructurallyValidatedTemplate validated, RealmPresetId presetId, String sourceFile,
            String fingerprint, ImportPolicy policy) {
        NormalizedImportedTemplate raw = validated.template();
        presetId.requireNamespaced();
        int minX = validated.centeredSourceBounds().minX();
        int minZ = validated.centeredSourceBounds().minZ();
        Map<Position, BlockState> resolvedStates = new HashMap<>();
        List<String> warnings = new ArrayList<>(raw.warnings());
        Set<String> requiredMods = new HashSet<>();
        int stripped = 0;
        for (NormalizedImportedTemplate.NormalizedBlock block : raw.blocks()) {
            if (block.x() < 0 || block.x() >= raw.width() || block.y() < 0 || block.y() >= raw.height()
                    || block.z() < 0 || block.z() >= raw.depth()) {
                throw new IllegalArgumentException("normalized block lies outside enclosing dimensions");
            }
            if (COMMON_SAFETY.forbiddenBlock(block.state())) {
                throw new IllegalArgumentException("forbidden block " + block.state().blockId());
            }
            var resolution = states.resolve(block.state());
            if (resolution.error().isPresent()) throw new IllegalArgumentException(resolution.error().orElseThrow());
            BlockState state = resolution.value().orElseThrow();
            resolution.requiredMod().ifPresent(requiredMods::add);
            if (block.blockEntity().isPresent()) {
                var sanitation = blockEntities.sanitize(block.state(), block.blockEntity().orElseThrow(), policy);
                if (!sanitation.allowed()) throw new IllegalArgumentException(String.join("; ", sanitation.errors()));
                warnings.addAll(sanitation.warnings());
                stripped++;
            }
            Position position = new Position(block.x(), block.y(), block.z());
            BlockState previous = resolvedStates.putIfAbsent(position, state);
            if (previous != null && !previous.equals(state)) {
                throw new IllegalArgumentException("conflicting blocks at normalized position " + position);
            }
        }
        List<CompiledImportedTemplate.CompiledBlock> blocks = resolvedStates.entrySet().stream()
                .filter(entry -> !entry.getValue().isAir())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new CompiledImportedTemplate.CompiledBlock(
                        minX + entry.getKey().x, entry.getKey().y, minZ + entry.getKey().z, entry.getValue()))
                .toList();
        if (blocks.size() != validated.nonAirBlockCount()) throw new IllegalStateException("platform air classification disagrees with common validation");
        PresetRelativeSpawn spawn = findSpawn(resolvedStates, raw.width(), raw.height(), raw.depth(), minX, minZ);
        int maxY = Math.max(raw.height() - 1, Math.addExact((int) Math.floor(spawn.y()), 1));
        PresetRelativeBounds bounds = new PresetRelativeBounds(
                minX, 0, minZ, minX + raw.width() - 1, maxY, minZ + raw.depth() - 1);
        if (stripped > 0) warnings.add("reconstructed " + stripped + " known safe empty block entit"
                + (stripped == 1 ? "y" : "ies"));
        if (raw.dataVersion() < 0) warnings.add("source has no Minecraft DataVersion; current registry validation was used");
        String revision = "import-" + fingerprint.substring(0, 16);
        RealmPresetDefinition definition = new RealmPresetDefinition(
                presetId, 1, revision, displayName(presetId), "Imported server schematic " + sourceFile,
                spawn, bounds, Math.floorDiv(bounds.width() + 15, 16), Math.floorDiv(bounds.depth() + 15, 16),
                true, false, Set.of(), requiredMods, Optional.of(fingerprint), "compiled_import", Optional.empty(),
                PresetSourceType.IMPORTED, List.of());
        return new CompiledImportedTemplate(definition, sourceFile, raw.format(), blocks, stripped, warnings);
    }

    private static PresetRelativeSpawn findSpawn(
            Map<Position, BlockState> states, int width, int height, int depth, int minX, int minZ) {
        int centerX = (width - 1) / 2, centerZ = (depth - 1) / 2;
        Position best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Map.Entry<Position, BlockState> entry : states.entrySet()) {
            Position pos = entry.getKey();
            if (!Block.isFaceFullSquare(
                    entry.getValue().getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), Direction.UP)) continue;
            if (!air(states.get(new Position(pos.x, pos.y + 1, pos.z)))
                    || !air(states.get(new Position(pos.x, pos.y + 2, pos.z)))) continue;
            long distance = Math.abs((long) pos.x - centerX) + Math.abs((long) pos.z - centerZ);
            if (best == null || distance < bestDistance || (distance == bestDistance && pos.y > best.y)) {
                best = pos; bestDistance = distance;
            }
        }
        if (best == null) throw new IllegalArgumentException("no safe spawn column with solid floor and two air blocks");
        return new PresetRelativeSpawn(minX + best.x + 0.5, best.y + 1, minZ + best.z + 0.5, 0, 0);
    }

    private static boolean air(BlockState state) { return state == null || state.isAir(); }
    private static String displayName(RealmPresetId id) {
        String value = id.path().replace('_', ' ');
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
    private record Position(int x, int y, int z) implements Comparable<Position> {
        @Override public int compareTo(Position other) {
            int yOrder = Integer.compare(y, other.y);
            if (yOrder != 0) return yOrder;
            int zOrder = Integer.compare(z, other.z);
            return zOrder != 0 ? zOrder : Integer.compare(x, other.x);
        }
    }

    static final class BlockEntities implements BlockEntitySanitizationPort {
        private static final ImportSafetyClassifier COMMON = new ImportSafetyClassifier();
        private static final Set<String> STRUCTURAL_KEYS = Set.of(
                "id", "Id", "x", "y", "z", "Pos", "keepPacked");
        private static final Set<String> CONTAINERS = Set.of(
                "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:furnace",
                "minecraft:blast_furnace", "minecraft:smoker", "minecraft:hopper", "minecraft:dispenser",
                "minecraft:dropper", "minecraft:brewing_stand", "minecraft:shulker_box",
                "minecraft:white_shulker_box", "minecraft:orange_shulker_box", "minecraft:magenta_shulker_box",
                "minecraft:light_blue_shulker_box", "minecraft:yellow_shulker_box", "minecraft:lime_shulker_box",
                "minecraft:pink_shulker_box", "minecraft:gray_shulker_box", "minecraft:light_gray_shulker_box",
                "minecraft:cyan_shulker_box", "minecraft:purple_shulker_box", "minecraft:blue_shulker_box",
                "minecraft:brown_shulker_box", "minecraft:green_shulker_box", "minecraft:red_shulker_box",
                "minecraft:black_shulker_box");

        @Override public Result sanitize(
                SymbolicBlockState state, NbtCompoundTag data, ImportPolicy policy) {
            if (state.blockId().equals("minecraft:flower_pot")) {
                if (policy == ImportPolicy.STRICT) return Result.rejected(
                        "STRICT policy rejects legacy flower-pot block-entity data");
                return Result.stripped(List.of(
                        "SANITIZE: discarded legacy flower-pot contents; no modern block-state mapping was inferred",
                        "SANITIZE: reconstructed safe empty minecraft:flower_pot"));
            }
            if (!COMMON.likelyContainer(state) || !CONTAINERS.contains(state.blockId())) {
                String type = data.string("id").isBlank() ? data.string("Id") : data.string("id");
                return Result.rejected("block-entity NBT is not allowlisted for " + state.blockId()
                        + (type.isBlank() ? "" : " (" + type + ")"));
            }
            boolean inventory = COMMON.containsInventoryData(data);
            boolean loot = COMMON.containsLootData(data);
            TreeSet<String> additional = new TreeSet<>(data.keys());
            additional.removeAll(STRUCTURAL_KEYS);
            additional.remove("Items");
            additional.remove("LootTable");
            additional.remove("LootTableSeed");
            if (policy == ImportPolicy.STRICT) {
                if (inventory) return Result.rejected(
                        "STRICT policy rejects container inventory in " + state.blockId());
                if (loot) return Result.rejected(
                        "STRICT policy rejects loot table data in " + state.blockId());
                if (!additional.isEmpty()) return Result.rejected(
                        "STRICT policy rejects ambiguous block-entity data in "
                                + state.blockId() + ": " + additional);
                return Result.stripped(
                        "STRICT: reconstructed known safe empty block entity for " + state.blockId());
            }
            ArrayList<String> warnings = new ArrayList<>();
            if (inventory) warnings.add(
                    "SANITIZE: stripped container inventory from " + state.blockId());
            if (loot) warnings.add("SANITIZE: removed loot table data from " + state.blockId());
            if (!additional.isEmpty()) warnings.add(
                    "SANITIZE: discarded remaining block-entity data from "
                            + state.blockId() + ": " + additional);
            warnings.add("SANITIZE: reconstructed known safe empty block entity for " + state.blockId());
            return Result.stripped(warnings);
        }
    }
}
