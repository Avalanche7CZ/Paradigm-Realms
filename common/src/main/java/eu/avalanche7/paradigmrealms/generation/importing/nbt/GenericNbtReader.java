package eu.avalanche7.paradigmrealms.generation.importing.nbt;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.avalanche7.paradigmrealms.generation.importing.ImportLimits;

public final class GenericNbtReader {
    public NbtCompoundTag read(byte[] data) throws IOException {
        if (data.length > ImportLimits.MAX_DECOMPRESSED_BYTES) throw new IOException("NBT exceeds decompressed limit");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
            NbtType rootType = type(input.readUnsignedByte());
            if (rootType != NbtType.COMPOUND) throw new IOException("NBT root must be a compound");
            input.readUTF();
            Counter counter = new Counter();
            NbtCompoundTag result = (NbtCompoundTag) readPayload(input, rootType, 0, counter);
            if (input.read() != -1) throw new IOException("trailing data follows NBT root");
            return result;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new IOException("malformed NBT: " + exception.getMessage(), exception);
        }
    }

    private NbtTag readPayload(DataInputStream input, NbtType type, int depth, Counter counter) throws IOException {
        if (depth > ImportLimits.MAX_NBT_DEPTH) throw new IOException("NBT nesting exceeds 64 levels");
        counter.add(1);
        return switch (type) {
            case BYTE -> new NbtTag.ByteTag(input.readByte()); case SHORT -> new NbtTag.ShortTag(input.readShort());
            case INT -> new NbtTag.IntTag(input.readInt()); case LONG -> new NbtTag.LongTag(input.readLong());
            case FLOAT -> new NbtTag.FloatTag(input.readFloat()); case DOUBLE -> new NbtTag.DoubleTag(input.readDouble());
            case STRING -> new NbtTag.StringTag(input.readUTF());
            case BYTE_ARRAY -> new NbtTag.ByteArrayTag(readBytes(input, boundedLength(input.readInt(), 1, counter)));
            case INT_ARRAY -> { int length = boundedLength(input.readInt(), 4, counter); int[] value = new int[length];
                for (int i = 0; i < length; i++) value[i] = input.readInt(); yield new NbtTag.IntArrayTag(value); }
            case LONG_ARRAY -> { int length = boundedLength(input.readInt(), 8, counter); long[] value = new long[length];
                for (int i = 0; i < length; i++) value[i] = input.readLong(); yield new NbtTag.LongArrayTag(value); }
            case LIST -> readList(input, depth, counter); case COMPOUND -> readCompound(input, depth, counter);
            case END, NUMBER -> throw new IOException("invalid NBT payload type " + type);
        };
    }

    private NbtListTag readList(DataInputStream input, int depth, Counter counter) throws IOException {
        NbtType elementType = type(input.readUnsignedByte());
        int length = boundedLength(input.readInt(), 1, counter);
        if (elementType == NbtType.END && length != 0) throw new IOException("non-empty END list");
        List<NbtTag> values = new ArrayList<>(length);
        for (int index = 0; index < length; index++) values.add(readPayload(input, elementType, depth + 1, counter));
        return new NbtListTag(elementType, values);
    }

    private NbtCompoundTag readCompound(DataInputStream input, int depth, Counter counter) throws IOException {
        Map<String, NbtTag> values = new LinkedHashMap<>();
        while (true) {
            NbtType type = type(input.readUnsignedByte());
            if (type == NbtType.END) break;
            String name = input.readUTF();
            if (values.putIfAbsent(name, readPayload(input, type, depth + 1, counter)) != null) {
                throw new IOException("duplicate compound key " + name);
            }
        }
        return new NbtCompoundTag(values);
    }

    private static byte[] readBytes(DataInputStream input, int length) throws IOException {
        byte[] value = new byte[length]; input.readFully(value); return value;
    }
    private static int boundedLength(int length, int width, Counter counter) throws IOException {
        if (length < 0 || (long) length * width > ImportLimits.MAX_DECOMPRESSED_BYTES) throw new IOException("invalid or oversized NBT array");
        counter.add(length); return length;
    }
    private static NbtType type(int id) throws IOException {
        try { return NbtType.byId(id); } catch (IllegalArgumentException exception) { throw new IOException(exception.getMessage(), exception); }
    }
    private static final class Counter {
        private long value;
        void add(long amount) throws IOException {
            value = Math.addExact(value, amount);
            if (value > ImportLimits.MAX_NBT_ELEMENTS) throw new IOException("NBT element count exceeds limit");
        }
    }
}
