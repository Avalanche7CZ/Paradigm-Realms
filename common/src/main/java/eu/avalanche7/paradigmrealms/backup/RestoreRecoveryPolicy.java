package eu.avalanche7.paradigmrealms.backup;

import java.time.Instant;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;

public final class RestoreRecoveryPolicy {
    private RestoreRecoveryPolicy() {}

    public static boolean requiresProtection(RestoreOperationManifest operation) {
        return operation.stage() != RestoreManifestStage.COMPLETED
                && !(operation.stage() == RestoreManifestStage.FAILED
                        && operation.failureCode().filter("CANCELLED_BY_ADMIN"::equals).isPresent());
    }

    public static boolean canRetry(RestoreOperationManifest operation) {
        return operation.stage() == RestoreManifestStage.FAILED && requiresProtection(operation);
    }

    public static boolean canCancel(RestoreOperationManifest operation) {
        return operation.failureCode().isEmpty()
                && (operation.stage() == RestoreManifestStage.PREPARED
                        || operation.stage() == RestoreManifestStage.SERVER_STOPPED_EXPECTED);
    }

    public static boolean matchesTarget(RestoreOperationManifest operation, Realm realm, String worldIdentity) {
        return realm != null
                && realm.id().value() == operation.realmId()
                && realm.owner().uuid().equals(operation.expectedOwnerUuid())
                && realm.dimension().toString().equals(operation.dimension())
                && worldIdentity.equals(operation.worldIdentity())
                && realm.allocation().profile().value().equals(operation.allocationProfile())
                && BackupStrategySelector.select(realm.allocation()) == operation.strategy()
                && BackupCellBounds.from(realm.allocation().cellBounds()).equals(operation.targetBounds());
    }

    public static RestoreOperationManifest retry(RestoreOperationManifest operation, Realm realm,
            String worldIdentity, String stateDigest, Instant now) {
        if (!canRetry(operation) || !matchesTarget(operation, realm, worldIdentity)
                || realm.state() != RealmLifecycleState.ACTIVE || realm.lifecycleOperation().isPresent()) {
            throw new IllegalStateException("Failed restore target is no longer available for retry");
        }
        return new RestoreOperationManifest(operation.manifestVersion(), operation.operationId(),
                operation.backupId(), operation.realmId(), operation.expectedOwnerUuid(), operation.targetBounds(),
                operation.dimension(), operation.allocationProfile(), operation.strategy(), operation.worldIdentity(),
                stateDigest, operation.archiveRelativePath(), operation.dimensionRelativePath(),
                operation.quarantineRelativePath(), operation.rollbackBackupId(), operation.mode(),
                RestoreManifestStage.SERVER_STOPPED_EXPECTED, operation.createdAt(), now,
                operation.failureCode(), operation.failureDetail());
    }
}
