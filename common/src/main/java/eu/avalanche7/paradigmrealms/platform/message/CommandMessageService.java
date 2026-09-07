package eu.avalanche7.paradigmrealms.platform.message;

import java.util.Map;

import eu.avalanche7.paradigmrealms.platform.command.CommandSource;

@FunctionalInterface
public interface CommandMessageService {
    void send(CommandSource source, String template, Map<String, String> values, String nativeFallback);

    default void sendLocalized(
            CommandSource source,
            String key,
            String template,
            Map<String, String> values,
            String nativeFallback) {
        send(source, template, values, nativeFallback);
    }

    default void sendLocalized(CommandSource source, String key, Map<String, String> values) {
        String message = eu.avalanche7.paradigmrealms.message.PlayerMessages.text(key, values);
        send(source, message, Map.of(), message);
    }

    default void sendErrorLocalized(CommandSource source, String key, Map<String, String> values) {
        source.sendError(eu.avalanche7.paradigmrealms.message.PlayerMessages.text(key, values));
    }
}
