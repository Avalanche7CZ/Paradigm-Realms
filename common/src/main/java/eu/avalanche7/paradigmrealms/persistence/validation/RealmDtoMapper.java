package eu.avalanche7.paradigmrealms.persistence.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocation;
import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.RealmOwner;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.domain.realm.RealmBan;
import eu.avalanche7.paradigmrealms.domain.realm.RealmFailure;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperation;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperationKind;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperationStage;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.domain.realm.RealmOperation;
import eu.avalanche7.paradigmrealms.domain.realm.RealmSettings;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmBanDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmFailureDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmLifecycleOperationDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmOperationDtoV1;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import eu.avalanche7.paradigmrealms.region.CellCoordinate;
import eu.avalanche7.paradigmrealms.region.ChunkBounds;

public final class RealmDtoMapper {
    private final RealmAllocator allocator;

    public RealmDtoMapper(RealmAllocator allocator) {
        this.allocator = allocator;
    }

    public ConversionResult<Realm> toDomain(RealmDtoV1 dto, String path) {
        List<ValidationIssue> issues = new ArrayList<>();
        try {
            if (dto.recordSchema() != SchemaVersion.V1.value()
                    && dto.recordSchema() != SchemaVersion.CURRENT.value()) {
                return ConversionResult.failure(ValidationIssue.error(
                        "UNSUPPORTED_RECORD_SCHEMA", path + ".record_schema",
                        "unsupported record schema " + dto.recordSchema()));
            }
            RealmId id = new RealmId(dto.id());
            UUID ownerUuid = parseUuid(dto.ownerUuid(), path + ".owner_uuid", issues);
            Set<UUID> members = parseUniqueUuids(dto.memberUuids(), path + ".members", issues);
            Set<UUID> visitors = parseUniqueUuids(dto.visitorUuids(), path + ".visitors", issues);
            Set<UUID> managers = parseUniqueUuids(dto.managerUuids(), path + ".managers", issues);
            Map<UUID, RealmBan> bans = parseBans(dto.bans(), path + ".bans", issues);
            if (!issues.isEmpty()) {
                return new ConversionResult<>(Optional.empty(), issues);
            }

            RealmAllocation storedAllocation = new RealmAllocation(
                    new CellCoordinate(dto.cellX(), dto.cellZ()),
                    new ChunkBounds(dto.cellMinChunkX(), dto.cellMinChunkZ(),
                            dto.cellMaxChunkX(), dto.cellMaxChunkZ()),
                    new ChunkBounds(dto.buildMinChunkX(), dto.buildMinChunkZ(),
                            dto.buildMaxChunkX(), dto.buildMaxChunkZ()));
            RealmAllocation expected = allocator.preview(id);
            if (!storedAllocation.equals(expected)) {
                return ConversionResult.failure(ValidationIssue.error(
                        "ALLOCATION_MISMATCH", path + ".allocation",
                        "persisted allocation does not match realm ID " + id));
            }

            RealmLifecycleState state = RealmLifecycleState.valueOf(dto.state());
            Optional<RealmFailure> failure = dto.failure().map(this::toFailure);
            Realm realm = new Realm(
                    id,
                    new RealmOwner(ownerUuid),
                    state,
                    DimensionId.parse(dto.dimension()),
                    storedAllocation,
                    new BlockPosition(dto.spawnX(), dto.spawnY(), dto.spawnZ(),
                            dto.spawnYaw(), dto.spawnPitch()),
                    new RealmPresetId(dto.presetId()),
                    members,
                    visitors,
                    RealmAccessPolicy.valueOf(dto.accessPolicy()),
                    new CreationTimestamp(dto.createdAtEpochMs()),
                    SchemaVersion.CURRENT,
                    dto.operation().map(this::toOperation),
                    failure,
                    dto.displayName(), dto.description(), dto.listed(), managers, bans,
                    new RealmSettings(dto.pvp(), dto.explosions(), dto.mobGriefing(),
                            dto.visitorInteraction(), dto.visitorContainers()),
                    dto.replacementOfRealmId().map(RealmId::new), dto.replacedByRealmId().map(RealmId::new),
                    dto.archivedAtEpochMs().map(CreationTimestamp::new),
                    dto.lifecycleOperation().map(this::toLifecycleOperation));
            return ConversionResult.success(realm);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            issues.add(ValidationIssue.error("MALFORMED_REALM", path, exception.getMessage()));
            return new ConversionResult<>(Optional.empty(), issues);
        }
    }

    public RealmDtoV1 toDto(Realm realm) {
        RealmAllocation allocation = realm.allocation();
        return new RealmDtoV1(
                SchemaVersion.CURRENT.value(), realm.id().value(), realm.owner().uuid().toString(),
                realm.state().name(), realm.dimension().toString(),
                allocation.cell().x(), allocation.cell().z(),
                allocation.cellBounds().minX(), allocation.cellBounds().minZ(),
                allocation.cellBounds().maxX(), allocation.cellBounds().maxZ(),
                allocation.buildableBounds().minX(), allocation.buildableBounds().minZ(),
                allocation.buildableBounds().maxX(), allocation.buildableBounds().maxZ(),
                realm.spawn().x(), realm.spawn().y(), realm.spawn().z(),
                realm.spawn().yaw(), realm.spawn().pitch(), realm.preset().value(),
                sortedUuids(realm.members()), sortedUuids(realm.invitedVisitors()),
                realm.accessPolicy().name(), realm.createdAt().epochMillis(),
                realm.operation().map(this::toOperationDto),
                realm.failure().map(this::toFailureDto), realm.displayName(), realm.description(), realm.listed(),
                sortedUuids(realm.managers()), realm.bans().values().stream()
                        .sorted(java.util.Comparator.comparing(value -> value.playerUuid().toString()))
                        .map(this::toBanDto).toList(), realm.settings().pvp(), realm.settings().explosions(),
                realm.settings().mobGriefing(), realm.settings().visitorInteraction(),
                realm.settings().visitorContainers(), realm.replacementOf().map(RealmId::value),
                realm.replacedBy().map(RealmId::value), realm.archivedAt().map(CreationTimestamp::epochMillis),
                realm.lifecycleOperation().map(this::toLifecycleOperationDto));
    }

    private RealmOperation toOperation(RealmOperationDtoV1 dto) {
        return new RealmOperation(UUID.fromString(dto.operationUuid()), dto.presetRevision(), dto.attempt(),
                new CreationTimestamp(dto.updatedAtEpochMs()));
    }

    private RealmOperationDtoV1 toOperationDto(RealmOperation operation) {
        return new RealmOperationDtoV1(operation.operationId().toString(), operation.presetRevision(),
                operation.attempt(), operation.updatedAt().epochMillis());
    }

    private RealmFailure toFailure(RealmFailureDtoV1 dto) {
        return new RealmFailure(dto.code(), dto.detail(), RealmLifecycleState.valueOf(dto.failedPhase()),
                UUID.fromString(dto.operationUuid()), dto.attempt(),
                new CreationTimestamp(dto.updatedAtEpochMs()));
    }

    private RealmFailureDtoV1 toFailureDto(RealmFailure failure) {
        return new RealmFailureDtoV1(failure.code(), failure.detail(), failure.failedPhase().name(),
                failure.operationId().toString(), failure.attempt(), failure.updatedAt().epochMillis());
    }

    private RealmLifecycleOperation toLifecycleOperation(RealmLifecycleOperationDtoV1 dto) {
        return new RealmLifecycleOperation(UUID.fromString(dto.operationUuid()),
                RealmLifecycleOperationKind.valueOf(dto.kind()), RealmLifecycleOperationStage.valueOf(dto.stage()),
                dto.requestedPresetId().map(RealmPresetId::new), dto.targetRealmId().map(RealmId::new),
                new CreationTimestamp(dto.requestedAtEpochMs()), new CreationTimestamp(dto.updatedAtEpochMs()),
                dto.failureCode(), dto.failureDetail());
    }

    private RealmLifecycleOperationDtoV1 toLifecycleOperationDto(RealmLifecycleOperation operation) {
        return new RealmLifecycleOperationDtoV1(operation.operationId().toString(), operation.kind().name(),
                operation.stage().name(), operation.requestedPreset().map(RealmPresetId::value),
                operation.targetRealmId().map(RealmId::value), operation.requestedAt().epochMillis(),
                operation.updatedAt().epochMillis(), operation.failureCode(), operation.failureDetail());
    }

    private static Map<UUID, RealmBan> parseBans(
            List<RealmBanDtoV1> values, String path, List<ValidationIssue> issues) {
        Map<UUID, RealmBan> result = new HashMap<>();
        for (int i = 0; i < values.size(); i++) {
            RealmBanDtoV1 value = values.get(i);
            try {
                UUID player = UUID.fromString(value.playerUuid());
                RealmBan ban = new RealmBan(player, value.playerNameSnapshot(), UUID.fromString(value.actorUuid()),
                        value.reason(), new CreationTimestamp(value.createdAtEpochMs()));
                if (result.putIfAbsent(player, ban) != null) {
                    issues.add(ValidationIssue.error("DUPLICATE_BAN", path + '[' + i + ']', "duplicate banned player"));
                }
            } catch (IllegalArgumentException exception) {
                issues.add(ValidationIssue.error("MALFORMED_BAN", path + '[' + i + ']', exception.getMessage()));
            }
        }
        return Map.copyOf(result);
    }

    private RealmBanDtoV1 toBanDto(RealmBan ban) {
        return new RealmBanDtoV1(ban.playerUuid().toString(), ban.playerNameSnapshot(), ban.actorUuid().toString(),
                ban.reason(), ban.createdAt().epochMillis());
    }

    private static UUID parseUuid(String value, String path, List<ValidationIssue> issues) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            issues.add(ValidationIssue.error("MALFORMED_UUID", path, "invalid UUID"));
            return new UUID(0, 0);
        }
    }

    private static Set<UUID> parseUniqueUuids(
            List<String> values, String path, List<ValidationIssue> issues) {
        Set<UUID> result = new HashSet<>();
        for (int i = 0; i < values.size(); i++) {
            UUID uuid;
            try {
                uuid = UUID.fromString(values.get(i));
            } catch (IllegalArgumentException exception) {
                issues.add(ValidationIssue.error("MALFORMED_UUID", path + '[' + i + ']', "invalid UUID"));
                continue;
            }
            if (!result.add(uuid)) {
                issues.add(ValidationIssue.error("DUPLICATE_UUID", path + '[' + i + ']',
                        "duplicate UUID in list"));
            }
        }
        return Set.copyOf(result);
    }

    private static List<String> sortedUuids(Set<UUID> values) {
        return values.stream().map(UUID::toString).sorted().toList();
    }
}
