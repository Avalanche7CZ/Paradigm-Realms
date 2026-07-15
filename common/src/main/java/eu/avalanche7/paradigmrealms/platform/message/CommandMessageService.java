package eu.avalanche7.paradigmrealms.platform.message;

import java.util.Map;

import eu.avalanche7.paradigmrealms.platform.command.CommandSource;

@FunctionalInterface
public interface CommandMessageService {
    void send(CommandSource source, String template, Map<String, String> values, String nativeFallback);
}
