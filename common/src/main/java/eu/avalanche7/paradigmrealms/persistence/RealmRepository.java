package eu.avalanche7.paradigmrealms.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmFailure;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationReport;
import eu.avalanche7.paradigmrealms.ownership.RealmOwnershipTransfer;

public interface RealmRepository {
    Optional<Realm> findById(RealmId id);

    Optional<Realm> findByOwner(UUID ownerUuid);

    List<Realm> list();

    void save(Realm realm);

    default void saveAll(List<Realm> realms, List<RealmInvitation> invitations) {
        throw new UnsupportedOperationException("bulk realm persistence is not implemented");
    }

    default List<RealmInvitation> listInvitations() {
        return List.of();
    }

    default List<RealmOwnershipTransfer> listOwnershipTransfers() {
        return List.of();
    }

    default void saveOwnershipTransfers(List<RealmOwnershipTransfer> transfers) {
        throw new UnsupportedOperationException("ownership transfer persistence is not implemented");
    }

    default void commitOwnershipTransfer(
            Realm replacement,
            List<RealmInvitation> invitations,
            List<RealmOwnershipTransfer> transfers) {
        throw new UnsupportedOperationException("ownership transfer commit is not implemented");
    }

    default void saveRealmAndInvitations(Realm realm, List<RealmInvitation> invitations) {
        throw new UnsupportedOperationException("membership persistence is not implemented");
    }

    RealmId allocateNextRealmId();

    Realm allocateRealm(RealmFactory factory);

    Realm updateLifecycle(RealmId id, RealmLifecycleState target, Optional<RealmFailure> failure);

    SchemaMetadata schemaMetadata();

    ValidationReport validate();
}
