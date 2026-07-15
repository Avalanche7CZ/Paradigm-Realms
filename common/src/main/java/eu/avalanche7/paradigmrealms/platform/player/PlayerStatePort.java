package eu.avalanche7.paradigmrealms.platform.player;

import java.util.Optional;
import java.util.UUID;

public interface PlayerStatePort {
    Optional<PlayerPosition> position(UUID player);
}
