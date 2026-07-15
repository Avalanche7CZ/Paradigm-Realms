package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.List;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;

public interface BlockEntitySanitizationPort {
    Result sanitize(SymbolicBlockState state, NbtCompoundTag data, ImportPolicy policy);

    record Result(boolean allowed, boolean stripped, List<String> warnings, List<String> errors) {
        public Result {
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
            if (allowed == !errors.isEmpty()) throw new IllegalArgumentException("allowed result/error mismatch");
        }
        public static Result stripped(String warning) { return new Result(true, true, List.of(warning), List.of()); }
        public static Result stripped(List<String> warnings) { return new Result(true, true, warnings, List.of()); }
        public static Result rejected(String error) { return new Result(false, false, List.of(), List.of(error)); }
    }
}
