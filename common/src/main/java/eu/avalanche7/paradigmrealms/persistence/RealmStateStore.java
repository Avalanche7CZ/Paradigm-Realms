package eu.avalanche7.paradigmrealms.persistence;

import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;

public interface RealmStateStore {
    StateLoadResult load();

    void save(RealmStateDtoV1 state);
}
