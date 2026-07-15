package eu.avalanche7.paradigmrealms.persistence.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.persistence.data.StorageValue;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmFailureDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmInvitationDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmOperationDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;

public final class RealmSchemaV1Codec {
    public StorageValue.ObjectValue encode(RealmStateDtoV1 state) {
        Map<String, StorageValue> root = new LinkedHashMap<>();
        root.put("schema_version", number(state.schemaVersion()));
        root.put("revision", number(state.revision()));
        root.put("next_realm_id", number(state.nextRealmId()));
        root.put("realms", new StorageValue.ListValue(state.realms().stream()
                .<StorageValue>map(this::encodeRealm).toList()));
        root.put("invitations", new StorageValue.ListValue(state.invitations().stream()
                .<StorageValue>map(this::encodeInvitation).toList()));
        return new StorageValue.ObjectValue(root);
    }

    public RealmStateDtoV1 decode(StorageValue.ObjectValue root) {
        int schema = intValue(root, "schema_version", "root");
        long revision = longValue(root, "revision", "root");
        long nextRealmId = longValue(root, "next_realm_id", "root");
        StorageValue.ListValue realms = listValue(root, "realms", "root");
        List<RealmDtoV1> decoded = new ArrayList<>(realms.values().size());
        for (int i = 0; i < realms.values().size(); i++) {
            StorageValue value = realms.values().get(i);
            if (!(value instanceof StorageValue.ObjectValue object)) {
                throw malformed("root.realms[" + i + "]", "expected object");
            }
            decoded.add(decodeRealm(object, "root.realms[" + i + "]"));
        }
        List<RealmInvitationDtoV1> invitations = new ArrayList<>();
        StorageValue rawInvitations = root.get("invitations");
        if (rawInvitations != null) {
            if (!(rawInvitations instanceof StorageValue.ListValue list)) {
                throw malformed("root.invitations", "expected list");
            }
            for (int i = 0; i < list.values().size(); i++) {
                StorageValue value = list.values().get(i);
                if (!(value instanceof StorageValue.ObjectValue object)) {
                    throw malformed("root.invitations[" + i + "]", "expected object");
                }
                invitations.add(decodeInvitation(object, "root.invitations[" + i + "]"));
            }
        }
        return new RealmStateDtoV1(schema, revision, nextRealmId, decoded, invitations);
    }

    public StorageValue.ObjectValue encodeRealm(RealmDtoV1 realm) {
        Map<String, StorageValue> value = new LinkedHashMap<>();
        value.put("record_schema", number(realm.recordSchema()));
        value.put("id", number(realm.id()));
        value.put("owner_uuid", string(realm.ownerUuid()));
        value.put("state", string(realm.state()));
        value.put("dimension", string(realm.dimension()));
        value.put("cell_x", number(realm.cellX()));
        value.put("cell_z", number(realm.cellZ()));
        value.put("cell_min_chunk_x", number(realm.cellMinChunkX()));
        value.put("cell_min_chunk_z", number(realm.cellMinChunkZ()));
        value.put("cell_max_chunk_x", number(realm.cellMaxChunkX()));
        value.put("cell_max_chunk_z", number(realm.cellMaxChunkZ()));
        value.put("build_min_chunk_x", number(realm.buildMinChunkX()));
        value.put("build_min_chunk_z", number(realm.buildMinChunkZ()));
        value.put("build_max_chunk_x", number(realm.buildMaxChunkX()));
        value.put("build_max_chunk_z", number(realm.buildMaxChunkZ()));
        value.put("spawn_x", decimal(realm.spawnX()));
        value.put("spawn_y", decimal(realm.spawnY()));
        value.put("spawn_z", decimal(realm.spawnZ()));
        value.put("spawn_yaw", decimal(realm.spawnYaw()));
        value.put("spawn_pitch", decimal(realm.spawnPitch()));
        value.put("preset_id", string(realm.presetId()));
        value.put("members", strings(realm.memberUuids()));
        value.put("visitors", strings(realm.visitorUuids()));
        value.put("access_policy", string(realm.accessPolicy()));
        value.put("created_at_epoch_ms", number(realm.createdAtEpochMs()));
        realm.operation().ifPresent(operation -> value.put("operation", encodeOperation(operation)));
        realm.failure().ifPresent(failure -> value.put("failure", encodeFailure(failure)));
        return new StorageValue.ObjectValue(value);
    }

    public RealmDtoV1 decodeRealm(StorageValue.ObjectValue value, String path) {
        Optional<RealmOperationDtoV1> operation = Optional.empty();
        StorageValue rawOperation = value.get("operation");
        if (rawOperation != null) {
            if (!(rawOperation instanceof StorageValue.ObjectValue object)) {
                throw malformed(path + ".operation", "expected object");
            }
            operation = Optional.of(decodeOperation(object, path + ".operation"));
        }
        Optional<RealmFailureDtoV1> failure = Optional.empty();
        StorageValue rawFailure = value.get("failure");
        if (rawFailure != null) {
            if (!(rawFailure instanceof StorageValue.ObjectValue object)) {
                throw malformed(path + ".failure", "expected object");
            }
            failure = Optional.of(decodeFailure(object, path + ".failure"));
        }
        return new RealmDtoV1(
                intValue(value, "record_schema", path),
                longValue(value, "id", path),
                stringValue(value, "owner_uuid", path),
                stringValue(value, "state", path),
                stringValue(value, "dimension", path),
                intValue(value, "cell_x", path), intValue(value, "cell_z", path),
                intValue(value, "cell_min_chunk_x", path), intValue(value, "cell_min_chunk_z", path),
                intValue(value, "cell_max_chunk_x", path), intValue(value, "cell_max_chunk_z", path),
                intValue(value, "build_min_chunk_x", path), intValue(value, "build_min_chunk_z", path),
                intValue(value, "build_max_chunk_x", path), intValue(value, "build_max_chunk_z", path),
                doubleValue(value, "spawn_x", path), doubleValue(value, "spawn_y", path),
                doubleValue(value, "spawn_z", path),
                floatValue(value, "spawn_yaw", path), floatValue(value, "spawn_pitch", path),
                stringValue(value, "preset_id", path),
                stringList(value, "members", path), stringList(value, "visitors", path),
                stringValue(value, "access_policy", path),
                longValue(value, "created_at_epoch_ms", path), operation, failure);
    }

    private StorageValue.ObjectValue encodeOperation(RealmOperationDtoV1 operation) {
        return new StorageValue.ObjectValue(Map.of(
                "operation_uuid", string(operation.operationUuid()),
                "preset_revision", string(operation.presetRevision()),
                "attempt", number(operation.attempt()),
                "updated_at_epoch_ms", number(operation.updatedAtEpochMs())));
    }

    private RealmOperationDtoV1 decodeOperation(StorageValue.ObjectValue value, String path) {
        return new RealmOperationDtoV1(
                stringValue(value, "operation_uuid", path),
                stringValue(value, "preset_revision", path),
                intValue(value, "attempt", path),
                longValue(value, "updated_at_epoch_ms", path));
    }

    private StorageValue.ObjectValue encodeFailure(RealmFailureDtoV1 failure) {
        return new StorageValue.ObjectValue(Map.of(
                "code", string(failure.code()),
                "detail", string(failure.detail()),
                "failed_phase", string(failure.failedPhase()),
                "operation_uuid", string(failure.operationUuid()),
                "attempt", number(failure.attempt()),
                "updated_at_epoch_ms", number(failure.updatedAtEpochMs())));
    }

    private RealmFailureDtoV1 decodeFailure(StorageValue.ObjectValue value, String path) {
        return new RealmFailureDtoV1(
                stringValue(value, "code", path), stringValue(value, "detail", path),
                stringValue(value, "failed_phase", path), stringValue(value, "operation_uuid", path),
                intValue(value, "attempt", path), longValue(value, "updated_at_epoch_ms", path));
    }

    private StorageValue.ObjectValue encodeInvitation(RealmInvitationDtoV1 invitation) {
        Map<String, StorageValue> value = new LinkedHashMap<>();
        value.put("record_schema", number(invitation.recordSchema()));
        value.put("realm_id", number(invitation.realmId()));
        value.put("realm_owner_uuid", string(invitation.realmOwnerUuid()));
        value.put("invited_player_uuid", string(invitation.invitedPlayerUuid()));
        value.put("owner_name_snapshot", string(invitation.ownerNameSnapshot()));
        value.put("invited_name_snapshot", string(invitation.invitedNameSnapshot()));
        value.put("created_at_epoch_ms", number(invitation.createdAtEpochMs()));
        value.put("expires_at_epoch_ms", number(invitation.expiresAtEpochMs()));
        return new StorageValue.ObjectValue(value);
    }

    private RealmInvitationDtoV1 decodeInvitation(StorageValue.ObjectValue value, String path) {
        return new RealmInvitationDtoV1(
                intValue(value, "record_schema", path),
                longValue(value, "realm_id", path),
                stringValue(value, "realm_owner_uuid", path),
                stringValue(value, "invited_player_uuid", path),
                stringValue(value, "owner_name_snapshot", path),
                stringValue(value, "invited_name_snapshot", path),
                longValue(value, "created_at_epoch_ms", path),
                longValue(value, "expires_at_epoch_ms", path));
    }

    private static StorageValue.StringValue string(String value) {
        return new StorageValue.StringValue(value);
    }

    private static StorageValue.LongValue number(long value) {
        return new StorageValue.LongValue(value);
    }

    private static StorageValue.DoubleValue decimal(double value) {
        return new StorageValue.DoubleValue(value);
    }

    private static StorageValue.ListValue strings(List<String> values) {
        return new StorageValue.ListValue(values.stream()
                .<StorageValue>map(StorageValue.StringValue::new).toList());
    }

    private static String stringValue(StorageValue.ObjectValue object, String key, String path) {
        StorageValue value = required(object, key, path);
        if (value instanceof StorageValue.StringValue string) {
            return string.value();
        }
        throw malformed(path + '.' + key, "expected string");
    }

    private static long longValue(StorageValue.ObjectValue object, String key, String path) {
        StorageValue value = required(object, key, path);
        if (value instanceof StorageValue.LongValue number) {
            return number.value();
        }
        throw malformed(path + '.' + key, "expected integer");
    }

    private static int intValue(StorageValue.ObjectValue object, String key, String path) {
        try {
            return Math.toIntExact(longValue(object, key, path));
        } catch (ArithmeticException exception) {
            throw malformed(path + '.' + key, "integer is outside 32-bit range");
        }
    }

    private static double doubleValue(StorageValue.ObjectValue object, String key, String path) {
        StorageValue value = required(object, key, path);
        if (value instanceof StorageValue.DoubleValue number) {
            return number.value();
        }
        if (value instanceof StorageValue.LongValue number) {
            return number.value();
        }
        throw malformed(path + '.' + key, "expected number");
    }

    private static float floatValue(StorageValue.ObjectValue object, String key, String path) {
        double value = doubleValue(object, key, path);
        float result = (float) value;
        if (!Float.isFinite(result)) {
            throw malformed(path + '.' + key, "number is outside float range");
        }
        return result;
    }

    private static StorageValue.ListValue listValue(StorageValue.ObjectValue object, String key, String path) {
        StorageValue value = required(object, key, path);
        if (value instanceof StorageValue.ListValue list) {
            return list;
        }
        throw malformed(path + '.' + key, "expected list");
    }

    private static List<String> stringList(StorageValue.ObjectValue object, String key, String path) {
        StorageValue.ListValue list = listValue(object, key, path);
        List<String> result = new ArrayList<>(list.values().size());
        for (int i = 0; i < list.values().size(); i++) {
            StorageValue value = list.values().get(i);
            if (!(value instanceof StorageValue.StringValue string)) {
                throw malformed(path + '.' + key + '[' + i + ']', "expected string");
            }
            result.add(string.value());
        }
        return List.copyOf(result);
    }

    private static StorageValue required(StorageValue.ObjectValue object, String key, String path) {
        StorageValue value = object.get(key);
        if (value == null) {
            throw malformed(path + '.' + key, "missing required field");
        }
        return value;
    }

    private static MalformedStorageException malformed(String path, String message) {
        return new MalformedStorageException(path, message);
    }
}
