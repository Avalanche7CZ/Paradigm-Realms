package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BackupQueuePolicy {
    private final int maximumSize;
    private final int maximumPlayerRequests;
    private final ArrayDeque<Request> queue = new ArrayDeque<>();
    private final Set<Long> realmIds = new HashSet<>();
    private int playerRequests;

    public BackupQueuePolicy(int maximumSize, int maximumPlayerRequests) {
        if (maximumSize < 1 || maximumPlayerRequests < 1 || maximumPlayerRequests > maximumSize) {
            throw new IllegalArgumentException("invalid backup queue limits");
        }
        this.maximumSize = maximumSize;
        this.maximumPlayerRequests = maximumPlayerRequests;
    }

    public synchronized Decision offer(Request request) {
        if (realmIds.contains(request.realmId())) {
            return new Decision(Status.DUPLICATE, -1);
        }
        if (queue.size() >= maximumSize) {
            return new Decision(Status.FULL, -1);
        }
        if (request.reason() == BackupReason.PLAYER_REQUESTED && playerRequests >= maximumPlayerRequests) {
            return new Decision(Status.PLAYER_LIMIT, -1);
        }
        queue.add(request);
        realmIds.add(request.realmId());
        if (request.reason() == BackupReason.PLAYER_REQUESTED) {
            playerRequests++;
        }
        return new Decision(Status.QUEUED, queue.size());
    }

    public synchronized Optional<Request> poll() {
        Request value = queue.poll();
        if (value == null) {
            return Optional.empty();
        }
        realmIds.remove(value.realmId());
        if (value.reason() == BackupReason.PLAYER_REQUESTED) {
            playerRequests--;
        }
        return Optional.of(value);
    }

    public synchronized boolean remove(long realmId) {
        Request found = queue.stream()
                .filter(value -> value.realmId() == realmId)
                .findFirst()
                .orElse(null);
        if (found == null) {
            return false;
        }
        queue.remove(found);
        realmIds.remove(realmId);
        if (found.reason() == BackupReason.PLAYER_REQUESTED) {
            playerRequests--;
        }
        return true;
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized List<Request> snapshot() {
        return List.copyOf(new ArrayList<>(queue));
    }

    public enum Status { QUEUED, DUPLICATE, FULL, PLAYER_LIMIT }

    public record Decision(Status status, int position) {}

    public record Request(long realmId, BackupReason reason, BackupActor actor, Instant requestedAt,
            int attempt, Optional<UUID> requester) {
        public Request {
            if (realmId < 1 || attempt < 0) {
                throw new IllegalArgumentException("invalid backup request");
            }
            java.util.Objects.requireNonNull(reason, "reason");
            java.util.Objects.requireNonNull(actor, "actor");
            java.util.Objects.requireNonNull(requestedAt, "requestedAt");
            requester = java.util.Objects.requireNonNull(requester, "requester");
        }

        public Request retry(Instant at) {
            return new Request(realmId, reason, actor, at, attempt + 1, requester);
        }
    }
}
