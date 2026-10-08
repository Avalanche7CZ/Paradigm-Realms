package eu.avalanche7.paradigmrealms.core;

import eu.avalanche7.paradigmrealms.domain.RealmId;

public interface RealmsRuntimeHooks {
    RealmsRuntimeHooks NOOP = new RealmsRuntimeHooks() {
        @Override public void realmIndexChanged() {}
        @Override public void revalidateRealmPresence(RealmId realmId) {}
    };

    default boolean realmEntryAllowed(RealmId realmId) { return true; }

    default boolean realmOwnershipTransferAllowed(RealmId realmId) { return true; }

    void realmIndexChanged();

    void revalidateRealmPresence(RealmId realmId);
}
