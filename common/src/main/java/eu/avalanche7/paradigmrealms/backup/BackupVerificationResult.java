package eu.avalanche7.paradigmrealms.backup;

import java.util.List;
import java.util.Optional;

public record BackupVerificationResult(boolean valid, Optional<BackupManifest> manifest,
        List<String> failures, long streamedBytes) {
    public BackupVerificationResult {
        manifest = java.util.Objects.requireNonNull(manifest, "manifest");
        failures = List.copyOf(failures);
        if (valid && (manifest.isEmpty() || !failures.isEmpty())) throw new IllegalArgumentException("valid result invariant failed");
    }
    public static BackupVerificationResult invalid(List<String> failures, long bytes) {
        return new BackupVerificationResult(false, Optional.empty(), failures, bytes);
    }
}
