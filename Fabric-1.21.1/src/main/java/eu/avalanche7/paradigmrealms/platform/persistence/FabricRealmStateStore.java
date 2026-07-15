package eu.avalanche7.paradigmrealms.platform.persistence;

import java.util.Objects;

import eu.avalanche7.paradigmrealms.persistence.RealmStateStore;
import eu.avalanche7.paradigmrealms.persistence.StateLoadResult;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;
import net.minecraft.world.PersistentStateManager;

public final class FabricRealmStateStore implements RealmStateStore {
    public static final String STORAGE_KEY = "paradigm_realms";

    private final RealmPersistentState persistentState;
    private final PersistentStateManager manager;

    public FabricRealmStateStore(PersistentStateManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.persistentState = manager.getOrCreate(RealmPersistentState.TYPE, STORAGE_KEY);
    }

    @Override
    public StateLoadResult load() {
        return persistentState.loadResult();
    }

    @Override
    public synchronized void save(RealmStateDtoV1 state) {
        RealmStateDtoV1 previous = persistentState.loadResult().state();
        if (!persistentState.replace(state)) {
            return;
        }
        try {
            manager.save();
        } catch (RuntimeException exception) {
            persistentState.restoreAfterFailedSave(previous);
            throw exception;
        }
    }
}
