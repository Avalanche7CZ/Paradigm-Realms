package eu.avalanche7.paradigmrealms.persistence;

import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;

@FunctionalInterface
public interface RealmFactory {
    Realm create(RealmId allocatedId);
}
