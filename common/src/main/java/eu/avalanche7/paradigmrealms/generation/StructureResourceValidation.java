package eu.avalanche7.paradigmrealms.generation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StructureResourceValidation(Optional<String> fingerprint, List<String> reasons) {
    public StructureResourceValidation {
        fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }

    public static StructureResourceValidation failed(String reason) {
        return new StructureResourceValidation(Optional.empty(), List.of(reason));
    }
}
