package eu.avalanche7.paradigmrealms.persistence.migration;

public final class UnsupportedFutureSchemaException extends IllegalStateException {
    public UnsupportedFutureSchemaException(int found, int supported) {
        super("storage schema " + found + " is newer than supported schema " + supported);
    }
}
