package eu.avalanche7.paradigmrealms.persistence;

import eu.avalanche7.paradigmrealms.domain.SchemaVersion;

public record SchemaMetadata(SchemaVersion schemaVersion, long revision, long nextRealmId, boolean writable) {
    public SchemaMetadata {
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        if (nextRealmId < 1) {
            throw new IllegalArgumentException("next realm ID must be positive");
        }
    }
}
