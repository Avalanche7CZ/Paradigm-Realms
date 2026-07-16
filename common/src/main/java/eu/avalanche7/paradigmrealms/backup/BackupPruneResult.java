package eu.avalanche7.paradigmrealms.backup;

import java.util.List;
import java.util.Map;

public record BackupPruneResult(
        List<BackupCatalogEntry> selected,
        long reclaimableBytes,
        Map<BackupId, String> retentionReasons,
        boolean storageLimitsSatisfied,
        boolean applied) {
    public BackupPruneResult {
        selected = List.copyOf(selected);
        retentionReasons = Map.copyOf(retentionReasons);
        if (reclaimableBytes < 0) {
            throw new IllegalArgumentException("reclaimable bytes cannot be negative");
        }
    }
}
