package eu.avalanche7.paradigmrealms.backup;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class BackupCaptureTask {
    private BackupCaptureTask() {}

    public static <T> CompletableFuture<T> start(
            Runnable releaseLock, Supplier<CompletableFuture<T>> capture) {
        CompletableFuture<T> result;
        try {
            result = Objects.requireNonNull(capture.get(), "capture future");
        } catch (RuntimeException exception) {
            result = CompletableFuture.failedFuture(exception);
        }
        return result.whenComplete((value, failure) -> releaseLock.run());
    }
}
