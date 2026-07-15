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
        Optional<RealmFailureDtoV1> failure) {

    public RealmDtoV1 {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(presetId, "presetId");
        Objects.requireNonNull(memberUuids, "memberUuids");
        Objects.requireNonNull(visitorUuids, "visitorUuids");
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(failure, "failure");
        memberUuids = List.copyOf(memberUuids);
        visitorUuids = List.copyOf(visitorUuids);
    }
}
