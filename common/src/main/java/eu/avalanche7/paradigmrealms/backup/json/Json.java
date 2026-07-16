package eu.avalanche7.paradigmrealms.backup.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {}

    public static Object parse(String source) {
        Parser parser = new Parser(source);
        Object value = parser.value();
        parser.space();
        if (!parser.end()) {
            throw parser.error("trailing data");
        }
        return value;
    }

    public static String write(Object value) {
        StringBuilder output = new StringBuilder();
        append(output, value, 0);
        return output.append('\n').toString();
    }

    @SuppressWarnings("unchecked")
    private static void append(StringBuilder output, Object value, int indent) {
        if (value == null) {
            output.append("null");
            return;
        }
        if (value instanceof String string) {
            string(output, string);
            return;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Float) {
            output.append(value);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            output.append('{');
            if (!map.isEmpty()) {
                output.append('\n');
            }
            int index = 0;
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
                output.append("  ".repeat(indent + 1));
                string(output, entry.getKey());
                output.append(": ");
                append(output, entry.getValue(), indent + 1);
                if (++index < map.size()) {
                    output.append(',');
                }
                output.append('\n');
            }
            if (!map.isEmpty()) {
                output.append("  ".repeat(indent));
            }
            output.append('}');
            return;
        }
        if (value instanceof List<?> list) {
            output.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    output.append(", ");
                }
                append(output, list.get(i), indent);
            }
            output.append(']');
            return;
        }
        throw new IllegalArgumentException("unsupported JSON value " + value.getClass().getName());
    }

    private static void string(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (c < 0x20) {
                        output.append(String.format("\\u%04x", (int) c));
                    } else {
                        output.append(c);
                    }
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = java.util.Objects.requireNonNull(source, "source");
        }

        private boolean end() {
            return index >= source.length();
        }

        private void space() {
            while (!end() && Character.isWhitespace(source.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("malformed JSON at character " + index + ": " + message);
        }

        private Object value() {
            space();
            if (end()) {
                throw error("expected value");
            }
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Object literal(String text, Object value) {
            if (!source.startsWith(text, index)) {
                throw error("expected " + text);
            }
            index += text.length();
            return value;
        }

        private Map<String, Object> object() {
            index++;
            space();
            Map<String, Object> values = new LinkedHashMap<>();
            if (!end() && source.charAt(index) == '}') {
                index++;
                return values;
            }
            while (true) {
                space();
                if (end() || source.charAt(index) != '"') {
                    throw error("expected object key");
                }
                String key = string();
                space();
                if (end() || source.charAt(index++) != ':') {
                    throw error("expected colon");
                }
                if (values.putIfAbsent(key, value()) != null) {
                    throw error("duplicate object key " + key);
                }
                space();
                if (end()) {
                    throw error("unterminated object");
                }
                char separator = source.charAt(index++);
                if (separator == '}') {
                    return values;
                }
                if (separator != ',') {
                    throw error("expected comma");
                }
            }
        }

        private List<Object> array() {
            index++;
            space();
            List<Object> values = new ArrayList<>();
            if (!end() && source.charAt(index) == ']') {
                index++;
                return values;
            }
            while (true) {
                values.add(value());
                space();
                if (end()) {
                    throw error("unterminated array");
                }
                char separator = source.charAt(index++);
                if (separator == ']') {
                    return values;
                }
                if (separator != ',') {
                    throw error("expected comma");
                }
            }
        }

        private String string() {
            index++;
            StringBuilder value = new StringBuilder();
            while (!end()) {
                char c = source.charAt(index++);
                if (c == '"') {
                    return value.toString();
                }
                if (c == '\\') {
                    if (end()) {
                        throw error("unterminated escape");
                    }
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> value.append(escaped);
                        case 'b' -> value.append('\b');
                        case 'f' -> value.append('\f');
                        case 'n' -> value.append('\n');
                        case 'r' -> value.append('\r');
                        case 't' -> value.append('\t');
                        case 'u' -> {
                            if (index + 4 > source.length()) {
                                throw error("short Unicode escape");
                            }
                            try {
                                value.append((char) Integer.parseInt(
                                        source.substring(index, index + 4),
                                        16));
                            } catch (NumberFormatException exception) {
                                throw error("bad Unicode escape");
                            }
                            index += 4;
                        }
                        default -> throw error("bad escape");
                    }
                } else {
                    if (c < 0x20) {
                        throw error("control character in string");
                    }
                    value.append(c);
                }
            }
            throw error("unterminated string");
        }
        private Number number() {
            int start = index;
            if (!end() && source.charAt(index) == '-') {
                index++;
            }
            while (!end() && Character.isDigit(source.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (!end() && source.charAt(index) == '.') {
                decimal = true;
                index++;
                while (!end() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            if (!end() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!end() && (source.charAt(index) == '+' || source.charAt(index) == '-')) {
                    index++;
                }
                while (!end() && Character.isDigit(source.charAt(index))) {
                    index++;
                }
            }
            if (start == index) {
                throw error("expected value");
            }
            try {
                return decimal
                        ? Double.parseDouble(source.substring(start, index))
                        : Long.parseLong(source.substring(start, index));
            } catch (NumberFormatException exception) {
                throw error("invalid number");
            }
        }
    }
}
