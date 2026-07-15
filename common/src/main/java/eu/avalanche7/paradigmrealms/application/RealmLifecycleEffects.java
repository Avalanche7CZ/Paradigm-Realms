package eu.avalanche7.paradigmrealms.application;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;

@FunctionalInterface
public interface RealmLifecycleEffects {
    EvacuationResult evacuateAndVerify(Realm source);

    enum EvacuationResult {
        COMPLETE,
        RETRY,
        FAILED
    }

    static RealmLifecycleEffects immediate() {
        return source -> EvacuationResult.COMPLETE;
    }
}
