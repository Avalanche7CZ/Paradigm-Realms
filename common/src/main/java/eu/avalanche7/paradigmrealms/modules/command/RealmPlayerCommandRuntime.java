package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionConfig;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionResult;
import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.platform.teleport.SetSpawnResult;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;

public interface RealmPlayerCommandRuntime {
    PresetSelectionResult selectPreset(Optional<RealmPresetId> requested);
    List<RealmPresetDefinition> selectablePresets();
    PresetSelectionConfig presetSelection();
    boolean presetAvailable(RealmPresetId preset);
    Realm createRealm(UUID owner, RealmPresetDefinition preset);
    Optional<Realm> findRealmByOwner(UUID owner);
    TeleportResult teleportHome(UUID player, Realm realm);
    default TeleportResult leaveForeignRealm(UUID player) { return TeleportResult.WORLD_UNAVAILABLE; }
    SetSpawnResult setSpawn(UUID player);
    Optional<String> requestResetConfirmation(UUID owner, RealmPresetId preset);
    RealmLifecycleManagementService.Result confirmReset(UUID owner, String token);
    void cancelReset(UUID owner);
    Optional<String> requestDeleteConfirmation(UUID owner);
    RealmLifecycleManagementService.Result confirmDelete(UUID owner, String token);
    void cancelDelete(UUID owner);
}
