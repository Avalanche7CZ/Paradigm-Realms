package eu.avalanche7.paradigmrealms.platform.world;

public record StandingSafety(
        boolean avoidFloorFluids,
        boolean avoidLeaves,
        boolean avoidPowderSnow,
        boolean avoidHazardousFloor) {
    public static final StandingSafety STANDARD = new StandingSafety(true, false, false, false);
}
