package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WildsResetManifest(
        int version,
        String worldIdentity,
        String dimensionId,
        UUID operationId,
        long sourceEpoch,
        long targetEpoch,
        long sourceSeed,
        long targetSeed,
        WildsProfileId sourceProfile,
        WildsProfileId targetProfile,
        String expectedRelativeWildsPath,
        String quarantineRelativePath,
        WildsManifestStage stage,
        Instant createdAt,
        Instant updatedAt,
        Optional<WildsFailure> failure) {
    public static final int VERSION = 1;
    public static final String WILDS_DIMENSION = "paradigm_realms:wilds";

    public WildsResetManifest {
        if (version != VERSION) throw new IllegalArgumentException("unsupported manifest version");
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(sourceProfile, "sourceProfile");
        Objects.requireNonNull(targetProfile, "targetProfile");
        Objects.requireNonNull(expectedRelativeWildsPath, "expectedRelativeWildsPath");
        Objects.requireNonNull(quarantineRelativePath, "quarantineRelativePath");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(failure, "failure");
        if (!WILDS_DIMENSION.equals(dimensionId)) throw new IllegalArgumentException("manifest targets another dimension");
        validateRelative(expectedRelativeWildsPath);
        validateRelative(quarantineRelativePath);
        if (targetEpoch != Math.addExact(sourceEpoch, 1)) throw new IllegalArgumentException("manifest epoch mismatch");
    }

    public WildsResetManifest withStage(WildsManifestStage replacement, Instant now) {
        return new WildsResetManifest(version, worldIdentity, dimensionId, operationId, sourceEpoch,
                targetEpoch, sourceSeed, targetSeed, sourceProfile, targetProfile,
                expectedRelativeWildsPath, quarantineRelativePath, replacement, createdAt, now, failure);
    }

    private static void validateRelative(String value) {
        if (value.isBlank() || value.startsWith("/") || value.startsWith("\\")
                || value.contains("..") || value.contains(":")) {
            throw new IllegalArgumentException("unsafe relative manifest path");
        }
    }
}
