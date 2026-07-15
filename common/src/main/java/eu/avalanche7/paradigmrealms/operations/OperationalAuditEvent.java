package eu.avalanche7.paradigmrealms.operations;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.RealmId;

public record OperationalAuditEvent(
        int eventVersion,
        Instant timestamp,
        String eventType,
        String outcome,
        Optional<UUID> operationId,
        Optional<UUID> actor,
        Optional<String> actorName,
        Optional<UUID> target,
        Optional<RealmId> realmId,
        Optional<Long> wildsEpoch,
        Map<String, String> details) {
    public OperationalAuditEvent {
        if (eventVersion != 1) throw new IllegalArgumentException("unsupported audit event version");
        Objects.requireNonNull(timestamp, "timestamp");
        eventType = safe(eventType, 80);
        outcome = safe(outcome, 80);
        operationId = Objects.requireNonNull(operationId, "operationId");
        actor = Objects.requireNonNull(actor, "actor");
        actorName = Objects.requireNonNull(actorName, "actorName").map(value -> safe(value, 64));
        target = Objects.requireNonNull(target, "target");
        realmId = Objects.requireNonNull(realmId, "realmId");
        wildsEpoch = Objects.requireNonNull(wildsEpoch, "wildsEpoch");
        Objects.requireNonNull(details, "details");
        if (details.size() > 16) throw new IllegalArgumentException("too many audit details");
        details = details.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> safe(entry.getKey(), 80), entry -> safe(entry.getValue(), 240)));
    }

    public static OperationalAuditEvent simple(
            Instant timestamp, String type, String outcome, Optional<UUID> actor, Optional<RealmId> realmId) {
        return new OperationalAuditEvent(1, timestamp, type, outcome, Optional.empty(), actor,
                Optional.empty(), Optional.empty(), realmId, Optional.empty(), Map.of());
    }

    private static String safe(String value, int maximum) {
        String normalized = Objects.requireNonNull(value, "value").replaceAll("[\\r\\n\\t]", " ").strip();
        if (normalized.isEmpty() || normalized.length() > maximum) throw new IllegalArgumentException("invalid audit value");
        return normalized;
    }
}
