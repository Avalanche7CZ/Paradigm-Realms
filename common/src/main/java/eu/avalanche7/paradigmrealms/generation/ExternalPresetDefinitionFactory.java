package eu.avalanche7.paradigmrealms.generation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.function.Predicate;

public final class ExternalPresetDefinitionFactory {
    public RealmPresetDefinition create(
            ExternalPresetMetadata metadata,
            String resourceNamespace,
            boolean allowExternalPresets,
            Predicate<String> modLoaded,
            StructureResourceValidation structureValidation) {
        java.util.Objects.requireNonNull(metadata, "metadata");
        java.util.Objects.requireNonNull(resourceNamespace, "resourceNamespace");
        java.util.Objects.requireNonNull(modLoaded, "modLoaded");
        java.util.Objects.requireNonNull(structureValidation, "structureValidation");
        metadata.id().requireNamespaced();
        metadata.structure().requireNamespaced();
        if (!metadata.id().namespace().equals(resourceNamespace)) {
            throw new IllegalArgumentException(
                    "preset ID namespace must match its metadata resource namespace");
        }
        ArrayList<String> disabled = new ArrayList<>();
        if (!allowExternalPresets) {
            disabled.add("external presets are disabled by server configuration");
        }
        metadata.requiredMods().stream().sorted().filter(mod -> !modLoaded.test(mod))
                .forEach(mod -> disabled.add("required mod is not loaded: " + mod));
        if (metadata.bounds().minY() < -128 || metadata.bounds().maxY() > 255) {
            disabled.add("relative Y bounds leave the overworld-type build height at anchor Y 64");
        }
        disabled.addAll(structureValidation.reasons());
        String structureFingerprint = structureValidation.fingerprint()
                .orElseGet(() -> sha256(metadata.id().value() + ":" + metadata.version()));
        String fingerprint = sha256(structureFingerprint + "|" + metadata.id().value()
                + "|" + metadata.version() + "|" + metadata.structure().value()
                + "|" + metadata.spawn() + "|" + metadata.bounds());
        int requiredChunksX = Math.floorDiv(Math.addExact(metadata.bounds().width(), 15), 16);
        int requiredChunksZ = Math.floorDiv(Math.addExact(metadata.bounds().depth(), 15), 16);
        return new RealmPresetDefinition(
                metadata.id(), metadata.version(),
                "v" + metadata.version() + "-" + fingerprint.substring(0, 16),
                metadata.displayName(), metadata.description(), metadata.spawn(), metadata.bounds(),
                requiredChunksX, requiredChunksZ, metadata.selectable(), metadata.legacy(),
                metadata.aliases(), metadata.requiredMods(), java.util.Optional.of(fingerprint),
                "minecraft_structure", java.util.Optional.of(metadata.structure()),
                PresetSourceType.EXTERNAL, disabled);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
