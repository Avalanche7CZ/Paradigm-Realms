package eu.avalanche7.paradigmrealms.generation.importing.nbt;

import java.util.List;
import java.util.Objects;

public record NbtListTag(NbtType elementType, List<NbtTag> values) implements NbtTag {
    public NbtListTag {
        Objects.requireNonNull(elementType, "elementType");
        values = List.copyOf(values);
        if (elementType == NbtType.END && !values.isEmpty()) throw new IllegalArgumentException("non-empty END list");
        if (values.stream().anyMatch(value -> value.type() != elementType)) throw new IllegalArgumentException("heterogeneous NBT list");
    }
    @Override public NbtType type() { return NbtType.LIST; }
    public int size() { return values.size(); }
    public boolean isEmpty() { return values.isEmpty(); }
    public NbtCompoundTag compound(int index) { return (NbtCompoundTag) values.get(index); }
    public NbtListTag list(int index) { return (NbtListTag) values.get(index); }
    public int intValue(int index) { return NbtCompoundTag.number(values.get(index)).intValue(); }
    public long[] longArray(int index) { return ((NbtTag.LongArrayTag) values.get(index)).value(); }
}
