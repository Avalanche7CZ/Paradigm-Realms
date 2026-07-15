package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.Objects;
import java.util.Optional;

public record RealmLifecycleOperationDtoV1(
        String operationUuid,
        String kind,
        String stage,
        Optional<String> requestedPresetId,
        Optional<Long> targetRealmId,
        long requestedAtEpochMs,
        long updatedAtEpochMs,
        Optional<String> failureCode,
        Optional<String> failureDetail) {
    public RealmLifecycleOperationDtoV1 {
        Objects.requireNonNull(operationUuid, "operationUuid");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(requestedPresetId, "requestedPresetId");
        Objects.requireNonNull(targetRealmId, "targetRealmId");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(failureDetail, "failureDetail");
    }
}
