package eu.avalanche7.paradigmrealms.backup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record BackupActor(Type type, Optional<UUID> uuid, String nameSnapshot) {
    public enum Type { ADMIN, PLAYER, SYSTEM }

    public BackupActor {
        Objects.requireNonNull(type, "type");
        uuid = Objects.requireNonNull(uuid, "uuid");
        nameSnapshot = Objects.requireNonNull(nameSnapshot, "nameSnapshot").strip();
        if (nameSnapshot.length() > 64) throw new IllegalArgumentException("actor name is too long");
        if (type != Type.SYSTEM && uuid.isEmpty()) throw new IllegalArgumentException("actor UUID is required");
    }

    public static BackupActor system() { return new BackupActor(Type.SYSTEM, Optional.empty(), "Server"); }
}
