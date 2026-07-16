package eu.avalanche7.paradigmrealms.backup;

import java.util.Optional;
import java.util.UUID;

public final class RestoreMetadataPolicy {
    public Decision decide(RestoreMode mode, UUID currentOwner, RealmMetadataSnapshot snapshot,
            boolean snapshotOwnerOwnsAnotherActiveRealm, boolean targetAllocationMatches) {
        if (!targetAllocationMatches) return Decision.rejected("The realm no longer owns the backed-up allocation.");
        if (mode == RestoreMode.WORLD_ONLY) return new Decision(true, false, false, false, Optional.empty());
        if (mode == RestoreMode.WORLD_AND_SAFE_METADATA) return new Decision(true, true, true, false, Optional.empty());
        if (!snapshot.ownerUuid().equals(currentOwner) && snapshotOwnerOwnsAnotherActiveRealm) {
            return Decision.rejected("Full metadata restore would give the backed-up owner two active realms.");
        }
        return new Decision(true, true, true, true, Optional.empty());
    }
    public record Decision(boolean allowed, boolean restoreIdentityAndSettings, boolean restoreSpawn,
            boolean restoreOwnershipAndRoles, Optional<String> rejection) {
        public static Decision rejected(String reason) { return new Decision(false, false, false, false, Optional.of(reason)); }
    }
}
