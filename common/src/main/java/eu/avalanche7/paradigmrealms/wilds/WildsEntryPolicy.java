package eu.avalanche7.paradigmrealms.wilds;

import java.util.UUID;

public final class WildsEntryPolicy {
    public WildsEntryDecision evaluate(WildsState state, UUID player, boolean permitted, boolean joiningFromSavedWilds) {
        if (state.lifecycle() == WildsLifecycleState.DISABLED) return deny(state, WildsEntryDecision.Reason.DISABLED);
        if (!state.lifecycle().entryOpen()) return deny(state, WildsEntryDecision.Reason.LIFECYCLE_BLOCKED);
        if (!state.generationVerified()) return deny(state, WildsEntryDecision.Reason.GENERATION_UNVERIFIED);
        if (!permitted) return deny(state, WildsEntryDecision.Reason.PERMISSION_DENIED);
        if (joiningFromSavedWilds && state.approvedEpoch(player) != state.activeEpoch()) {
            return deny(state, WildsEntryDecision.Reason.STALE_PLAYER_EPOCH);
        }
        return new WildsEntryDecision(true, WildsEntryDecision.Reason.ALLOWED, state.activeEpoch());
    }

    private static WildsEntryDecision deny(WildsState state, WildsEntryDecision.Reason reason) {
        return new WildsEntryDecision(false, reason, state.activeEpoch());
    }
}
