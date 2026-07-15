package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WarningSchedule {
    public List<Long> normalize(Collection<Long> values) {
        return values.stream().peek(value -> {
            if (value < 0) throw new IllegalArgumentException("warning time cannot be negative");
        }).distinct().sorted(java.util.Comparator.reverseOrder()).toList();
    }

    public Set<Long> crossed(
            Instant scheduledFor, Instant previousCheck, Instant now,
            Collection<Long> configured, Set<Long> alreadyEmitted) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (long seconds : normalize(configured)) {
            Instant warningAt = scheduledFor.minusSeconds(seconds);
            if (warningAt.isAfter(previousCheck) && !warningAt.isAfter(now) && !alreadyEmitted.contains(seconds)) {
                result.add(seconds);
            }
        }
        return Set.copyOf(result);
    }

    public Set<Long> skipElapsed(
            Instant scheduledFor, Instant now, Collection<Long> configured, Set<Long> alreadyEmitted) {
        LinkedHashSet<Long> result = new LinkedHashSet<>(alreadyEmitted);
        for (long seconds : normalize(configured)) {
            if (!scheduledFor.minusSeconds(seconds).isAfter(now)) result.add(seconds);
        }
        return Set.copyOf(result);
    }
}
