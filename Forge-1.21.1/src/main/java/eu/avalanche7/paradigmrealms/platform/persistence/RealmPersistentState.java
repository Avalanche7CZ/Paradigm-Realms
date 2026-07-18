package eu.avalanche7.paradigmrealms.platform.persistence;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.persistence.StateLoadResult;
import eu.avalanche7.paradigmrealms.persistence.codec.RealmSchemaV1Codec;
import eu.avalanche7.paradigmrealms.persistence.codec.MalformedStorageException;
import eu.avalanche7.paradigmrealms.persistence.data.StorageValue;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;
import eu.avalanche7.paradigmrealms.persistence.migration.MigrationRegistry;
import eu.avalanche7.paradigmrealms.persistence.migration.RealmSchemaV1ToV2Migration;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.AbstractNbtNumber;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtString;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.PersistentState;

public final class RealmPersistentState extends PersistentState {
    public static final Type<RealmPersistentState> TYPE = new Type<>(
            RealmPersistentState::new,
            RealmPersistentState::fromNbt,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final RealmSchemaV1Codec codec = new RealmSchemaV1Codec();
    private final StorageNbt nbtAdapter = new StorageNbt();
    private RealmStateDtoV1 state;
    private List<ValidationIssue> loadIssues;
    private boolean writable;
    private NbtCompound preservedMalformedRoot;

    public RealmPersistentState() {
        this(RealmStateDtoV1.empty(), List.of(), true, null);
    }

    private RealmPersistentState(
            RealmStateDtoV1 state,
            List<ValidationIssue> loadIssues,
            boolean writable,
            NbtCompound preservedMalformedRoot) {
        this.state = Objects.requireNonNull(state, "state");
        this.loadIssues = List.copyOf(loadIssues);
        this.writable = writable;
        this.preservedMalformedRoot = preservedMalformedRoot;
    }

    public static RealmPersistentState fromNbt(
            NbtCompound root, RegistryWrapper.WrapperLookup registries) {
        Objects.requireNonNull(root, "root");
        try {
            StorageNbt nbtAdapter = new StorageNbt();
            StorageValue.ObjectValue storage = nbtAdapter.fromNbt(root);
            int sourceSchema = schemaVersion(storage);
            StorageValue.ObjectValue migrated = migrate(storage, sourceSchema);
            RealmStateDtoV1 decoded = new RealmSchemaV1Codec().decode(migrated);
            RealmPersistentState state = new RealmPersistentState(decoded, List.of(), true, null);
            if (sourceSchema < SchemaVersion.CURRENT.value()) state.markDirty();
            return state;
        } catch (IllegalArgumentException exception) {
            ValidationIssue issue = ValidationIssue.error(
                    "MALFORMED_NBT", "root", exception.getMessage());
            return new RealmPersistentState(RealmStateDtoV1.empty(), List.of(issue), false, root.copy());
        }
    }

    private static int schemaVersion(StorageValue.ObjectValue root) {
        StorageValue value = root.get("schema_version");
        if (!(value instanceof StorageValue.LongValue number)) {
            throw new MalformedStorageException("root.schema_version", "expected integer");
        }
        return Math.toIntExact(number.value());
    }

    private static StorageValue.ObjectValue migrate(StorageValue.ObjectValue source, int sourceSchema) {
        if (sourceSchema >= SchemaVersion.CURRENT.value()) return source;
        MigrationRegistry migrations = new MigrationRegistry();
        migrations.register(new RealmSchemaV1ToV2Migration());
        return migrations.migrate(source, sourceSchema, SchemaVersion.CURRENT.value());
    }

    public StateLoadResult loadResult() {
        return new StateLoadResult(state, loadIssues, writable);
    }

    public boolean replace(RealmStateDtoV1 replacement) {
        if (!writable) {
            throw new IllegalStateException("malformed persistent state is read-only");
        }
        Objects.requireNonNull(replacement, "replacement");
        if (state.equals(replacement)) {
            return false;
        }
        state = replacement;
        markDirty();
        return true;
    }

    public void restoreAfterFailedSave(RealmStateDtoV1 previous) {
        state = Objects.requireNonNull(previous, "previous");
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound root, RegistryWrapper.WrapperLookup registries) {
        if (preservedMalformedRoot != null) {
            return root.copyFrom(preservedMalformedRoot);
        }
        return root.copyFrom(nbtAdapter.toNbt(codec.encode(state)));
    }

    private static final class StorageNbt {
        private NbtCompound toNbt(StorageValue.ObjectValue value) {
            return (NbtCompound) toNbtElement(value, "root");
        }

        private StorageValue.ObjectValue fromNbt(NbtCompound compound) {
            return (StorageValue.ObjectValue) fromNbtElement(compound, "root");
        }

        private NbtElement toNbtElement(StorageValue value, String path) {
            return switch (value) {
                case StorageValue.ObjectValue object -> {
                    NbtCompound compound = new NbtCompound();
                    object.values().forEach((key, child) ->
                            compound.put(key, toNbtElement(child, path + "." + key)));
                    yield compound;
                }
                case StorageValue.ListValue list -> {
                    NbtList result = new NbtList();
                    for (int index = 0; index < list.values().size(); index++) {
                        result.add(toNbtElement(list.values().get(index), path + "[" + index + "]"));
                    }
                    yield result;
                }
                case StorageValue.StringValue string -> isUuidPath(path)
                        ? NbtHelper.fromUuid(UUID.fromString(string.value())) : NbtString.of(string.value());
                case StorageValue.LongValue number -> NbtLong.of(number.value());
                case StorageValue.DoubleValue number -> NbtDouble.of(number.value());
            };
        }

        private StorageValue fromNbtElement(NbtElement element, String path) {
            if (element instanceof NbtCompound compound) {
                Map<String, StorageValue> values = new LinkedHashMap<>();
                for (String key : compound.getKeys()) {
                    values.put(key, fromNbtElement(compound.get(key), path + "." + key));
                }
                return new StorageValue.ObjectValue(values);
            }
            if (element instanceof NbtList list) {
                java.util.ArrayList<StorageValue> values = new java.util.ArrayList<>(list.size());
                for (int index = 0; index < list.size(); index++) {
                    values.add(fromNbtElement(list.get(index), path + "[" + index + "]"));
                }
                return new StorageValue.ListValue(values);
            }
            if (element instanceof NbtString string) {
                if (isUuidPath(path)) throw new MalformedStorageException(
                        path, "expected UUID int array");
                return new StorageValue.StringValue(string.asString());
            }
            if (element instanceof NbtIntArray intArray && isUuidPath(path)) {
                try {
                    return new StorageValue.StringValue(NbtHelper.toUuid(intArray).toString());
                } catch (IllegalArgumentException exception) {
                    throw new MalformedStorageException(path, "invalid UUID int array");
                }
            }
            if (element instanceof AbstractNbtNumber number) {
                if (element.getType() == NbtElement.FLOAT_TYPE
                        || element.getType() == NbtElement.DOUBLE_TYPE) {
                    return new StorageValue.DoubleValue(number.doubleValue());
                }
                return new StorageValue.LongValue(number.longValue());
            }
            throw new MalformedStorageException(path, "unsupported NBT type " + element.getType());
        }

        private static boolean isUuidPath(String path) {
            return path.endsWith("_uuid") || path.matches(".*\\.(members|visitors|managers)\\[\\d+]");
        }
    }
}
