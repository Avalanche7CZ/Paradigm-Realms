package eu.avalanche7.paradigmrealms.persistence;

public final class ReadOnlyStoreException extends IllegalStateException {
    public ReadOnlyStoreException() {
        super("realm storage is read-only because startup validation did not pass");
    }
}
