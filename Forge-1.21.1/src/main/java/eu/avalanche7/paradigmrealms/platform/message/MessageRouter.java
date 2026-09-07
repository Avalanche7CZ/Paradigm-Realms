package eu.avalanche7.paradigmrealms.platform.message;

import java.util.Map;
import java.util.Objects;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import eu.avalanche7.paradigmrealms.platform.command.CommandSource;
import eu.avalanche7.paradigmrealms.platform.command.ForgeCommandPlatform;
import eu.avalanche7.paradigmrealms.message.LanguageCatalog;

public final class MessageRouter implements CommandMessenger, CommandMessageService {
    private final CommandMessenger fallback;
    private final LanguageCatalog language;
    private volatile CommandMessenger delegate;

    public MessageRouter() {
        this(null, (source, template, values, nativeFallback) ->
                source.sendFeedback(() -> Text.literal(nativeFallback), false));
    }

    public MessageRouter(LanguageCatalog language) {
        this(language, (source, template, values, nativeFallback) ->
                source.sendFeedback(() -> Text.literal(nativeFallback), false));
    }

    public MessageRouter(CommandMessenger fallback) {
        this(null, fallback);
    }

    public MessageRouter(LanguageCatalog language, CommandMessenger fallback) {
        this.language = language;
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
        if (!(source instanceof ForgeCommandPlatform.Source forgeSource)) {
            throw new IllegalArgumentException("command source belongs to another platform");
        }
        send(forgeSource.original(), template, values, nativeFallback);
    }

    @Override
    public void sendLocalized(CommandSource source, String key, String template,
            Map<String, String> values, String nativeFallback) {
        if (!(source instanceof ForgeCommandPlatform.Source forgeSource)) {
            throw new IllegalArgumentException("command source belongs to another platform");
        }
        sendLocalized(forgeSource.original(), key, template, values, nativeFallback);
    }

    @Override
    public void sendLocalized(ServerCommandSource source, String key, String template,
            Map<String, String> values, String nativeFallback) {
        String translated = translate(key, values, nativeFallback);
        delegate.send(source, translated, Map.of(), translated);
    }

    @Override
    public String translate(String key, Map<String, String> values, String fallback) {
        return language == null ? CommandMessenger.super.translate(key, values, fallback)
                : language.translate(key, values, fallback);
    }
}
