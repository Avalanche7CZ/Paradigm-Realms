package eu.avalanche7.paradigmrealms.generation.importing;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

public record SymbolicBlockState(String blockId, Map<String, String> properties) {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_.-]+");
    public SymbolicBlockState {
        Objects.requireNonNull(blockId, "blockId"); Objects.requireNonNull(properties, "properties");
        if (!ID.matcher(blockId).matches()) throw new IllegalArgumentException("invalid symbolic block ID " + blockId);
        TreeMap<String, String> sorted = new TreeMap<>();
        properties.forEach((key, value) -> {
            if (!TOKEN.matcher(key).matches() || !TOKEN.matcher(value).matches()) throw new IllegalArgumentException("invalid block-state property");
            if (sorted.putIfAbsent(key, value) != null) throw new IllegalArgumentException("duplicate block-state property " + key);
        });
        properties = java.util.Collections.unmodifiableMap(sorted);
    }
    public static SymbolicBlockState parse(String source) {
        String value = source.trim(); int bracket = value.indexOf('[');
        String id = bracket < 0 ? value : value.substring(0, bracket);
        if (!id.contains(":")) id = "minecraft:" + id;
        Map<String, String> properties = new TreeMap<>();
        if (bracket >= 0) {
            if (!value.endsWith("]")) throw new IllegalArgumentException("malformed block-state properties");
            String body = value.substring(bracket + 1, value.length() - 1);
            if (!body.isBlank()) for (String assignment : body.split(",")) {
                String[] parts = assignment.split("=", 2);
                if (parts.length != 2 || properties.putIfAbsent(parts[0], parts[1]) != null) {
                    throw new IllegalArgumentException("malformed or duplicate block-state property " + assignment);
                }
            }
        }
        return new SymbolicBlockState(id, properties);
    }
    public String namespace() { return blockId.substring(0, blockId.indexOf(':')); }
    public String canonical() {
        if (properties.isEmpty()) return blockId;
        return blockId + "[" + properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(",")) + "]";
    }
}
