package eu.avalanche7.paradigmrealms.membership;

import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;

public record MembershipResult(
        MembershipStatus status,
        Optional<Realm> realm,
        Optional<RealmInvitation> invitation) {
    public MembershipResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(invitation, "invitation");
    }

    public boolean succeeded() {
        return switch (status) {
            case CREATED, REFRESHED, ACCEPTED, DECLINED, REMOVED, LEFT, ACCESS_CHANGED -> true;
            default -> false;
        };
    }
}
