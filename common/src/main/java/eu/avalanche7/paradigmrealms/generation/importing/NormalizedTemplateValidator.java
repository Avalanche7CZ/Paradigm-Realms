package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.HashMap;
import java.util.Map;

import eu.avalanche7.paradigmrealms.generation.PresetRelativeBounds;

public final class NormalizedTemplateValidator {
    private static final ImportSafetyClassifier SAFETY = new ImportSafetyClassifier();
    public StructurallyValidatedTemplate validate(NormalizedImportedTemplate template) {
        return validate(template, ImportPolicy.STRICT);
    }
    public StructurallyValidatedTemplate validate(NormalizedImportedTemplate template, ImportPolicy policy) {
        if (template.width() > 160 || template.depth() > 160) {
            throw new IllegalArgumentException("enclosing X/Z bounds exceed the 10x10-chunk realm region");
        }
        if (template.height() > 318) throw new IllegalArgumentException("enclosing height exceeds the Realms build-height envelope");
        Map<Position, NormalizedImportedTemplate.NormalizedBlock> positions = new HashMap<>(); int nonAir = 0;
        for (var block : template.blocks()) {
            if (block.x() < 0 || block.x() >= template.width() || block.y() < 0 || block.y() >= template.height()
                    || block.z() < 0 || block.z() >= template.depth()) throw new IllegalArgumentException("normalized block lies outside enclosing dimensions");
            if (SAFETY.forbiddenBlock(block.state())) throw new IllegalArgumentException("forbidden block " + block.state().blockId());
            block.blockEntity().ifPresent(data -> {
                if (policy == ImportPolicy.STRICT && SAFETY.containsLootData(data)) {
                    throw new IllegalArgumentException("STRICT policy rejects loot table data");
                }
            });
            Position key = new Position(block.x(), block.y(), block.z());
            var previous = positions.putIfAbsent(key, block);
            if (previous != null && (!previous.state().equals(block.state()) || !previous.blockEntity().equals(block.blockEntity()))) {
                throw new IllegalArgumentException("conflicting normalized overlap at " + key);
            }
            if (!isAir(block.state())) nonAir++;
        }
        if (nonAir < 1) throw new IllegalArgumentException("template contains no non-air blocks");
        if (nonAir > ImportLimits.MAX_PLACED_BLOCKS) throw new IllegalArgumentException("non-air block count exceeds 250000 placement limit");
        int minX = -Math.floorDiv(template.width(), 2), minZ = -Math.floorDiv(template.depth(), 2);
        return new StructurallyValidatedTemplate(template,
                new PresetRelativeBounds(minX, 0, minZ, minX + template.width() - 1,
                        template.height() - 1, minZ + template.depth() - 1), nonAir);
    }
    private static boolean isAir(SymbolicBlockState state) {
        return state.blockId().equals("minecraft:air") || state.blockId().equals("minecraft:cave_air")
                || state.blockId().equals("minecraft:void_air");
    }
    private record Position(int x, int y, int z) {}
}
