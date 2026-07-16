package eu.avalanche7.paradigmrealms.backup;

public enum BackupStorageKind {
    TERRAIN("terrain"), ENTITIES("entities"), POI("poi");

    private final String directory;
    BackupStorageKind(String directory) { this.directory = directory; }
    public String directory() { return directory; }
}
