package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.Objects;
import java.util.Optional;

public interface SymbolicBlockStateResolver<T> {
    Resolution<T> resolve(SymbolicBlockState state);

    record Resolution<T>(Optional<T> value, Optional<String> requiredMod, Optional<String> error) {
        public Resolution {
            Objects.requireNonNull(value, "value"); Objects.requireNonNull(requiredMod, "requiredMod");
            Objects.requireNonNull(error, "error");
            if (value.isPresent() == error.isPresent()) throw new IllegalArgumentException("resolution must contain value or error");
        }
        public static <T> Resolution<T> success(T value, Optional<String> requiredMod) {
            return new Resolution<>(Optional.of(value), requiredMod, Optional.empty());
        }
        public static <T> Resolution<T> failure(String error) {
            return new Resolution<>(Optional.empty(), Optional.empty(), Optional.of(error));
        }
    }
}
