package eu.avalanche7.paradigmrealms.generation.importing.nbt;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record NbtCompoundTag(Map<String, NbtTag> values) implements NbtTag {
    public NbtCompoundTag { values = Map.copyOf(Objects.requireNonNull(values, "values")); }
    @Override public NbtType type() { return NbtType.COMPOUND; }
    public Set<String> keys() { return values.keySet(); }
    public NbtTag get(String key) { return values.get(key); }
    public boolean contains(String key) { return values.containsKey(key); }
    public boolean contains(String key, NbtType type) {
        NbtTag value = values.get(key); return value != null && type.accepts(value.type());
    }
    public boolean isEmpty() { return values.isEmpty(); }
    public int size() { return values.size(); }
    public byte byteValue(String key) { return number(required(key)).byteValue(); }
    public short shortValue(String key) { return number(required(key)).shortValue(); }
    public int intValue(String key) { return number(required(key)).intValue(); }
    public long longValue(String key) { return number(required(key)).longValue(); }
    public String string(String key) { NbtTag value = values.get(key); return value instanceof NbtTag.StringTag tag ? tag.value() : ""; }
    public byte[] byteArray(String key) { return ((NbtTag.ByteArrayTag) required(key)).value(); }
    public int[] intArray(String key) { return ((NbtTag.IntArrayTag) required(key)).value(); }
    public long[] longArray(String key) { return ((NbtTag.LongArrayTag) required(key)).value(); }
    public NbtCompoundTag compound(String key) { return (NbtCompoundTag) required(key); }
    public NbtListTag list(String key, NbtType elementType) {
        NbtTag value = values.get(key);
        if (!(value instanceof NbtListTag list) || (list.elementType() != elementType && !list.isEmpty())) {
            return new NbtListTag(elementType, java.util.List.of());
        }
        return list;
    }
    private NbtTag required(String key) {
        NbtTag value = values.get(key); if (value == null) throw new IllegalArgumentException("missing NBT key " + key); return value;
    }
    static Number number(NbtTag value) {
        return switch (value) {
            case NbtTag.ByteTag tag -> tag.value(); case NbtTag.ShortTag tag -> tag.value();
            case NbtTag.IntTag tag -> tag.value(); case NbtTag.LongTag tag -> tag.value();
            case NbtTag.FloatTag tag -> tag.value(); case NbtTag.DoubleTag tag -> tag.value();
            default -> throw new IllegalArgumentException("NBT value is not numeric");
        };
    }
}
