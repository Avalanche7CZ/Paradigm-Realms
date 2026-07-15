package eu.avalanche7.paradigmrealms.domain.realm;

public final class InvalidLifecycleTransitionException extends IllegalStateException {
    public InvalidLifecycleTransitionException(RealmLifecycleState from, RealmLifecycleState to) {
        super("invalid realm lifecycle transition: " + from + " -> " + to);
    }
}
