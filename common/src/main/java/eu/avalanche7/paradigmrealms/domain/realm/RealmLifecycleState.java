package eu.avalanche7.paradigmrealms.domain.realm;

public enum RealmLifecycleState {
    ALLOCATED,
    GENERATING,
    READY,
    ACTIVE,
    FAILED,
    DELETING,
    ARCHIVED,
    DELETED
}
