package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;

public record RealmFailure(
        String code,
        String detail,
        RealmLifecycleState failedPhase,
        UUID operationId,
        int attempt,
        CreationTimestamp updatedAt) {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final int MAX_DETAIL_LENGTH = 512;

    public RealmFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(failedPhase, "failedPhase");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("invalid failure code: " + code);
        }
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("failure detail exceeds " + MAX_DETAIL_LENGTH + " characters");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException("failure attempt cannot be negative");
        }
        if (failedPhase == RealmLifecycleState.FAILED || failedPhase == RealmLifecycleState.DELETED) {
            throw new IllegalArgumentException("failed phase must describe the operation that failed");
        }
    }
}
