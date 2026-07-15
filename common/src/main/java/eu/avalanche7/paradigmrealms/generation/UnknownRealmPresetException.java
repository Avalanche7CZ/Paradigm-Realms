package eu.avalanche7.paradigmrealms.generation;

import eu.avalanche7.paradigmrealms.domain.RealmPresetId;

public final class UnknownRealmPresetException extends IllegalArgumentException {
    public UnknownRealmPresetException(RealmPresetId preset) {
        super("unknown realm preset: " + preset);
    }
}
