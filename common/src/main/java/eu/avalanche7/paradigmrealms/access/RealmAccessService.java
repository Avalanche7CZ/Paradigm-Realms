package eu.avalanche7.paradigmrealms.access;

import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;

public final class RealmAccessService {
    public AccessDecision evaluate(Realm realm, UUID actor, AccessAction action) {
        Objects.requireNonNull(realm, "realm");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");

        AccessRole role = roleOf(realm, actor);
        if (role == AccessRole.BANNED) {
            return new AccessDecision(false, role, AccessDecisionReason.BANNED);
        }
        if (realm.state() != RealmLifecycleState.ACTIVE) {
            return new AccessDecision(false, role, AccessDecisionReason.REALM_NOT_ACTIVE);
        }
        return switch (role) {
            case OWNER -> allow(role, AccessDecisionReason.ALLOWED_OWNER);
            case MANAGER -> allow(role, AccessDecisionReason.ALLOWED_MANAGER);
            case MEMBER -> allow(role, AccessDecisionReason.ALLOWED_MEMBER);
            case VISITOR -> visitorDecision(role, action, AccessDecisionReason.ALLOWED_PUBLIC_VISITOR);
            case BANNED, UNAUTHORIZED -> new AccessDecision(false, role, AccessDecisionReason.PRIVATE_REALM);
        };
    }

    public AccessRole roleOf(Realm realm, UUID actor) {
        if (realm.owner().uuid().equals(actor)) {
            return AccessRole.OWNER;
        }
        if (realm.bans().containsKey(actor)) {
            return AccessRole.BANNED;
        }
        if (realm.managers().contains(actor)) {
            return AccessRole.MANAGER;
        }
        if (realm.members().contains(actor)) {
            return AccessRole.MEMBER;
        }
        if (realm.accessPolicy() == RealmAccessPolicy.PUBLIC_VISIT) {
            return AccessRole.VISITOR;
        }
        return AccessRole.UNAUTHORIZED;
    }

    private static AccessDecision visitorDecision(
            AccessRole role, AccessAction action, AccessDecisionReason allowedReason) {
        return switch (action) {
            case ENTER -> allow(role, allowedReason);
            case BUILD -> new AccessDecision(false, role, AccessDecisionReason.VISITOR_CANNOT_BUILD);
            case CONTAINER_INTERACT ->
                    new AccessDecision(false, role, AccessDecisionReason.VISITOR_CANNOT_USE_CONTAINER);
            case ENTITY_INTERACT ->
                    new AccessDecision(false, role, AccessDecisionReason.VISITOR_CANNOT_INTERACT_WITH_ENTITY);
        };
    }

    private static AccessDecision allow(AccessRole role, AccessDecisionReason reason) {
        return new AccessDecision(true, role, reason);
    }
}
