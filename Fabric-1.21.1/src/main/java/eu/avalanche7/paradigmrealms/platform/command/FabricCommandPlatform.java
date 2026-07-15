package eu.avalanche7.paradigmrealms.platform.command;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import eu.avalanche7.paradigmrealms.integration.permission.PlayerReference;
import eu.avalanche7.paradigmrealms.platform.player.PlayerIdentity;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

public final class FabricCommandPlatform implements CommandPlatform {
    private final CommandDispatcher<ServerCommandSource> dispatcher;

    public FabricCommandPlatform(CommandDispatcher<ServerCommandSource> dispatcher) {
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher, "dispatcher");
    }

    @Override
    public CommandBuilder literal(String name) {
        return new Builder(LiteralArgumentBuilder.literal(name));
    }

    @Override
    public CommandBuilder argument(String name, CommandArgument type) {
        return Builder.argument(name, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void register(CommandBuilder root) {
        if (!(root instanceof Builder fabricRoot)
                || !(fabricRoot.original() instanceof LiteralArgumentBuilder<?> literal)) {
            throw new IllegalArgumentException("root must be a Fabric literal command");
        }
        dispatcher.register((LiteralArgumentBuilder<ServerCommandSource>) literal);
    }

    private static final class Builder implements CommandBuilder {
        private final ArgumentBuilder<ServerCommandSource, ?> builder;

        private Builder(ArgumentBuilder<ServerCommandSource, ?> builder) { this.builder = builder; }

        private static Builder argument(String name, CommandArgument argument) {
            RequiredArgumentBuilder<ServerCommandSource, ?> nativeBuilder = switch (argument.kind()) {
                case WORD -> RequiredArgumentBuilder.argument(name, StringArgumentType.word());
                case STRING -> RequiredArgumentBuilder.argument(name, StringArgumentType.string());
                case GREEDY_STRING -> RequiredArgumentBuilder.argument(name, StringArgumentType.greedyString());
                case INTEGER -> RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer(
                        Math.toIntExact(argument.minimum()), Math.toIntExact(argument.maximum())));
                case LONG -> RequiredArgumentBuilder.argument(name,
                        LongArgumentType.longArg(argument.minimum(), argument.maximum()));
                case PLAYER_PROFILES -> RequiredArgumentBuilder.argument(
                        name, GameProfileArgumentType.gameProfile());
            };
            return new Builder(nativeBuilder);
        }

        private ArgumentBuilder<ServerCommandSource, ?> original() { return builder; }

        @Override public CommandBuilder requires(Predicate<eu.avalanche7.paradigmrealms.platform.command.CommandSource> requirement) {
            builder.requires(source -> requirement.test(new Source(source)));
            return this;
        }

        @Override public CommandBuilder executes(CommandExecutor executor) {
            builder.executes(context -> {
                try {
                    return executor.execute(new Context(context));
                } catch (RuntimeException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("common command execution failed", exception);
                }
            });
            return this;
        }

        @Override @SuppressWarnings("unchecked")
        public CommandBuilder suggests(SuggestionProvider provider) {
            if (!(builder instanceof RequiredArgumentBuilder<?, ?> required)) {
                throw new IllegalStateException("suggestions require an argument node");
            }
            RequiredArgumentBuilder<ServerCommandSource, ?> typed =
                    (RequiredArgumentBuilder<ServerCommandSource, ?>) required;
            typed.suggests((context, suggestions) -> net.minecraft.command.CommandSource.suggestMatching(
                    provider.suggestions(new Context(context), suggestions.getRemaining())
                            .stream().distinct(), suggestions));
            return this;
        }

        @Override public CommandBuilder then(CommandBuilder child) {
            if (!(child instanceof Builder fabricChild)) {
                throw new IllegalArgumentException("command child belongs to another platform");
            }
            builder.then(fabricChild.builder);
            return this;
        }
    }

    private static final class Context implements eu.avalanche7.paradigmrealms.platform.command.CommandContext {
        private final CommandContext<ServerCommandSource> context;
        private Context(CommandContext<ServerCommandSource> context) { this.context = context; }

        @Override public eu.avalanche7.paradigmrealms.platform.command.CommandSource source() {
            return new Source(context.getSource());
        }
        @Override public String string(String name) { return StringArgumentType.getString(context, name); }
        @Override public int integer(String name) { return IntegerArgumentType.getInteger(context, name); }
        @Override public long longValue(String name) { return LongArgumentType.getLong(context, name); }
        @Override public List<PlayerIdentity> playerProfiles(String name) throws Exception {
            var profiles = GameProfileArgumentType.getProfileArgument(context, name);
            if (profiles.isEmpty()) return List.of();
            var first = profiles.iterator().next();
            return first.getId() == null ? List.of()
                    : List.of(new PlayerIdentity(first.getId(), first.getName()));
        }
    }

    public static final class Source implements eu.avalanche7.paradigmrealms.platform.command.CommandSource {
        private final ServerCommandSource source;
        public Source(ServerCommandSource source) {
            this.source = java.util.Objects.requireNonNull(source, "source");
        }
        public ServerCommandSource original() { return source; }

        @Override public Optional<PlayerReference> player() {
            var player = source.getPlayer();
            if (player == null) return Optional.empty();
            return Optional.of(new PlayerReference(player.getUuid(), player.getGameProfile().getName(),
                    source.getServer().getPermissionLevel(player.getGameProfile())));
        }
        @Override public String name() { return source.getName(); }
        @Override public boolean hasPermissionLevel(int level) { return source.hasPermissionLevel(level); }
        @Override public void executeOnServerThread(Runnable task) { source.getServer().execute(task); }
        @Override public void sendFeedback(CommandText message, boolean toOperators) {
            Text rendered = render(message);
            source.sendFeedback(() -> rendered, toOperators);
        }
        @Override public void sendError(CommandText message) { source.sendError(render(message)); }

        private static Text render(CommandText message) {
            MutableText root = Text.empty();
            for (CommandText.Part part : message.parts()) {
                MutableText rendered = Text.literal(part.text());
                Style style = Style.EMPTY;
                if (part.click().isPresent()) {
                    CommandText.Click click = part.click().orElseThrow();
                    ClickEvent.Action action = click.action() == CommandText.ClickAction.RUN_COMMAND
                            ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
                    style = style.withClickEvent(new ClickEvent(action, click.value()));
                }
                if (part.hover().isPresent()) style = style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT, Text.literal(part.hover().orElseThrow())));
                root.append(rendered.setStyle(style));
            }
            return root;
        }
    }
}
