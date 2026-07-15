package eu.avalanche7.paradigmrealms.platform.command;

public record CommandArgument(Kind kind, long minimum, long maximum) {
    public enum Kind { WORD, STRING, GREEDY_STRING, INTEGER, LONG, PLAYER_PROFILES }

    public CommandArgument {
        java.util.Objects.requireNonNull(kind, "kind");
        if (minimum > maximum) throw new IllegalArgumentException("minimum cannot exceed maximum");
    }

    public static CommandArgument word() { return new CommandArgument(Kind.WORD, 0, 0); }
    public static CommandArgument string() { return new CommandArgument(Kind.STRING, 0, 0); }
    public static CommandArgument greedyString() { return new CommandArgument(Kind.GREEDY_STRING, 0, 0); }
    public static CommandArgument integer(int minimum, int maximum) {
        return new CommandArgument(Kind.INTEGER, minimum, maximum);
    }
    public static CommandArgument longArgument(long minimum, long maximum) {
        return new CommandArgument(Kind.LONG, minimum, maximum);
    }
    public static CommandArgument playerProfiles() {
        return new CommandArgument(Kind.PLAYER_PROFILES, 0, 0);
    }
}
