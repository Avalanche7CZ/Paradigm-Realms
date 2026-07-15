package eu.avalanche7.paradigmrealms.persistence.validation;

import java.util.List;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.ownership.RealmOwnershipTransfer;

public record ValidatedRealmState(
        List<Realm> realms,
        List<RealmInvitation> invitations,
        List<RealmOwnershipTransfer> ownershipTransfers,
        ValidationReport report) {
    public ValidatedRealmState {
        realms = List.copyOf(realms);
        invitations = List.copyOf(invitations);
        ownershipTransfers = List.copyOf(ownershipTransfers);
    }
}
