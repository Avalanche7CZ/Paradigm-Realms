package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.List;
import java.util.Objects;

public record RealmStateDtoV1(
        int schemaVersion,
        long revision,
        long nextRealmId,
        List<RealmDtoV1> realms,
        List<RealmInvitationDtoV1> invitations,
        List<RealmOwnershipTransferDtoV1> ownershipTransfers) {
    public RealmStateDtoV1 {
        Objects.requireNonNull(realms, "realms");
        Objects.requireNonNull(invitations, "invitations");
        Objects.requireNonNull(ownershipTransfers, "ownershipTransfers");
        realms = List.copyOf(realms);
        invitations = List.copyOf(invitations);
        ownershipTransfers = List.copyOf(ownershipTransfers);
    }

    public RealmStateDtoV1(int schemaVersion, long revision, long nextRealmId, List<RealmDtoV1> realms) {
        this(schemaVersion, revision, nextRealmId, realms, List.of(), List.of());
    }

    public RealmStateDtoV1(
            int schemaVersion, long revision, long nextRealmId, List<RealmDtoV1> realms,
            List<RealmInvitationDtoV1> invitations) {
        this(schemaVersion, revision, nextRealmId, realms, invitations, List.of());
    }

    public static RealmStateDtoV1 empty() {
        return new RealmStateDtoV1(2, 0, 1, List.of(), List.of(), List.of());
    }
}
