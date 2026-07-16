package eu.avalanche7.paradigmrealms.backup;

import java.util.List;

public record BackupCatalogRepairResult(
        boolean successful,
        int catalogEntries,
        int scannedArchives,
        List<String> warnings) {
    public BackupCatalogRepairResult {
        warnings = List.copyOf(warnings);
    }
}
