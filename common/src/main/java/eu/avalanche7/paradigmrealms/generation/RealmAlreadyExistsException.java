package eu.avalanche7.paradigmrealms.generation;

import java.util.UUID;

public final class RealmAlreadyExistsException extends IllegalStateException {
    public RealmAlreadyExistsException(UUID owner) {
        super("owner already has a realm: " + owner);
    }
}
