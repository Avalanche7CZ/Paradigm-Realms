package eu.avalanche7.paradigmrealms.generation;

import java.util.Map;

import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;

public final class BuiltInPresetPlacementEmitter {
    private static final SymbolicBlockState BEDROCK = block("minecraft:bedrock");
    private static final SymbolicBlockState DIRT = block("minecraft:dirt");
    private static final SymbolicBlockState GRASS = block("minecraft:grass_block");
    private static final SymbolicBlockState STONE = block("minecraft:stone");
    private static final SymbolicBlockState SMOOTH_STONE = block("minecraft:smooth_stone");
    private static final SymbolicBlockState WATER = block("minecraft:water");
    private static final SymbolicBlockState OAK_LOG = block("minecraft:oak_log");
    private static final SymbolicBlockState OAK_LEAVES = block("minecraft:oak_leaves");

    public boolean supports(PresetPlacementPlan plan) {
        return switch (plan.preset().placementFormat()) {
            case "builtin_starter_island", "builtin_flat_grass",
                    "builtin_empty_platform", "legacy_platform" -> true;
            default -> false;
        };
    }

    public void emit(PresetPlacementPlan plan, SymbolicBlockSink sink) {
        java.util.Objects.requireNonNull(plan, "plan");
        java.util.Objects.requireNonNull(sink, "sink");
        switch (plan.preset().placementFormat()) {
            case "builtin_starter_island" -> starterIsland(plan, sink);
            case "builtin_flat_grass" -> flatGrass(plan, sink);
            case "builtin_empty_platform" -> emptyPlatform(plan, sink);
            case "legacy_platform" -> legacyPlatform(plan, sink);
            default -> throw new IllegalArgumentException(
                    "not a built-in placement format: " + plan.preset().placementFormat());
        }
    }

    private static void legacyPlatform(PresetPlacementPlan plan, SymbolicBlockSink sink) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            place(plan, sink, x, -2, z, x == 0 && z == 0 ? BEDROCK : DIRT);
            place(plan, sink, x, -1, z, GRASS);
        }
    }

    private static void emptyPlatform(PresetPlacementPlan plan, SymbolicBlockSink sink) {
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            place(plan, sink, x, -1, z, SMOOTH_STONE);
        }
    }

    private static void flatGrass(PresetPlacementPlan plan, SymbolicBlockSink sink) {
        for (int x = -24; x <= 23; x++) for (int z = -24; z <= 23; z++) {
            place(plan, sink, x, -3, z, BEDROCK);
            place(plan, sink, x, -2, z, DIRT);
            place(plan, sink, x, -1, z, DIRT);
            place(plan, sink, x, 0, z, GRASS);
        }
    }

    private static void starterIsland(PresetPlacementPlan plan, SymbolicBlockSink sink) {
        for (int x = -24; x <= 24; x++) for (int z = -24; z <= 24; z++) {
            double distance = Math.sqrt((double) x * x + (double) z * z);
            if (distance > 23.5) continue;
            int bottom = Math.max(-8, -2 - (int) Math.floor((23.5 - distance) / 3.4));
            for (int y = bottom; y <= -3; y++) place(plan, sink, x, y, z, STONE);
            place(plan, sink, x, -2, z, DIRT);
            place(plan, sink, x, -1, z, DIRT);
            place(plan, sink, x, 0, z, GRASS);
        }
        for (int x = 7; x <= 10; x++) for (int z = -3; z <= 1; z++) {
            if ((x - 8.5) * (x - 8.5) + (z + 1.0) * (z + 1.0) <= 6.0) {
                place(plan, sink, x, 0, z, WATER);
            }
        }
        oak(plan, sink, -11, 1, -8);
        oak(plan, sink, 12, 1, 9);
        oak(plan, sink, -13, 1, 10);
    }

    private static void oak(
            PresetPlacementPlan plan, SymbolicBlockSink sink, int baseX, int baseY, int baseZ) {
        for (int y = 0; y < 5; y++) place(plan, sink, baseX, baseY + y, baseZ, OAK_LOG);
        for (int y = 3; y <= 6; y++) {
            int radius = y == 6 ? 1 : 2;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius && Math.abs(z) == radius && y != 4) continue;
                if (x != 0 || y != 4 || z != 0) {
                    place(plan, sink, baseX + x, baseY + y, baseZ + z, OAK_LEAVES);
                }
            }
        }
    }

    private static void place(
            PresetPlacementPlan plan, SymbolicBlockSink sink,
            int relativeX, int relativeY, int relativeZ, SymbolicBlockState state) {
        BlockCoordinate target = new BlockCoordinate(
                Math.addExact(plan.anchor().x(), relativeX),
                Math.addExact(plan.anchor().y(), relativeY),
                Math.addExact(plan.anchor().z(), relativeZ));
        if (!plan.bounds().contains(target.x(), target.y(), target.z())) {
            throw new IllegalStateException("built-in preset attempted an out-of-plan write at " + target);
        }
        sink.place(target, state);
    }

    private static SymbolicBlockState block(String id) {
        return new SymbolicBlockState(id, Map.of());
    }

    @FunctionalInterface
    public interface SymbolicBlockSink {
        void place(BlockCoordinate position, SymbolicBlockState state);
    }
}
