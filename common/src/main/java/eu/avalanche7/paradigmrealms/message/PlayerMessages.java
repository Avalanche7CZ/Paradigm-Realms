package eu.avalanche7.paradigmrealms.message;

import java.util.Map;
import java.util.Objects;

/** Process-wide server message access for common and mapped runtime callbacks. */
public final class PlayerMessages {
    private static volatile LanguageCatalog catalog = LanguageCatalog.bundled("en");

    private PlayerMessages() {}

    public static void install(LanguageCatalog languageCatalog) {
        catalog = Objects.requireNonNull(languageCatalog, "languageCatalog");
    }

    public static String text(String key) {
        return catalog.translate(key, Map.of());
    }

    public static String text(String key, Map<String, String> placeholders) {
        return catalog.translate(key, placeholders);
    }
}
