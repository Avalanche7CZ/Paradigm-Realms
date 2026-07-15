package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.Objects;

public record RealmOperationDtoV1(
        String operationUuid,
        String presetRevision,
        int attempt,
        long updatedAtEpochMs) {
    public RealmOperationDtoV1 {
        Objects.requireNonNull(operationUuid, "operationUuid");
        Objects.requireNonNull(presetRevision, "presetRevision");
    }
}
