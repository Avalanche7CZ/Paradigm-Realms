package eu.avalanche7.paradigmrealms.platform.command;

import java.util.List;
import java.util.function.Predicate;

public interface CommandBuilder {
    CommandBuilder requires(Predicate<CommandSource> requirement);
    CommandBuilder executes(CommandExecutor executor);
    CommandBuilder suggests(SuggestionProvider provider);
    CommandBuilder then(CommandBuilder child);

    @FunctionalInterface
    interface CommandExecutor {
        int execute(CommandContext context) throws Exception;
    }

    @FunctionalInterface
    interface SuggestionProvider {
        List<String> suggestions(CommandContext context, String input);
    }
}
