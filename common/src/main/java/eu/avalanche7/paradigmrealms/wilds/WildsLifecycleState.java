package eu.avalanche7.paradigmrealms.wilds;

public enum WildsLifecycleState {
    DISABLED,
    ACTIVE,
    ENTRY_CLOSED,
    RESET_SCHEDULED,
    ENTRY_BLOCKED,
    EVACUATING,
    SAVE_BARRIER,
    OFFLINE_RESET_PENDING,
    VERIFYING,
    FAILED;

    public boolean entryOpen() {
        return this == ACTIVE || this == RESET_SCHEDULED;
    }

    public boolean resetInProgress() {
        return switch (this) {
            case RESET_SCHEDULED, ENTRY_BLOCKED, EVACUATING, SAVE_BARRIER,
                    OFFLINE_RESET_PENDING, VERIFYING, FAILED -> true;
            default -> false;
        };
    }
}
