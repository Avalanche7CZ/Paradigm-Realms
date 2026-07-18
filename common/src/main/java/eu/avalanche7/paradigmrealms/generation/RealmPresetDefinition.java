package eu.avalanche7.paradigmrealms.generation;

import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public record RealmPresetDefinition(
        RealmPresetId id,
        int version,
        String revision,
        String displayName,
        String description,
        PresetRelativeSpawn spawn,
        PresetRelativeBounds bounds,
        int requiredBuildableChunksX,
        int requiredBuildableChunksZ,
        boolean selectable,
        boolean legacy,
        Set<RealmPresetId> aliases,
        Set<String> requiredMods,
        Optional<String> fingerprint,
        String placementFormat,
        Optional<RealmPresetId> structure,
        PresetSourceType sourceType,
        List<String> disableReasons) {
    private static final Pattern REVISION = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public RealmPresetDefinition {
        Objects.requireNonNull(id, "id");
        id.requireNamespaced();
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(spawn, "spawn");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(aliases, "aliases");
        Objects.requireNonNull(requiredMods, "requiredMods");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(placementFormat, "placementFormat");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(disableReasons, "disableReasons");
        if (version < 1) throw new IllegalArgumentException("preset version must be positive");
        if (!REVISION.matcher(revision).matches()) {
            throw new IllegalArgumentException("invalid preset revision: " + revision);
        }
        if (displayName.isBlank() || description.isBlank()) {
            throw new IllegalArgumentException("preset display name and description cannot be blank");
        }
        if (!bounds.contains(spawn)) throw new IllegalArgumentException("preset spawn must be inside bounds");
        if (requiredBuildableChunksX < 1 || requiredBuildableChunksX > 16
                || requiredBuildableChunksZ < 1 || requiredBuildableChunksZ > 16) {
            throw new IllegalArgumentException("required buildable dimensions must be between 1 and 16 chunks");
        }
        if (bounds.width() > requiredBuildableChunksX * 16
                || bounds.depth() > requiredBuildableChunksZ * 16) {
            throw new IllegalArgumentException("declared bounds exceed required buildable dimensions");
        }

        if (bounds.minX() < -128 || bounds.maxX() > 127
                || bounds.minZ() < -128 || bounds.maxZ() > 127) {
            throw new IllegalArgumentException("declared bounds do not fit the centered 16x16-chunk region");
        }
        aliases = Set.copyOf(aliases);
        if (aliases.contains(id)) throw new IllegalArgumentException("preset cannot alias itself");
        requiredMods = Set.copyOf(requiredMods);
        for (String mod : requiredMods) {
            if (!mod.matches("[a-z][a-z0-9_-]{0,63}")) throw new IllegalArgumentException("invalid mod ID: " + mod);
        }
        fingerprint.ifPresent(value -> {
            if (!value.matches("[a-f0-9]{16,64}")) throw new IllegalArgumentException("invalid fingerprint");
        });
        if (!placementFormat.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("invalid placement format: " + placementFormat);
        }
        if ("minecraft_structure".equals(placementFormat) && structure.isEmpty()) {
            throw new IllegalArgumentException("structure placement requires a structure identifier");
        }
        structure.ifPresent(RealmPresetId::requireNamespaced);
        disableReasons = List.copyOf(disableReasons);
    }

    public boolean enabled() { return disableReasons.isEmpty(); }
    public boolean playerSelectable() { return enabled() && selectable && !legacy; }

    public RealmPresetDefinition disabled(String reason) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>(disableReasons);
        reasons.add(Objects.requireNonNull(reason, "reason"));
        return new RealmPresetDefinition(id, version, revision, displayName, description, spawn, bounds,
                requiredBuildableChunksX, requiredBuildableChunksZ, selectable, legacy, aliases, requiredMods,
                fingerprint, placementFormat, structure, sourceType, reasons);
    }
}
