package eu.avalanche7.paradigmrealms.persistence.dto;

public record RealmOwnershipTransferDtoV1(
        String operationUuid,
        long realmId,
        String currentOwnerUuid,
        String targetUuid,
        String currentOwnerName,
        String targetName,
        long createdAtEpochMs,
        long expiresAtEpochMs) {}
