package eu.avalanche7.paradigmrealms.generation;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import eu.avalanche7.paradigmrealms.region.BlockVolume;
import eu.avalanche7.paradigmrealms.region.CoordinateMath;

public final class PresetPlacementPlanner {
    public static final int ANCHOR_Y = 64;

    public PresetPlacementPlan plan(RealmAllocation allocation, RealmPresetDefinition preset) {
        var build = CoordinateMath.toBlockBounds(allocation.buildableBounds());
        int anchorX = Math.addExact(build.minX(), Math.floorDiv(build.width(), 2));
        int anchorZ = Math.addExact(build.minZ(), Math.floorDiv(build.depth(), 2));
        return translate(allocation, preset, new BlockCoordinate(anchorX, ANCHOR_Y, anchorZ), null);
    }

    public PresetPlacementPlan recover(Realm realm, RealmPresetDefinition preset) {
        if (preset.legacy()) {
            var relative = preset.spawn();
            int anchorX = Math.toIntExact(Math.round(realm.spawn().x() - relative.x()));
            int anchorY = Math.toIntExact(Math.round(realm.spawn().y() - relative.y()));
            int anchorZ = Math.toIntExact(Math.round(realm.spawn().z() - relative.z()));
            return translate(realm.allocation(), preset,
                    new BlockCoordinate(anchorX, anchorY, anchorZ), realm.spawn());
        }
        PresetPlacementPlan plan = plan(realm.allocation(), preset);
        if (!plan.spawn().equals(realm.spawn())) {
            throw new IllegalStateException("persisted spawn does not match captured preset plan");
        }
        return plan;
    }

    private static PresetPlacementPlan translate(
            RealmAllocation allocation,
            RealmPresetDefinition preset,
            BlockCoordinate anchor,
            BlockPosition persistedSpawn) {
        PresetRelativeBounds relative = preset.bounds();
        BlockVolume translated = new BlockVolume(
                Math.addExact(anchor.x(), relative.minX()), Math.addExact(anchor.y(), relative.minY()),
                Math.addExact(anchor.z(), relative.minZ()), Math.addExact(anchor.x(), relative.maxX()),
                Math.addExact(anchor.y(), relative.maxY()), Math.addExact(anchor.z(), relative.maxZ()));
        var build = CoordinateMath.toBlockBounds(allocation.buildableBounds());
        if (!build.contains(translated.minX(), translated.minZ())
                || !build.contains(translated.maxX(), translated.maxZ())) {
            throw new IllegalArgumentException("preset placement would leave buildable realm bounds");
        }
        BlockPosition spawn = persistedSpawn == null ? new BlockPosition(
                checkedAdd(anchor.x(), preset.spawn().x()), checkedAdd(anchor.y(), preset.spawn().y()),
                checkedAdd(anchor.z(), preset.spawn().z()), preset.spawn().yaw(), preset.spawn().pitch())
                : persistedSpawn;
        return new PresetPlacementPlan(preset, anchor,
                new BlockCoordinate(translated.minX(), translated.minY(), translated.minZ()), translated, spawn);
    }

    private static double checkedAdd(int left, double right) {
        double result = left + right;
        if (!Double.isFinite(result) || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE) {
            throw new ArithmeticException("preset coordinate overflow");
        }
        return result;
    }
}
