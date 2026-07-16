package eu.avalanche7.paradigmrealms.modules.command;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import eu.avalanche7.paradigmrealms.backup.BackupCatalogEntry;
import eu.avalanche7.paradigmrealms.backup.BackupId;
import eu.avalanche7.paradigmrealms.backup.BackupRequestResult;
import eu.avalanche7.paradigmrealms.backup.BackupStatusSnapshot;
import eu.avalanche7.paradigmrealms.backup.BackupVerificationResult;
import eu.avalanche7.paradigmrealms.backup.RestoreMode;
import eu.avalanche7.paradigmrealms.backup.RestorePreparationResult;
import eu.avalanche7.paradigmrealms.backup.BackupPruneResult;
import eu.avalanche7.paradigmrealms.backup.BackupCatalogRepairResult;
import eu.avalanche7.paradigmrealms.backup.BackupDeletionResult;

public interface RealmBackupCommandRuntime {
    BackupRequestResult requestOwnBackup(UUID player, String playerName);
    List<BackupCatalogEntry> ownBackups(UUID player);
    BackupRequestResult requestAdminBackup(long realmId, UUID actor, String actorName);
    BackupRequestResult requestAdminBackupForOwner(UUID owner, UUID actor, String actorName);
    default List<Long> backupRealmIds() { return List.of(); }
    default List<UUID> backupRealmOwners() { return List.of(); }
    BackupStatusSnapshot backupStatus();
    List<BackupCatalogEntry> backups();
    List<BackupCatalogEntry> backupsForRealm(long realmId);
    Optional<BackupCatalogEntry> backup(BackupId backupId);
    CompletionStage<BackupVerificationResult> verifyBackup(BackupId backupId);
    boolean setBackupPinned(
            BackupId backupId, boolean pinned, UUID actor, String actorName);
    BackupDeletionResult requestBackupDeletion(BackupId backupId, UUID actor);
    BackupDeletionResult confirmBackupDeletion(String token, UUID actor);
    int runDueBackups();
    CompletionStage<RestorePreparationResult> prepareBackupRestore(
            BackupId backupId, RestoreMode mode, UUID actor, String actorName);
    boolean cancelBackupRestore(BackupId backupId);
    BackupPruneResult previewBackupPrune();
    BackupPruneResult runBackupPrune(UUID actor, String actorName);
    BackupCatalogRepairResult rebuildBackupCatalog(UUID actor, String actorName);
}
