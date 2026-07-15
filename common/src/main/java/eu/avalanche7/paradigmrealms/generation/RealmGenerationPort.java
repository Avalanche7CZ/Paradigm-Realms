package eu.avalanche7.paradigmrealms.generation;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;

@FunctionalInterface
public interface RealmGenerationPort {
    void generate(Realm generatingRealm, PresetPlacementPlan placementPlan) throws Exception;
}
