package eu.avalanche7.paradigmrealms.generation.importing.nbt;

public sealed interface NbtTag permits NbtTag.ByteTag, NbtTag.ShortTag, NbtTag.IntTag,
        NbtTag.LongTag, NbtTag.FloatTag, NbtTag.DoubleTag, NbtTag.ByteArrayTag,
        NbtTag.StringTag, NbtListTag, NbtCompoundTag, NbtTag.IntArrayTag, NbtTag.LongArrayTag {
    NbtType type();

    record ByteTag(byte value) implements NbtTag { @Override public NbtType type() { return NbtType.BYTE; } }
    record ShortTag(short value) implements NbtTag { @Override public NbtType type() { return NbtType.SHORT; } }
    record IntTag(int value) implements NbtTag { @Override public NbtType type() { return NbtType.INT; } }
    record LongTag(long value) implements NbtTag { @Override public NbtType type() { return NbtType.LONG; } }
    record FloatTag(float value) implements NbtTag { @Override public NbtType type() { return NbtType.FLOAT; } }
    record DoubleTag(double value) implements NbtTag { @Override public NbtType type() { return NbtType.DOUBLE; } }
    record StringTag(String value) implements NbtTag {
        public StringTag { if (value == null) throw new NullPointerException("value"); }
        @Override public NbtType type() { return NbtType.STRING; }
    }
    record ByteArrayTag(byte[] value) implements NbtTag {
        public ByteArrayTag { value = value.clone(); }
        @Override public byte[] value() { return value.clone(); }
        @Override public NbtType type() { return NbtType.BYTE_ARRAY; }
    }
    record IntArrayTag(int[] value) implements NbtTag {
        public IntArrayTag { value = value.clone(); }
        @Override public int[] value() { return value.clone(); }
        @Override public NbtType type() { return NbtType.INT_ARRAY; }
    }
    record LongArrayTag(long[] value) implements NbtTag {
        public LongArrayTag { value = value.clone(); }
        @Override public long[] value() { return value.clone(); }
        @Override public NbtType type() { return NbtType.LONG_ARRAY; }
    }
}
