package eu.avalanche7.paradigmrealms.platform.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CommandText(List<Part> parts) {
    public CommandText {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
        if (parts.isEmpty()) throw new IllegalArgumentException("command text cannot be empty");
    }

    public static CommandText literal(String text) {
        return new CommandText(List.of(Part.literal(text)));
    }

    public CommandText append(Part part) {
        ArrayList<Part> copy = new ArrayList<>(parts);
        copy.add(Objects.requireNonNull(part, "part"));
        return new CommandText(copy);
    }

    public enum ClickAction { RUN_COMMAND, SUGGEST_COMMAND }

    public record Click(ClickAction action, String value) {
        public Click {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(value, "value");
        }
    }

    public record Part(String text, Optional<Click> click, Optional<String> hover) {
        public Part {
            Objects.requireNonNull(text, "text");
            click = Objects.requireNonNull(click, "click");
            hover = Objects.requireNonNull(hover, "hover");
        }

        public static Part literal(String text) {
            return new Part(text, Optional.empty(), Optional.empty());
        }

        public static Part interactive(String text, ClickAction action, String command, String hover) {
            return new Part(text, Optional.of(new Click(action, command)), Optional.ofNullable(hover));
        }
    }
}
