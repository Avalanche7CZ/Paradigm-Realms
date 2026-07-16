package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BackupOperation(
        UUID operationId,
        BackupId backupId,
        long realmId,
        BackupReason reason,
        BackupActor actor,
        BackupLifecycleState state,
        Instant createdAt,
        Instant updatedAt,
        int attempt,
        String stagingRelativePath,
        Optional<BackupFailure> failure,
        Optional<String> failureDetail) {
    public BackupOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(backupId, "backupId");
        if (realmId < 1 || attempt < 0) {
            throw new IllegalArgumentException("invalid backup operation identity");
        }
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        stagingRelativePath = safePath(stagingRelativePath);
        failure = Objects.requireNonNull(failure, "failure");
        failureDetail = Objects.requireNonNull(failureDetail, "failureDetail")
                .map(BackupOperation::safeDetail);
        if (state == BackupLifecycleState.FAILED && failure.isEmpty()) {
            throw new IllegalArgumentException("failed backup operation requires a failure classification");
        }
    }

    public BackupOperation transition(BackupLifecycleState next, Instant at) {
        if (state.terminal()) {
            throw new IllegalStateException("terminal backup operation cannot transition");
        }
        return new BackupOperation(
                operationId,
                backupId,
                realmId,
                reason,
                actor,
                next,
                createdAt,
                at,
                attempt,
                stagingRelativePath,
                Optional.empty(),
                Optional.empty());
    }

    public BackupOperation fail(BackupFailure classification, String detail, Instant at) {
        return new BackupOperation(
                operationId,
                backupId,
                realmId,
                reason,
                actor,
                BackupLifecycleState.FAILED,
                createdAt,
                at,
                attempt,
                stagingRelativePath,
                Optional.of(classification),
                Optional.ofNullable(detail));
    }

    public BackupOperation cancel(String detail, Instant at) {
        return new BackupOperation(
                operationId,
                backupId,
                realmId,
                reason,
                actor,
                BackupLifecycleState.CANCELLED,
                createdAt,
                at,
                attempt,
                stagingRelativePath,
                Optional.of(BackupFailure.CANCELLED),
                Optional.ofNullable(detail));
    }

    private static String safePath(String value) {
        Objects.requireNonNull(value, "stagingRelativePath");
        if (value.isBlank() || value.startsWith("/") || value.contains("..")
                || value.contains("\\") || value.length() > 512) {
            throw new IllegalArgumentException("unsafe backup staging path");
        }
        return value;
    }

    private static String safeDetail(String value) {
        String safe = value.replace('\n', ' ').replace('\r', ' ').strip();
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }
}
