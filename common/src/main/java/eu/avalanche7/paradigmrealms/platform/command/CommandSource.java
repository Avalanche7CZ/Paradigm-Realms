package eu.avalanche7.paradigmrealms.platform.command;

import java.util.Optional;

import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;

public interface CommandSource {
    Optional<PlayerReference> player();
    String name();
    boolean hasPermissionLevel(int level);
    void sendFeedback(CommandText message, boolean toOperators);
    void sendError(CommandText message);

    default void executeOnServerThread(Runnable task) {
        task.run();
    }

    default void sendFeedback(String message) {
        sendFeedback(CommandText.literal(message), false);
    }

    default void sendError(String message) {
        sendError(CommandText.literal(message));
    }
}
