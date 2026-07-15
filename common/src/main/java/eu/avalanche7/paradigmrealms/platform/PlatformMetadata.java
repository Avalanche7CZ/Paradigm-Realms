package eu.avalanche7.paradigmrealms.platform;

import java.util.Objects;

public record PlatformMetadata(
        String modVersion,
        String minecraftVersion,
        String loaderName,
        String loaderVersion,
        String optionalIntegrationState,
        String resetToolCompatibilityVersion) {
    public PlatformMetadata {
        Objects.requireNonNull(modVersion, "modVersion");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(loaderName, "loaderName");
        Objects.requireNonNull(loaderVersion, "loaderVersion");
        Objects.requireNonNull(optionalIntegrationState, "optionalIntegrationState");
        Objects.requireNonNull(resetToolCompatibilityVersion, "resetToolCompatibilityVersion");
    }

    public PlatformMetadata(String modVersion, String minecraftVersion, String loaderName, String loaderVersion) {
        this(modVersion, minecraftVersion, loaderName, loaderVersion, "unknown", "1");
    }

    public static PlatformMetadata unknown() {
        return new PlatformMetadata("unknown", "unknown", "unknown", "unknown", "unknown", "1");
    }
}
