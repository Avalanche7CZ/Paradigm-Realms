package eu.avalanche7.paradigmrealms.application;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;
import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.RealmId;

public final class AllocationPreviewService {
    private final RealmAllocator allocator;

    public AllocationPreviewService(RealmAllocator allocator) {
        this.allocator = allocator;
    }

    public RealmAllocation preview(RealmId realmId) {
        return allocator.preview(realmId);
    }
}
