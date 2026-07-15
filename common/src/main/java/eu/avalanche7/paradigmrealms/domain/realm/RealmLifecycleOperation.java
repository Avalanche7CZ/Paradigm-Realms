package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public record RealmLifecycleOperation(
        UUID operationId,
        RealmLifecycleOperationKind kind,
        RealmLifecycleOperationStage stage,
        Optional<RealmPresetId> requestedPreset,
        Optional<RealmId> targetRealmId,
        CreationTimestamp requestedAt,
        CreationTimestamp updatedAt,
        Optional<String> failureCode,
        Optional<String> failureDetail) {
    public RealmLifecycleOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(requestedPreset, "requestedPreset");
        Objects.requireNonNull(targetRealmId, "targetRealmId");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(failureCode, "failureCode");
        Objects.requireNonNull(failureDetail, "failureDetail");
        if (kind == RealmLifecycleOperationKind.RESET && requestedPreset.isEmpty()) {
            throw new IllegalArgumentException("reset operation requires a requested preset");
        }
        if (kind == RealmLifecycleOperationKind.DELETE && requestedPreset.isPresent()) {
            throw new IllegalArgumentException("delete operation must not carry a preset");
        }
        if (failureCode.isPresent() != failureDetail.isPresent()) {
            throw new IllegalArgumentException("lifecycle failure code and detail must be present together");
        }
        if (stage == RealmLifecycleOperationStage.FAILED && failureCode.isEmpty()) {
            throw new IllegalArgumentException("failed lifecycle operation requires failure metadata");
        }
    }

    public RealmLifecycleOperation(
            UUID operationId, RealmLifecycleOperationKind kind, RealmLifecycleOperationStage stage,
            Optional<RealmPresetId> requestedPreset, Optional<RealmId> targetRealmId,
            CreationTimestamp requestedAt, CreationTimestamp updatedAt) {
        this(operationId, kind, stage, requestedPreset, targetRealmId, requestedAt, updatedAt,
                Optional.empty(), Optional.empty());
    }

    public RealmLifecycleOperation withStage(RealmLifecycleOperationStage next, CreationTimestamp timestamp) {
        return new RealmLifecycleOperation(operationId, kind, next, requestedPreset, targetRealmId, requestedAt,
                timestamp, Optional.empty(), Optional.empty());
    }

    public RealmLifecycleOperation withTarget(RealmId target, RealmLifecycleOperationStage next, CreationTimestamp timestamp) {
        return new RealmLifecycleOperation(operationId, kind, next, requestedPreset, Optional.of(target), requestedAt,
                timestamp, Optional.empty(), Optional.empty());
    }

    public RealmLifecycleOperation failed(String code, String detail, CreationTimestamp timestamp) {
        return new RealmLifecycleOperation(operationId, kind, RealmLifecycleOperationStage.FAILED,
                requestedPreset, targetRealmId, requestedAt, timestamp, Optional.of(code), Optional.of(detail));
    }
}
