package eu.avalanche7.paradigmrealms.platform.generation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.generation.PresetPlacementPlan;
import eu.avalanche7.paradigmrealms.generation.BuiltInPresetPlacementEmitter;
import eu.avalanche7.paradigmrealms.generation.RealmGenerationPort;
import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkAccessPort;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLease;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadPurpose;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadRequest;
import eu.avalanche7.paradigmrealms.region.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.region.CoordinateMath;
import eu.avalanche7.paradigmrealms.platform.generation.importer.ForgeBlockStateResolver;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

public final class ForgeRealmPresetGenerator implements RealmGenerationPort {
    private static final RegistryKey<World> REALMS = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("paradigm_realms", "realms"));
    private final MinecraftServer server;
    private final ForgePresetCatalogManager presets;
    private final ChunkAccessPort chunks;
    private final BuiltInPresetPlacementEmitter builtIns = new BuiltInPresetPlacementEmitter();
    private final ForgeBlockStateResolver blockStates = new ForgeBlockStateResolver();

    public ForgeRealmPresetGenerator(
            MinecraftServer server, ForgePresetCatalogManager presets, ChunkAccessPort chunks) {
        this.server = server;
        this.presets = presets;
        this.chunks = chunks;
    }

    @Override
    public void generate(Realm realm, PresetPlacementPlan plan) throws Exception {
        validateCapture(realm, plan);
        ServerWorld world = server.getWorld(REALMS);
        if (world == null) throw new IllegalStateException("Realms dimension is not loaded");
        validateWorldBounds(world, realm, plan);
        try (ChunkLease ignored = acquireChunks(plan)) {
            switch (plan.preset().placementFormat()) {
                case "builtin_starter_island", "builtin_flat_grass",
                        "builtin_empty_platform", "legacy_platform" -> placeBuiltIn(world, plan);
                case "minecraft_structure" -> placeStructure(world, realm, plan);
                case "compiled_import" -> placeCompiledImport(world, plan);
                default -> throw new IllegalArgumentException(
                        "unsupported preset format " + plan.preset().placementFormat());
            }
            verifySpawn(world, plan);
        }
    }

    private static void validateCapture(Realm realm, PresetPlacementPlan plan) {
        boolean identityMatches = realm.preset().equals(plan.preset().id())
                || plan.preset().aliases().contains(realm.preset());
        if (!identityMatches) throw new IllegalStateException("captured preset identity does not match realm journal");
        String revision = realm.operation().orElseThrow().presetRevision();
        if (!revision.equals(plan.preset().revision())) {
            throw new IllegalStateException("captured preset revision does not match realm journal");
        }
    }

    private static void validateWorldBounds(ServerWorld world, Realm realm, PresetPlacementPlan plan) {
        var build = CoordinateMath.toBlockBounds(realm.allocation().buildableBounds());
        if (!build.contains(plan.bounds().minX(), plan.bounds().minZ())
                || !build.contains(plan.bounds().maxX(), plan.bounds().maxZ())) {
            throw new IllegalStateException("preset footprint leaves the buildable realm region");
        }
        if (plan.bounds().minY() < world.getBottomY() || plan.bounds().maxY() >= world.getTopY()) {
            throw new IllegalStateException("preset footprint leaves dimension build height");
        }
        if (!world.getWorldBorder().contains(new BlockPos(
                    plan.bounds().minX(), plan.bounds().minY(), plan.bounds().minZ()))
                || !world.getWorldBorder().contains(new BlockPos(
                    plan.bounds().maxX(), plan.bounds().maxY(), plan.bounds().maxZ()))) {
            throw new IllegalStateException("preset footprint is outside the world border");
        }
    }

    private ChunkLease acquireChunks(PresetPlacementPlan plan) throws Exception {
        int minX = Math.floorDiv(plan.bounds().minX(), 16);
        int maxX = Math.floorDiv(plan.bounds().maxX(), 16);
        int minZ = Math.floorDiv(plan.bounds().minZ(), 16);
        int maxZ = Math.floorDiv(plan.bounds().maxZ(), 16);
        LinkedHashSet<ChunkCoordinate> requested = new LinkedHashSet<>((maxX - minX + 1) * (maxZ - minZ + 1));
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            requested.add(new ChunkCoordinate(x, z));
        }
        return chunks.acquire(new ChunkLoadRequest(
                DimensionId.REALMS, requested, ChunkLoadPurpose.REALM_GENERATION, true));
    }

    private void placeBuiltIn(ServerWorld world, PresetPlacementPlan plan) {
        Map<SymbolicBlockState, BlockState> resolved = new HashMap<>();
        builtIns.emit(plan, (position, symbolic) -> {
            BlockState state = resolved.computeIfAbsent(symbolic, this::resolveBuiltInState);
            set(world, plan, pos(position), state);
        });
    }

    private BlockState resolveBuiltInState(SymbolicBlockState symbolic) {
        var result = blockStates.resolve(symbolic);
        return result.value().orElseThrow(() -> new IllegalStateException(
                "built-in block state is unavailable: "
                        + result.error().orElse(symbolic.canonical())));
    }

    private void placeStructure(ServerWorld world, Realm realm, PresetPlacementPlan plan) {
        Identifier id = Identifier.of(plan.preset().structure().orElseThrow().namespace(),
                plan.preset().structure().orElseThrow().path());
        var template = server.getStructureTemplateManager().getTemplate(id)
                .orElseThrow(() -> new IllegalStateException("validated structure is no longer available: " + id));
        var expected = plan.bounds();
        var size = template.getSize();
        if (size.getX() != expected.width() || size.getY() != expected.height()
                || size.getZ() != expected.depth()) {
            throw new IllegalStateException("structure dimensions drifted after catalog validation");
        }
        BlockPos origin = pos(plan.origin());
        StructurePlacementData placement = new StructurePlacementData()
                .setIgnoreEntities(true)
                .setMirror(BlockMirror.NONE)
                .setRotation(BlockRotation.NONE)
                .setBoundingBox(new BlockBox(expected.minX(), expected.minY(), expected.minZ(),
                        expected.maxX(), expected.maxY(), expected.maxZ()));
        boolean placed = template.place(world, origin, origin, placement,
                Random.create(realm.id().value()), Block.NOTIFY_ALL);
        if (!placed) throw new IllegalStateException("structure template reported no successful placement");
    }

    private void placeCompiledImport(ServerWorld world, PresetPlacementPlan plan) {
        var compiled = presets.compiledImport(plan.preset().id(), plan.preset().revision())
                .orElseThrow(() -> new IllegalStateException("validated compiled import is no longer available"));
        for (var block : compiled.blocks()) {
            set(world, plan, pos(plan.anchor()).add(block.x(), block.y(), block.z()), block.state());
        }
    }

    private static void set(ServerWorld world, PresetPlacementPlan plan, BlockPos pos, BlockState state) {
        if (!plan.bounds().contains(pos.getX(), pos.getY(), pos.getZ())) {
            throw new IllegalStateException("preset attempted an out-of-plan write at " + pos.toShortString());
        }
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
    }

    private static void verifySpawn(ServerWorld world, PresetPlacementPlan plan) {
        BlockPos feet = BlockPos.ofFloored(plan.spawn().x(), plan.spawn().y(), plan.spawn().z());
        if (!world.getBlockState(feet.down()).isSolidBlock(world, feet.down())
                || !world.getBlockState(feet).isAir()
                || !world.getBlockState(feet.up()).isAir()) {
            throw new IllegalStateException("generated preset failed safe-spawn verification");
        }
    }

    private static BlockPos pos(eu.avalanche7.paradigmrealms.region.BlockCoordinate value) {
        return new BlockPos(value.x(), value.y(), value.z());
    }
}
