package eu.avalanche7.paradigmrealms.protection;

import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.access.AccessRole;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.region.RealmRegionKind;

public record ProtectionDecision(
        boolean allowed,
        ProtectionReason reason,
        Optional<RealmId> realmId,
        AccessRole effectiveRole,
        RealmRegionKind regionKind,
        boolean adminBypassUsed) {
    public ProtectionDecision {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(realmId, "realmId");
        Objects.requireNonNull(effectiveRole, "effectiveRole");
        Objects.requireNonNull(regionKind, "regionKind");
    }
}
