package eu.avalanche7.paradigmrealms.domain.realm;

public enum RealmLifecycleOperationStage {
    REQUESTED,
    TARGET_RESERVED,
    TARGET_GENERATING,
    TARGET_ACTIVE,
    OWNER_SWITCHED,
    SOURCE_ARCHIVED,
    ENTRY_BLOCKED,
    OCCUPANTS_EVACUATED,
    ARCHIVED,
    FAILED
}
