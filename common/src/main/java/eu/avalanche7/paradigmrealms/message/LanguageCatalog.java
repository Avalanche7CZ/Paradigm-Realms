package eu.avalanche7.paradigmrealms.message;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Server-side language catalog with an English resource fallback. */
public final class LanguageCatalog {
    private static final String RESOURCE_ROOT = "/paradigm_realms/lang/";
    private static final List<String> BUILTIN_LANGUAGES = List.of("en", "cs");
    private static final Pattern LANGUAGE_CODE = Pattern.compile("[a-z]{2}(?:_[a-z]{2})?");

    private final String language;
    private final Map<String, String> translations;

    private LanguageCatalog(String language, Map<String, String> translations) {
        this.language = language;
        this.translations = Map.copyOf(translations);
    }

    static LanguageCatalog bundled(String language) {
        Map<String, String> values = new HashMap<>();
        loadResource("en", values, ignored -> {});
        if (!"en".equals(language)) loadResource(language, values, ignored -> {});
        return new LanguageCatalog(language, values);
    }

    public static LanguageCatalog load(
            Path languageDirectory,
            String requestedLanguage,
            Consumer<String> info,
            Consumer<String> warning) {
        Objects.requireNonNull(languageDirectory, "languageDirectory");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(warning, "warning");
        String language = normalize(requestedLanguage, warning);
        copyBuiltinFiles(languageDirectory, warning);

        Map<String, String> values = new HashMap<>();
        loadResource("en", values, warning);
        if (!"en".equals(language)) loadResource(language, values, warning);

        Path override = languageDirectory.resolve(language + ".json");
        if (Files.isRegularFile(override)) {
            loadFile(override, values, warning);
            info.accept("Loaded Paradigm Realms language " + language + " from " + override);
        } else {
            warning.accept("Language file " + override + " does not exist; using bundled fallbacks");
        }
        LanguageCatalog catalog = new LanguageCatalog(language, values);
        PlayerMessages.install(catalog);
        return catalog;
    }

    public String language() {
        return language;
    }

    public String translate(String key, Map<String, String> placeholders, String fallback) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        String translated = translations.getOrDefault(key, Objects.requireNonNull(fallback, "fallback"));
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            translated = translated.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return translated;
    }

    public String translate(String key, Map<String, String> placeholders) {
        return translate(key, placeholders, key);
    }

    public Map<String, String> translations() {
        return translations;
    }

    private static String normalize(String requested, Consumer<String> warning) {
        String normalized = requested == null ? "en" : requested.trim().toLowerCase(java.util.Locale.ROOT);
        if (!LANGUAGE_CODE.matcher(normalized).matches()) {
            warning.accept("Invalid language code '" + requested + "'; using en");
            return "en";
        }
        return normalized;
    }

    private static void copyBuiltinFiles(Path directory, Consumer<String> warning) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            warning.accept("Could not create language directory " + directory + ": " + exception.getMessage());
            return;
        }
        for (String language : BUILTIN_LANGUAGES) {
            Path target = directory.resolve(language + ".json");
            if (Files.exists(target)) continue;
            try (InputStream input = LanguageCatalog.class.getResourceAsStream(
                    RESOURCE_ROOT + language + ".json")) {
                if (input == null) {
                    warning.accept("Bundled language resource is missing: " + language);
                    continue;
                }
                Files.copy(input, target);
            } catch (IOException exception) {
                warning.accept("Could not copy language file " + target + ": " + exception.getMessage());
            }
        }
    }

    private static void loadResource(String language, Map<String, String> target, Consumer<String> warning) {
        try (InputStream input = LanguageCatalog.class.getResourceAsStream(RESOURCE_ROOT + language + ".json")) {
            if (input == null) {
                warning.accept("Bundled language resource is missing: " + language);
                return;
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                flatten(new Gson().fromJson(reader, JsonObject.class), "", target);
            }
        } catch (Exception exception) {
            warning.accept("Could not load bundled language " + language + ": " + exception.getMessage());
        }
    }

    private static void loadFile(Path path, Map<String, String> target, Consumer<String> warning) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            flatten(new Gson().fromJson(reader, JsonObject.class), "", target);
        } catch (Exception exception) {
            warning.accept("Could not load language file " + path + ": " + exception.getMessage());
        }
    }

    private static void flatten(JsonObject object, String prefix, Map<String, String> target) {
        if (object == null) return;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue().isJsonObject()) {
                flatten(entry.getValue().getAsJsonObject(), key, target);
            } else if (entry.getValue().isJsonPrimitive()) {
                target.put(key, entry.getValue().getAsString());
            }
        }
    }
}
