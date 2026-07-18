package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record RealmMetadataSnapshot(
        int schemaVersion, long realmId, UUID ownerUuid, String ownerNameSnapshot,
        String displayName, String description, String presetId, String lifecycleState,
        String dimension, String allocationProfile, BackupCellBounds allocation,
        double spawnX, double spawnY, double spawnZ,
        float spawnYaw, float spawnPitch, String accessPolicy, List<UUID> members,
        List<UUID> managers, List<UUID> invitedVisitors, Map<UUID, String> bans,
        Map<String, Boolean> settings, boolean listed, Instant createdAt) {
    public RealmMetadataSnapshot {
        if (schemaVersion < 1 || realmId < 1) throw new IllegalArgumentException("invalid realm metadata identity");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        ownerNameSnapshot = bounded(ownerNameSnapshot, 64, "ownerNameSnapshot");
        displayName = bounded(displayName, 128, "displayName");
        description = bounded(description, 1024, "description");
        presetId = bounded(presetId, 256, "presetId");
        lifecycleState = bounded(lifecycleState, 64, "lifecycleState");
        dimension = bounded(dimension, 256, "dimension");
        allocationProfile = bounded(allocationProfile, 64, "allocationProfile");
        Objects.requireNonNull(allocation, "allocation");
        accessPolicy = bounded(accessPolicy, 64, "accessPolicy");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        managers = List.copyOf(Objects.requireNonNull(managers, "managers"));
        invitedVisitors = List.copyOf(Objects.requireNonNull(invitedVisitors, "invitedVisitors"));
        bans = Map.copyOf(Objects.requireNonNull(bans, "bans"));
        settings = Map.copyOf(Objects.requireNonNull(settings, "settings"));
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public RealmMetadataSnapshot(
            int schemaVersion, long realmId, UUID ownerUuid, String ownerNameSnapshot,
            String displayName, String description, String presetId, String lifecycleState,
            String dimension, BackupCellBounds allocation, double spawnX, double spawnY, double spawnZ,
            float spawnYaw, float spawnPitch, String accessPolicy, List<UUID> members,
            List<UUID> managers, List<UUID> invitedVisitors, Map<UUID, String> bans,
            Map<String, Boolean> settings, boolean listed, Instant createdAt) {
        this(schemaVersion, realmId, ownerUuid, ownerNameSnapshot, displayName, description, presetId,
                lifecycleState, dimension, "custom-v1", allocation, spawnX, spawnY, spawnZ,
                spawnYaw, spawnPitch, accessPolicy, members, managers, invitedVisitors, bans, settings,
                listed, createdAt);
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > maximum) throw new IllegalArgumentException(name + " is too long");
        return value;
    }
}
