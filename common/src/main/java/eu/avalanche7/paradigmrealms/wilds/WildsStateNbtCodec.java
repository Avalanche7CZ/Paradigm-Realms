package eu.avalanche7.paradigmrealms.wilds;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtCompoundTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtListTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtTag;
import eu.avalanche7.paradigmrealms.generation.importing.nbt.NbtType;

public final class WildsStateNbtCodec {
    public NbtCompoundTag encode(WildsState value) {
        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("schema_version", integer(value.schemaVersion()));
        root.put("revision", number(value.revision()));
        root.put("lifecycle", string(value.lifecycle().name()));
        root.put("active_epoch", number(value.activeEpoch()));
        root.put("active_seed", number(value.activeSeed()));
        value.activeProfile().ifPresent(profile -> root.put("active_profile", string(profile.value())));
        root.put("generation_verified", bool(value.generationVerified()));
        value.activatedAt().ifPresent(time -> root.put("activated_at", string(time.toString())));
        value.lastSuccessfulResetAt().ifPresent(time ->
                root.put("last_successful_reset_at", string(time.toString())));
        value.nextScheduledReset().ifPresent(time ->
                root.put("next_scheduled_reset", string(time.toString())));
        value.spawn().ifPresent(spawn -> root.put("spawn", encodeSpawn(spawn)));
        value.operation().ifPresent(operation -> root.put("operation", encodeOperation(operation)));
        value.failure().ifPresent(failure -> root.put("failure", encodeFailure(failure)));
        ArrayList<NbtTag> approvals = new ArrayList<>();
        value.approvedPlayerEpochs().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> approvals.add(compound(Map.of(
                        "player_uuid", new NbtTag.IntArrayTag(uuidToIntArray(entry.getKey())),
                        "epoch", number(entry.getValue())))));
        root.put("approved_players", new NbtListTag(NbtType.COMPOUND, approvals));
        return compound(root);
    }

    public WildsState decode(NbtCompoundTag root) {
        require(root, "schema_version", NbtType.INT);
        int schema = root.intValue("schema_version");
        if (schema != WildsState.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported Wilds schema " + schema);
        }
        long epoch = requiredLong(root, "active_epoch");
        Optional<WildsProfileId> profile = optionalString(root, "active_profile").map(WildsProfileId::new);
        Optional<WildsSpawn> spawn = root.contains("spawn", NbtType.COMPOUND)
                ? Optional.of(decodeSpawn(root.compound("spawn"))) : Optional.empty();
        Optional<WildsResetOperation> operation = root.contains("operation", NbtType.COMPOUND)
                ? Optional.of(decodeOperation(root.compound("operation"))) : Optional.empty();
        Optional<WildsFailure> failure = root.contains("failure", NbtType.COMPOUND)
                ? Optional.of(decodeFailure(root.compound("failure"))) : Optional.empty();
        Map<UUID, Long> approvals = new HashMap<>();
        if (root.contains("approved_players", NbtType.LIST)) {
            NbtListTag list = root.list("approved_players", NbtType.COMPOUND);
            for (int index = 0; index < list.size(); index++) {
                NbtCompoundTag item = list.compound(index);
                require(item, "player_uuid", NbtType.INT_ARRAY);
                UUID player = uuidFromIntArray(item.intArray("player_uuid"));
                if (approvals.put(player, requiredLong(item, "epoch")) != null) {
                    throw new IllegalArgumentException("duplicate approved player " + player);
                }
            }
        }
        return new WildsState(schema, requiredLong(root, "revision"),
                WildsLifecycleState.valueOf(requiredString(root, "lifecycle")), epoch,
                requiredLong(root, "active_seed"), profile, spawn,
                requiredBoolean(root, "generation_verified"),
                optionalInstant(root, "activated_at"), optionalInstant(root, "last_successful_reset_at"),
                optionalInstant(root, "next_scheduled_reset"), operation, approvals, failure);
    }

    private static NbtCompoundTag encodeSpawn(WildsSpawn value) {
        return compound(Map.of(
                "epoch", number(value.epoch()), "x", decimal(value.x()), "y", decimal(value.y()),
                "z", decimal(value.z()), "yaw", floating(value.yaw()), "pitch", floating(value.pitch())));
    }

    private static WildsSpawn decodeSpawn(NbtCompoundTag value) {
        return new WildsSpawn(requiredLong(value, "epoch"), requiredDouble(value, "x"),
                requiredDouble(value, "y"), requiredDouble(value, "z"),
                requiredFloat(value, "yaw"), requiredFloat(value, "pitch"));
    }

    private static NbtCompoundTag encodeOperation(WildsResetOperation value) {
        Map<String, NbtTag> operation = new LinkedHashMap<>();
        operation.put("operation_id", new NbtTag.IntArrayTag(uuidToIntArray(value.operationId())));
        operation.put("source_epoch", number(value.sourceEpoch()));
        operation.put("target_epoch", number(value.targetEpoch()));
        operation.put("source_seed", number(value.sourceSeed()));
        operation.put("target_seed", number(value.targetSeed()));
        operation.put("source_profile", string(value.sourceProfile().value()));
        operation.put("target_profile", string(value.targetProfile().value()));
        operation.put("created_at", string(value.createdAt().toString()));
        operation.put("scheduled_for", string(value.scheduledFor().toString()));
        operation.put("last_warning_check", string(value.lastWarningCheck().toString()));
        operation.put("emitted_warnings", new NbtTag.LongArrayTag(
                value.emittedWarningsSeconds().stream().mapToLong(Long::longValue).sorted().toArray()));
        operation.put("settings", compound(Map.of(
                "rotate_seed", bool(value.settings().rotateSeed()),
                "shutdown", bool(value.settings().shutdownWhenPrepared()),
                "retention", integer(value.settings().backupRetentionCount()),
                "delete_after_verify", bool(value.settings().deleteOldBackupsAfterVerification()))));
        return compound(operation);
    }

    private static WildsResetOperation decodeOperation(NbtCompoundTag value) {
        require(value, "operation_id", NbtType.INT_ARRAY);
        NbtCompoundTag settings = requiredCompound(value, "settings");
        require(value, "emitted_warnings", NbtType.LONG_ARRAY);
        Set<Long> warnings = new HashSet<>();
        for (long warning : value.longArray("emitted_warnings")) warnings.add(warning);
        return new WildsResetOperation(uuidFromIntArray(value.intArray("operation_id")),
                requiredLong(value, "source_epoch"), requiredLong(value, "target_epoch"),
                requiredLong(value, "source_seed"), requiredLong(value, "target_seed"),
                new WildsProfileId(requiredString(value, "source_profile")),
                new WildsProfileId(requiredString(value, "target_profile")),
                Instant.parse(requiredString(value, "created_at")),
                Instant.parse(requiredString(value, "scheduled_for")),
                Instant.parse(requiredString(value, "last_warning_check")), warnings,
                new WildsOperationSettings(requiredBoolean(settings, "rotate_seed"),
                        requiredBoolean(settings, "shutdown"), requiredInt(settings, "retention"),
                        requiredBoolean(settings, "delete_after_verify")));
    }

    private static NbtCompoundTag encodeFailure(WildsFailure value) {
        return compound(Map.of("code", string(value.code()), "detail", string(value.detail()),
                "occurred_at", string(value.occurredAt().toString())));
    }

    private static WildsFailure decodeFailure(NbtCompoundTag value) {
        return new WildsFailure(requiredString(value, "code"), requiredString(value, "detail"),
                Instant.parse(requiredString(value, "occurred_at")));
    }

    private static Optional<String> optionalString(NbtCompoundTag value, String key) {
        return value.contains(key, NbtType.STRING) ? Optional.of(value.string(key)) : Optional.empty();
    }

    private static Optional<Instant> optionalInstant(NbtCompoundTag value, String key) {
        return optionalString(value, key).map(Instant::parse);
    }

    private static String requiredString(NbtCompoundTag value, String key) {
        require(value, key, NbtType.STRING); return value.string(key);
    }
    private static long requiredLong(NbtCompoundTag value, String key) {
        require(value, key, NbtType.LONG); return value.longValue(key);
    }
    private static int requiredInt(NbtCompoundTag value, String key) {
        require(value, key, NbtType.INT); return value.intValue(key);
    }
    private static double requiredDouble(NbtCompoundTag value, String key) {
        require(value, key, NbtType.DOUBLE); return ((NbtTag.DoubleTag) value.get(key)).value();
    }
    private static float requiredFloat(NbtCompoundTag value, String key) {
        require(value, key, NbtType.FLOAT); return ((NbtTag.FloatTag) value.get(key)).value();
    }
    private static boolean requiredBoolean(NbtCompoundTag value, String key) {
        require(value, key, NbtType.BYTE); return value.byteValue(key) != 0;
    }
    private static NbtCompoundTag requiredCompound(NbtCompoundTag value, String key) {
        require(value, key, NbtType.COMPOUND); return value.compound(key);
    }
    private static void require(NbtCompoundTag value, String key, NbtType type) {
        if (!value.contains(key, type)) throw new IllegalArgumentException("missing or invalid " + key);
    }

    private static NbtCompoundTag compound(Map<String, NbtTag> values) { return new NbtCompoundTag(values); }
    private static NbtTag.StringTag string(String value) { return new NbtTag.StringTag(value); }
    private static NbtTag.ByteTag bool(boolean value) { return new NbtTag.ByteTag((byte) (value ? 1 : 0)); }
    private static NbtTag.IntTag integer(int value) { return new NbtTag.IntTag(value); }
    private static NbtTag.LongTag number(long value) { return new NbtTag.LongTag(value); }
    private static NbtTag.FloatTag floating(float value) { return new NbtTag.FloatTag(value); }
    private static NbtTag.DoubleTag decimal(double value) { return new NbtTag.DoubleTag(value); }

    private static int[] uuidToIntArray(UUID uuid) {
        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();
        return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    private static UUID uuidFromIntArray(int[] value) {
        if (value.length != 4) throw new IllegalArgumentException("malformed UUID int array");
        long most = (long) value[0] << 32 | value[1] & 0xffffffffL;
        long least = (long) value[2] << 32 | value[3] & 0xffffffffL;
        return new UUID(most, least);
    }
}
