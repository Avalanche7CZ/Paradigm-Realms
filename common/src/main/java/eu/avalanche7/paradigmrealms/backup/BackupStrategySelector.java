package eu.avalanche7.paradigmrealms.backup;

import eu.avalanche7.paradigmrealms.allocation.AllocationProfile;
import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;

public final class BackupStrategySelector {
    private BackupStrategySelector() {}

    public static BackupStrategy select(RealmAllocation allocation) {
        if (allocation.profile().equals(AllocationProfile.REGION_ALIGNED_32_V1)
                && allocation.cellBounds().width() == 32
                && allocation.cellBounds().depth() == 32
                && Math.floorMod(allocation.cellBounds().minX(), 32) == 0
                && Math.floorMod(allocation.cellBounds().minZ(), 32) == 0) {
            return BackupStrategy.REGION_COPY;
        }
        return BackupStrategy.CHUNK_EXTRACT;
    }
}
