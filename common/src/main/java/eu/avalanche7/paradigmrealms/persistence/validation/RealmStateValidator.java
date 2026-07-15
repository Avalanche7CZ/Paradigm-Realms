package eu.avalanche7.paradigmrealms.persistence.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmInvitationDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmDtoV1;
import eu.avalanche7.paradigmrealms.persistence.dto.RealmStateDtoV1;
import eu.avalanche7.paradigmrealms.ownership.RealmOwnershipTransfer;

public final class RealmStateValidator {
    private final RealmDtoMapper mapper;

    public RealmStateValidator(RealmDtoMapper mapper) {
        this.mapper = mapper;
    }

    public ValidatedRealmState validate(RealmStateDtoV1 state) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<Realm> realms = new ArrayList<>();
        List<RealmInvitation> invitations = new ArrayList<>();
        List<RealmOwnershipTransfer> transfers = new ArrayList<>();
        if (state.schemaVersion() != SchemaVersion.V1.value()
                && state.schemaVersion() != SchemaVersion.CURRENT.value()) {
            String code = state.schemaVersion() > SchemaVersion.CURRENT.value()
                    ? "FUTURE_SCHEMA" : "UNMIGRATED_SCHEMA";
            issues.add(ValidationIssue.error(code, "root.schema_version",
                    "expected schema " + SchemaVersion.CURRENT.value() + " or an upgradeable schema 1 but found "
                            + state.schemaVersion()));
        }
        if (state.revision() < 0) {
            issues.add(ValidationIssue.error("INVALID_REVISION", "root.revision",
                    "revision cannot be negative"));
        }
        if (state.nextRealmId() < 1 || state.nextRealmId() > RealmAllocator.MAX_REALM_ID + 1) {
            issues.add(ValidationIssue.error("INVALID_NEXT_ID", "root.next_realm_id",
                    "next realm ID is outside the supported range"));
        }

        Set<Long> rawIds = new HashSet<>();
        Map<UUID, Long> owners = new HashMap<>();
        long maximumId = 0;
        for (int i = 0; i < state.realms().size(); i++) {
            RealmDtoV1 dto = state.realms().get(i);
            String path = "root.realms[" + i + ']';
            if (!rawIds.add(dto.id())) {
                issues.add(ValidationIssue.error("DUPLICATE_REALM_ID", path + ".id",
                        "duplicate realm ID " + dto.id()));
            }
            ConversionResult<Realm> converted = mapper.toDomain(dto, path);
            issues.addAll(converted.issues());
            converted.value().ifPresent(realm -> {
                if (realm.state() == eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ACTIVE) {
                    Long previous = owners.putIfAbsent(realm.owner().uuid(), realm.id().value());
                    if (previous != null) {
                        issues.add(ValidationIssue.error("DUPLICATE_ACTIVE_OWNER", path + ".owner_uuid",
                                "owner already has active realm " + previous));
                    }
                }
                realms.add(realm);
            });
            maximumId = Math.max(maximumId, dto.id());
        }
        if (maximumId >= state.nextRealmId()) {
            issues.add(ValidationIssue.error("NEXT_ID_NOT_MONOTONIC", "root.next_realm_id",
                    "next realm ID must be greater than every persisted realm ID"));
        }

        Map<Long, Realm> realmsById = new HashMap<>();
        realms.forEach(realm -> realmsById.put(realm.id().value(), realm));
        Set<String> invitationKeys = new HashSet<>();
        for (int i = 0; i < state.invitations().size(); i++) {
            RealmInvitationDtoV1 dto = state.invitations().get(i);
            String path = "root.invitations[" + i + ']';
            try {
                if (dto.recordSchema() != SchemaVersion.V1.value()) {
                    throw new IllegalArgumentException("unsupported invitation record schema " + dto.recordSchema());
                }
                UUID owner = UUID.fromString(dto.realmOwnerUuid());
                UUID invited = UUID.fromString(dto.invitedPlayerUuid());
                Realm realm = realmsById.get(dto.realmId());
                if (realm == null) {
                    throw new IllegalArgumentException("invitation references an unknown realm");
                }
                if (!realm.owner().uuid().equals(owner)) {
                    throw new IllegalArgumentException("invitation owner does not match realm owner");
                }
                if (realm.members().contains(invited)) {
                    throw new IllegalArgumentException("existing member must not have a pending invitation");
                }
                String key = dto.realmId() + ":" + invited;
                if (!invitationKeys.add(key)) {
                    issues.add(ValidationIssue.error(
                            "DUPLICATE_INVITATION", path, "duplicate realm/player invitation"));
                    continue;
                }
                invitations.add(new RealmInvitation(
                        new RealmId(dto.realmId()), owner, invited,
                        dto.ownerNameSnapshot(), dto.invitedNameSnapshot(),
                        new CreationTimestamp(dto.createdAtEpochMs()),
                        new CreationTimestamp(dto.expiresAtEpochMs()),
                        new SchemaVersion(dto.recordSchema())));
            } catch (IllegalArgumentException exception) {
                issues.add(ValidationIssue.error("MALFORMED_INVITATION", path, exception.getMessage()));
            }
        }
        Set<UUID> transferActors = new HashSet<>();
        Set<Long> transferRealms = new HashSet<>();
        for (int i = 0; i < state.ownershipTransfers().size(); i++) {
            var dto = state.ownershipTransfers().get(i);
            String path = "root.ownership_transfers[" + i + ']';
            try {
                UUID operation = UUID.fromString(dto.operationUuid());
                UUID owner = UUID.fromString(dto.currentOwnerUuid());
                UUID target = UUID.fromString(dto.targetUuid());
                Realm realm = realmsById.get(dto.realmId());
                if (realm == null || realm.state() != eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ACTIVE) {
                    throw new IllegalArgumentException("transfer requires an active realm");
                }
                if (!realm.owner().uuid().equals(owner)) {
                    throw new IllegalArgumentException("transfer owner does not match realm owner");
                }
                if (!transferRealms.add(dto.realmId()) || !transferActors.add(owner) || !transferActors.add(target)) {
                    throw new IllegalArgumentException("player or realm has multiple ownership transfers");
                }
                transfers.add(new RealmOwnershipTransfer(
                        operation, new RealmId(dto.realmId()), owner, target,
                        dto.currentOwnerName(), dto.targetName(),
                        new CreationTimestamp(dto.createdAtEpochMs()),
                        new CreationTimestamp(dto.expiresAtEpochMs())));
            } catch (IllegalArgumentException exception) {
                issues.add(ValidationIssue.error("MALFORMED_OWNERSHIP_TRANSFER", path, exception.getMessage()));
            }
        }
        return new ValidatedRealmState(realms, invitations, transfers, new ValidationReport(issues));
    }
}
