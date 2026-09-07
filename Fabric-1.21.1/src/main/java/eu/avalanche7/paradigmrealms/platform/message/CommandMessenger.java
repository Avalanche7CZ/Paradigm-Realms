package eu.avalanche7.paradigmrealms.platform.message;

import java.util.Map;

import net.minecraft.server.command.ServerCommandSource;

@FunctionalInterface
public interface CommandMessenger {
    void send(ServerCommandSource source, String template, Map<String, String> values, String nativeFallback);

    default void sendLocalized(ServerCommandSource source, String key, String template,
            Map<String, String> values, String nativeFallback) {
        send(source, template, values, nativeFallback);
    }

    default String translate(String key, Map<String, String> values, String fallback) {
        String result = fallback;
        for (Map.Entry<String, String> value : values.entrySet()) {
            result = result.replace("{" + value.getKey() + "}", value.getValue());
        }
        return result;
    }

    default String translate(String key, Map<String, String> values) {
        return eu.avalanche7.paradigmrealms.message.PlayerMessages.text(key, values);
    }
}
