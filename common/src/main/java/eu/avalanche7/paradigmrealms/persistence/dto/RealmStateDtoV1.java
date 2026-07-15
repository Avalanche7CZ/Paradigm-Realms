package eu.avalanche7.paradigmrealms.persistence.dto;

import java.util.List;
import java.util.Objects;

public record RealmStateDtoV1(
        int schemaVersion,
        long revision,
        long nextRealmId,
        List<RealmDtoV1> realms,
        List<RealmInvitationDtoV1> invitations) {
    public RealmStateDtoV1 {
        Objects.requireNonNull(realms, "realms");
        Objects.requireNonNull(invitations, "invitations");
        realms = List.copyOf(realms);
        invitations = List.copyOf(invitations);
    }

    public RealmStateDtoV1(int schemaVersion, long revision, long nextRealmId, List<RealmDtoV1> realms) {
        this(schemaVersion, revision, nextRealmId, realms, List.of());
    }

    public static RealmStateDtoV1 empty() {
        return new RealmStateDtoV1(1, 0, 1, List.of(), List.of());
    }
}
