package eu.avalanche7.paradigmrealms.wilds;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WildsDurationParser {
    private static final Pattern VALUE = Pattern.compile("([1-9][0-9]*)(s|m|h|d)", Pattern.CASE_INSENSITIVE);

    public Duration parse(String text) {
        Matcher matcher = VALUE.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) throw new IllegalArgumentException("duration must use s, m, h, or d");
        long amount = Long.parseLong(matcher.group(1));
        Duration result = switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new IllegalStateException();
        };
        if (result.compareTo(Duration.ofDays(3650)) > 0) throw new IllegalArgumentException("duration exceeds 10 years");
        return result;
    }
}
