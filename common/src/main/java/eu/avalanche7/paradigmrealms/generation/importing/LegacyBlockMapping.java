package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.Map;
import java.util.Optional;

public final class LegacyBlockMapping {
    private final Map<String, SymbolicBlockState> overrides;
    public LegacyBlockMapping(Map<String, SymbolicBlockState> overrides) { this.overrides = Map.copyOf(overrides); }
    public static LegacyBlockMapping builtIn() { return new LegacyBlockMapping(Map.of()); }
    public Optional<SymbolicBlockState> resolve(int id, int data) {
        Optional<SymbolicBlockState> override = override(id, data);
        if (override.isPresent()) return override;
        String state = LegacyVanillaStates.resolve(id, data); return state == null ? Optional.empty() : Optional.of(SymbolicBlockState.parse(state));
    }
    public Optional<SymbolicBlockState> override(int id, int data) {
        SymbolicBlockState value = overrides.get(id + ":" + data);
        if (value == null) value = overrides.get(Integer.toString(id));
        return Optional.ofNullable(value);
    }
}
