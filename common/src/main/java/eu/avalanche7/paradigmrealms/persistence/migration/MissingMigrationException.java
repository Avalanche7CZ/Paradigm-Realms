package eu.avalanche7.paradigmrealms.persistence.migration;

public final class MissingMigrationException extends IllegalStateException {
    public MissingMigrationException(int fromVersion) {
        super("no storage migration registered from schema " + fromVersion);
    }
}
