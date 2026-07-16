package eu.avalanche7.paradigmrealms.backup;

public enum BackupLifecycleState {
    QUEUED, PREPARING, LOCKING, FLUSHING, CAPTURING, PACKAGING, VERIFYING,
    COMPLETED, FAILED, CANCELLED;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
