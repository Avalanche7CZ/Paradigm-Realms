package eu.avalanche7.paradigmrealms.platform.command;

public interface CommandPlatform {
    CommandBuilder literal(String name);
    CommandBuilder argument(String name, CommandArgument type);
    void register(CommandBuilder root);
}
