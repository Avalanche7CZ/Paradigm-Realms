package eu.avalanche7.paradigmrealms.platform.message;

import java.util.Map;

import net.minecraft.server.command.ServerCommandSource;

@FunctionalInterface
public interface CommandMessenger {
    void send(ServerCommandSource source, String template, Map<String, String> values, String nativeFallback);
}
