package eu.avalanche7.paradigmrealms.persistence.codec;

public final class MalformedStorageException extends IllegalArgumentException {
    private final String path;

    public MalformedStorageException(String path, String message) {
        super(path + ": " + message);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
