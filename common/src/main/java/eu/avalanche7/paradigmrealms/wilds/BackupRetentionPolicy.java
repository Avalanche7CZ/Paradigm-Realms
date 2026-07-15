package eu.avalanche7.paradigmrealms.wilds;

import java.util.Comparator;
import java.util.List;

public final class BackupRetentionPolicy {
    public <T> List<T> eligibleForPruning(List<T> newestFirst, int retentionCount) {
        if (retentionCount < 1) throw new IllegalArgumentException("retention must be positive");
        return newestFirst.size() <= retentionCount
                ? List.of() : List.copyOf(newestFirst.subList(retentionCount, newestFirst.size()));
    }

    public <T> List<T> newestFirst(List<T> values, java.util.function.Function<T, Long> epoch) {
        return values.stream().sorted(Comparator.comparing(epoch).reversed()).toList();
    }
}
