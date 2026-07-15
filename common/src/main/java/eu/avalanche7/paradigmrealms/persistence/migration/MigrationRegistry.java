package eu.avalanche7.paradigmrealms.persistence.migration;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.persistence.data.StorageValue;

public final class MigrationRegistry {
    private final Map<Integer, StorageMigration> migrations = new HashMap<>();

    public void register(StorageMigration migration) {
        Objects.requireNonNull(migration, "migration");
        if (migration.toVersion() != migration.fromVersion() + 1) {
            throw new IllegalArgumentException("migrations must advance exactly one schema version");
        }
        if (migrations.putIfAbsent(migration.fromVersion(), migration) != null) {
            throw new IllegalArgumentException("duplicate migration from schema " + migration.fromVersion());
        }
    }

    public StorageValue.ObjectValue migrate(StorageValue.ObjectValue source, int sourceVersion, int targetVersion) {
        Objects.requireNonNull(source, "source");
        if (sourceVersion > targetVersion) {
            throw new UnsupportedFutureSchemaException(sourceVersion, targetVersion);
        }
        StorageValue.ObjectValue current = source;
        int version = sourceVersion;
        while (version < targetVersion) {
            StorageMigration migration = migrations.get(version);
            if (migration == null) {
                throw new MissingMigrationException(version);
            }
            current = Objects.requireNonNull(migration.migrate(current), "migration result");
            version = migration.toVersion();
        }
        return current;
    }
}
