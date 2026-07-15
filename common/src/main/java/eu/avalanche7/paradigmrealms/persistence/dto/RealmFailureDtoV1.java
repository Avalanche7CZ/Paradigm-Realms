package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.Objects;

public record RealmFailureDtoV1(
        String code,
        String detail,
        String failedPhase,
        String operationUuid,
        int attempt,
        long updatedAtEpochMs) {
    public RealmFailureDtoV1 {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(failedPhase, "failedPhase");
        Objects.requireNonNull(operationUuid, "operationUuid");
    }
}
