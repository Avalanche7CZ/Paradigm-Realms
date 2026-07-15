package eu.avalanche7.paradigmrealms.wilds;

public final class InvalidWildsTransitionException extends IllegalStateException {
    public InvalidWildsTransitionException(WildsLifecycleState from, WildsLifecycleState to) {
        super("invalid Wilds lifecycle transition: " + from + " -> " + to);
    }
}
