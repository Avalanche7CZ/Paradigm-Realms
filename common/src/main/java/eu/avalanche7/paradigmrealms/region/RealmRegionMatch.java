package eu.avalanche7.paradigmrealms.region;

import java.util.Objects;
import java.util.Optional;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;

public record RealmRegionMatch(RealmRegionKind kind, Optional<Realm> realm) {
    public RealmRegionMatch {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(realm, "realm");
        if (kind == RealmRegionKind.UNALLOCATED_REALMS_SPACE && realm.isPresent()) {
            throw new IllegalArgumentException("unallocated space must not resolve a realm");
        }
        if (kind != RealmRegionKind.UNALLOCATED_REALMS_SPACE && realm.isEmpty()) {
            throw new IllegalArgumentException("allocated region must resolve a realm");
        }
    }
}
