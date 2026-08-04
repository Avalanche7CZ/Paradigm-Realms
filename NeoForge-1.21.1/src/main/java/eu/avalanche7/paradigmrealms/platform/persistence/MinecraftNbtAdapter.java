package eu.avalanche7.paradigmrealms.platform.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.nbt.NbtShort;
import net.minecraft.nbt.NbtString;

public final class MinecraftNbtAdapter {
    public NbtCompound toMinecraft(NbtCompoundTag value) {
        return (NbtCompound) toMinecraftTag(value);
    }

    public NbtCompoundTag fromMinecraft(NbtCompound value) {
        return (NbtCompoundTag) fromMinecraftTag(value);
    }

    private NbtElement toMinecraftTag(NbtTag value) {
        return switch (value) {
            case NbtTag.ByteTag tag -> NbtByte.of(tag.value());
            case NbtTag.ShortTag tag -> NbtShort.of(tag.value());
            case NbtTag.IntTag tag -> NbtInt.of(tag.value());
            case NbtTag.LongTag tag -> NbtLong.of(tag.value());
            case NbtTag.FloatTag tag -> NbtFloat.of(tag.value());
            case NbtTag.DoubleTag tag -> NbtDouble.of(tag.value());
            case NbtTag.StringTag tag -> NbtString.of(tag.value());
            case NbtTag.ByteArrayTag tag -> new NbtByteArray(tag.value());
            case NbtTag.IntArrayTag tag -> new NbtIntArray(tag.value());
            case NbtTag.LongArrayTag tag -> new NbtLongArray(tag.value());
            case NbtListTag tag -> {
                NbtList list = new NbtList();
                tag.values().forEach(child -> list.add(toMinecraftTag(child)));
                yield list;
            }
            case NbtCompoundTag tag -> {
                NbtCompound compound = new NbtCompound();
                tag.values().forEach((key, child) -> compound.put(key, toMinecraftTag(child)));
                yield compound;
            }
        };
    }

    private NbtTag fromMinecraftTag(NbtElement value) {
        if (value instanceof NbtByte tag) return new NbtTag.ByteTag(tag.byteValue());
        if (value instanceof NbtShort tag) return new NbtTag.ShortTag(tag.shortValue());
        if (value instanceof NbtInt tag) return new NbtTag.IntTag(tag.intValue());
        if (value instanceof NbtLong tag) return new NbtTag.LongTag(tag.longValue());
        if (value instanceof NbtFloat tag) return new NbtTag.FloatTag(tag.floatValue());
        if (value instanceof NbtDouble tag) return new NbtTag.DoubleTag(tag.doubleValue());
        if (value instanceof NbtString tag) return new NbtTag.StringTag(tag.asString());
        if (value instanceof NbtByteArray tag) return new NbtTag.ByteArrayTag(tag.getByteArray());
        if (value instanceof NbtIntArray tag) return new NbtTag.IntArrayTag(tag.getIntArray());
        if (value instanceof NbtLongArray tag) return new NbtTag.LongArrayTag(tag.getLongArray());
        if (value instanceof NbtList tag) {
            ArrayList<NbtTag> children = new ArrayList<>(tag.size());
            for (int index = 0; index < tag.size(); index++) children.add(fromMinecraftTag(tag.get(index)));
            NbtType elementType = children.isEmpty() ? NbtType.END : children.getFirst().type();
            return new NbtListTag(elementType, children);
        }
        if (value instanceof NbtCompound tag) {
            Map<String, NbtTag> children = new LinkedHashMap<>();
            for (String key : tag.getKeys()) children.put(key, fromMinecraftTag(tag.get(key)));
            return new NbtCompoundTag(children);
        }
        throw new IllegalArgumentException("unsupported Minecraft NBT type " + value.getType());
    }
}
