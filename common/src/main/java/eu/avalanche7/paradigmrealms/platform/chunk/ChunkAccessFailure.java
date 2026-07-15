package eu.avalanche7.paradigmrealms.platform.chunk;

public final class ChunkAccessFailure extends Exception {
    private final Reason reason;

    public ChunkAccessFailure(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public ChunkAccessFailure(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() { return reason; }

    public enum Reason { WORLD_UNAVAILABLE, CHUNK_UNAVAILABLE }
}
