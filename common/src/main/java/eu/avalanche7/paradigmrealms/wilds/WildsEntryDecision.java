package eu.avalanche7.paradigmrealms.wilds;

public record WildsEntryDecision(boolean allowed, Reason reason, long activeEpoch) {
    public enum Reason { ALLOWED, DISABLED, LIFECYCLE_BLOCKED, GENERATION_UNVERIFIED, PERMISSION_DENIED, STALE_PLAYER_EPOCH }
}
