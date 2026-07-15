package eu.avalanche7.paradigmrealms.platform.teleport;

@FunctionalInterface
public interface TeleportEffectPort {
    TeleportResult teleport(TeleportRequest request);
}
