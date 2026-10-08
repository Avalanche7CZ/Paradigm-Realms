package eu.avalanche7.paradigmrealms.backup;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class RollbackBackupRequest {
    private RollbackBackupRequest() {}

    public static CompletableFuture<BackupCatalogEntry> start(Requester requester) {
        CompletableFuture<BackupCatalogEntry> result = new CompletableFuture<>();
        try {
            BackupRequestResult request = requester.request((successful, entry) -> {
                if (successful && entry.isPresent()) {
                    result.complete(entry.orElseThrow());
                } else {
                    result.completeExceptionally(new IllegalStateException("The current realm could not be backed up."));
                }
            });
            if (!request.accepted()) {
                result.completeExceptionally(new IllegalStateException(request.message()));
            }
        } catch (RuntimeException exception) {
            result.completeExceptionally(exception);
        }
        return result;
    }

    @FunctionalInterface
    public interface Requester {
        BackupRequestResult request(Completion completion);
    }

    @FunctionalInterface
    public interface Completion {
        void completed(boolean successful, Optional<BackupCatalogEntry> entry);
    }
}
