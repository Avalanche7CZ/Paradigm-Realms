package eu.avalanche7.paradigmrealms.domain.realm;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;

public record RealmOperation(
        UUID operationId,
        String presetRevision,
        int attempt,
        CreationTimestamp updatedAt) {
    private static final Pattern REVISION = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");

    public RealmOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(presetRevision, "presetRevision");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!REVISION.matcher(presetRevision).matches()) {
            throw new IllegalArgumentException("invalid preset revision: " + presetRevision);
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("operation attempt must be positive");
        }
    }

    public RealmOperation nextAttempt(CreationTimestamp timestamp) {
        return new RealmOperation(operationId, presetRevision, Math.addExact(attempt, 1), timestamp);
    }
}
