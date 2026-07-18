package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RealmDtoV1(
        int recordSchema,
        long id,
        String ownerUuid,
        String state,
        String dimension,
        String allocationProfile,
        int cellX,
        int cellZ,
        int cellMinChunkX,
        int cellMinChunkZ,
        int cellMaxChunkX,
        int cellMaxChunkZ,
        int buildMinChunkX,
        int buildMinChunkZ,
        int buildMaxChunkX,
        int buildMaxChunkZ,
        double spawnX,
        double spawnY,
        double spawnZ,
        float spawnYaw,
        float spawnPitch,
        String presetId,
        List<String> memberUuids,
        List<String> visitorUuids,
        String accessPolicy,
        long createdAtEpochMs,
        Optional<RealmOperationDtoV1> operation,
        Optional<RealmFailureDtoV1> failure,
        String displayName,
        String description,
        boolean listed,
        List<String> managerUuids,
        List<RealmBanDtoV1> bans,
        boolean pvp,
        boolean explosions,
        boolean mobGriefing,
        boolean visitorInteraction,
        boolean visitorContainers,
        Optional<Long> replacementOfRealmId,
        Optional<Long> replacedByRealmId,
        Optional<Long> archivedAtEpochMs,
        Optional<RealmLifecycleOperationDtoV1> lifecycleOperation) {

    public RealmDtoV1 {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(allocationProfile, "allocationProfile");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(memberUuids, "memberUuids");
        Objects.requireNonNull(visitorUuids, "visitorUuids");
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(managerUuids, "managerUuids");
        Objects.requireNonNull(bans, "bans");
        Objects.requireNonNull(replacementOfRealmId, "replacementOfRealmId");
        Objects.requireNonNull(replacedByRealmId, "replacedByRealmId");
        Objects.requireNonNull(archivedAtEpochMs, "archivedAtEpochMs");
        Objects.requireNonNull(lifecycleOperation, "lifecycleOperation");
        memberUuids = List.copyOf(memberUuids);
        visitorUuids = List.copyOf(visitorUuids);
        managerUuids = List.copyOf(managerUuids);
        bans = List.copyOf(bans);
    }

    public RealmDtoV1(
            int recordSchema, long id, String ownerUuid, String state, String dimension,
            int cellX, int cellZ, int cellMinChunkX, int cellMinChunkZ, int cellMaxChunkX, int cellMaxChunkZ,
            int buildMinChunkX, int buildMinChunkZ, int buildMaxChunkX, int buildMaxChunkZ,
            double spawnX, double spawnY, double spawnZ, float spawnYaw, float spawnPitch, String presetId,
            List<String> memberUuids, List<String> visitorUuids, String accessPolicy, long createdAtEpochMs,
            Optional<RealmOperationDtoV1> operation, Optional<RealmFailureDtoV1> failure) {
        this(recordSchema, id, ownerUuid, state, dimension, "custom-v1", cellX, cellZ, cellMinChunkX, cellMinChunkZ,
                cellMaxChunkX, cellMaxChunkZ, buildMinChunkX, buildMinChunkZ, buildMaxChunkX, buildMaxChunkZ,
                spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, presetId, memberUuids, visitorUuids, accessPolicy,
                createdAtEpochMs, operation, failure, "Realm #" + id, "", false, List.of(), List.of(), false,
                false, false, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public RealmDtoV1(
            int recordSchema, long id, String ownerUuid, String state, String dimension,
            String allocationProfile,
            int cellX, int cellZ, int cellMinChunkX, int cellMinChunkZ, int cellMaxChunkX, int cellMaxChunkZ,
            int buildMinChunkX, int buildMinChunkZ, int buildMaxChunkX, int buildMaxChunkZ,
            double spawnX, double spawnY, double spawnZ, float spawnYaw, float spawnPitch, String presetId,
            List<String> memberUuids, List<String> visitorUuids, String accessPolicy, long createdAtEpochMs,
            Optional<RealmOperationDtoV1> operation, Optional<RealmFailureDtoV1> failure) {
        this(recordSchema, id, ownerUuid, state, dimension, allocationProfile, cellX, cellZ,
                cellMinChunkX, cellMinChunkZ, cellMaxChunkX, cellMaxChunkZ,
                buildMinChunkX, buildMinChunkZ, buildMaxChunkX, buildMaxChunkZ,
                spawnX, spawnY, spawnZ, spawnYaw, spawnPitch, presetId, memberUuids, visitorUuids, accessPolicy,
                createdAtEpochMs, operation, failure, "Realm #" + id, "", false, List.of(), List.of(), false,
                false, false, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
