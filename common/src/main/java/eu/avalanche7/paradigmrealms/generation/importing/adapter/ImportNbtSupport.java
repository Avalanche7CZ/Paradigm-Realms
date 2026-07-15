package eu.avalanche7.paradigmrealms.generation.importing.adapter;

import java.util.HashMap;
import java.util.Map;

import eu.avalanche7.paradigmrealms.generation.importing.SymbolicBlockState;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

final class ImportNbtSupport {
    private ImportNbtSupport() {}
    static int unsignedShort(NbtCompoundTag root, String key) {
        if (!root.contains(key, NbtType.NUMBER)) throw new IllegalArgumentException("missing numeric " + key);
        int value = root.shortValue(key) & 0xffff; if (value < 1) throw new IllegalArgumentException(key + " must be positive"); return value;
    }
    static int[] vector(NbtCompoundTag root, String key) {
        if (root.contains(key, NbtType.INT_ARRAY)) {
            int[] value = root.intArray(key); if (value.length != 3) throw new IllegalArgumentException(key + " must contain three integers"); return value;
        }
        if (root.contains(key, NbtType.COMPOUND)) {
            NbtCompoundTag value = root.compound(key); return new int[] {value.intValue("x"), value.intValue("y"), value.intValue("z")};
        }
        throw new IllegalArgumentException("missing vector " + key);
    }
    static SymbolicBlockState paletteState(NbtCompoundTag entry) {
        String name = entry.string("Name"); if (name.isBlank()) throw new IllegalArgumentException("palette state has no Name");
        Map<String, String> properties = new java.util.TreeMap<>();
        if (entry.contains("Properties", NbtType.COMPOUND)) {
            NbtCompoundTag source = entry.compound("Properties");
            source.keys().stream().sorted().forEach(key -> properties.put(key, source.string(key)));
        }
        if (!name.contains(":")) name = "minecraft:" + name;
        return new SymbolicBlockState(name, properties);
    }
    static Map<Integer, SymbolicBlockState> spongePalette(NbtCompoundTag palette) {
        Map<Integer, SymbolicBlockState> result = new HashMap<>();
        for (String state : palette.keys()) {
            int index = palette.intValue(state);
            if (index < 0 || result.putIfAbsent(index, SymbolicBlockState.parse(state)) != null) {
                throw new IllegalArgumentException("duplicate or negative palette index " + index);
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("palette is empty"); return Map.copyOf(result);
    }
    static int[] decodeVarInts(byte[] bytes, int expected) {
        int[] values = new int[expected]; int offset = 0;
        for (int index = 0; index < expected; index++) {
            int value = 0, shift = 0;
            while (true) {
                if (offset >= bytes.length) throw new IllegalArgumentException("block data ends before declared volume");
                int current = bytes[offset++] & 255; value |= (current & 127) << shift;
                if ((current & 128) == 0) break; shift += 7;
                if (shift >= 35) throw new IllegalArgumentException("invalid overlong palette varint");
            }
            values[index] = value;
        }
        if (offset != bytes.length) throw new IllegalArgumentException("block data contains trailing palette entries"); return values;
    }
    static Map<Position, NbtCompoundTag> positionedCompounds(NbtListTag list) {
        Map<Position, NbtCompoundTag> result = new HashMap<>();
        for (int index = 0; index < list.size(); index++) {
            NbtCompoundTag value = list.compound(index); int[] pos;
            if (value.contains("Pos", NbtType.INT_ARRAY)) pos = value.intArray("Pos");
            else if (value.contains("pos", NbtType.INT_ARRAY)) pos = value.intArray("pos");
            else if (value.contains("x", NbtType.NUMBER)) pos = new int[] {value.intValue("x"), value.intValue("y"), value.intValue("z")};
            else throw new IllegalArgumentException("block entity has no supported position");
            if (pos.length != 3) throw new IllegalArgumentException("block entity position must have three integers");
            Position key = new Position(pos[0], pos[1], pos[2]);
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("duplicate block entity position");
        }
        return result;
    }
    record Position(int x, int y, int z) {}
}
