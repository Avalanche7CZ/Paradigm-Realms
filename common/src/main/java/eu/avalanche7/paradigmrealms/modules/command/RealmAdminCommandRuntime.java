package eu.avalanche7.paradigmrealms.modules.command;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.generation.PresetCatalogSnapshot;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportResult;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationReport;
import eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService;
import eu.avalanche7.paradigmrealms.operations.ConfigCommandResult;

public interface RealmAdminCommandRuntime {
    List<Realm> inspectRealms();
    Optional<Realm> inspectRealm(RealmId id);
    Optional<Realm> inspectRealmOwner(UUID owner);
    RealmAllocation previewAllocation(RealmId id);
    ValidationReport validateRealms();

    PresetCatalogSnapshot presetCatalogSnapshot();
    List<ValidationIssue> presetValidationIssues();
    CompletionStage<PresetCatalogSnapshot> reloadPresetCatalog();
    List<String> presetImportFiles();
    Map<RealmPresetId, String> presetImportBindings() throws IOException;
    PresetImportResult inspectPresetImport(String sourceFile);
    PresetImportResult importPreset(String sourceFile, RealmPresetId presetId);
    PresetImportResult removePresetImport(RealmPresetId presetId);
    PresetImportResult reimportPreset(RealmPresetId presetId);

    void enableSessionBypass(UUID player);
    void disableSessionBypass(UUID player);
    boolean sessionBypassEnabled(UUID player);
    RealmLifecycleManagementService.Result restoreArchive(RealmId id);
    RealmLifecycleManagementService.Result retryRealmOperation(RealmId id);
    List<String> repairPreview();
    boolean repairIndexes();
    int repairStaleSessions();
    int repairExpiredOperations();
    Optional<String> exportSupportBundle();
    ConfigCommandResult validateConfig();
    ConfigCommandResult reloadConfig();
}
