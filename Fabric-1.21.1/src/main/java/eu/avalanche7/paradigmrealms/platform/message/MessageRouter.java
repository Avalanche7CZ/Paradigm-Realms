package eu.avalanche7.paradigmrealms.platform.message;

import java.util.Map;
import java.util.Objects;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.command.FabricCommandPlatform;

public final class MessageRouter implements CommandMessenger, CommandMessageService {
    private final CommandMessenger fallback;
    private volatile CommandMessenger delegate;

    public MessageRouter() {
        this((source, template, values, nativeFallback) ->
                source.sendFeedback(() -> Text.literal(nativeFallback), false));
    }

    public MessageRouter(CommandMessenger fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.delegate = fallback;
    }

    public void install(CommandMessenger integration) {
        delegate = Objects.requireNonNull(integration, "integration");
    }

    public void reset() {
        delegate = fallback;
    }

    @Override
    public void send(
            ServerCommandSource source, String template, Map<String, String> values, String nativeFallback) {
        delegate.send(source, template, Map.copyOf(values), nativeFallback);
    }

    @Override
    public void send(
            CommandSource source, String template, Map<String, String> values, String nativeFallback) {
        if (!(source instanceof FabricCommandPlatform.Source fabricSource)) {
            throw new IllegalArgumentException("command source belongs to another platform");
        }
        send(fabricSource.original(), template, values, nativeFallback);
    }
}
