package eu.avalanche7.paradigmrealms.platform.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.config.RealmsConfig;
import net.fabricmc.loader.api.FabricLoader;

public final class RealmsConfigLoader {
    private RealmsConfigLoader() {}

    public static RealmsConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve("paradigm-realms.properties"));
    }

    static RealmsConfig load(Path path) {
        Properties properties = new Properties();
        try {
            Files.createDirectories(path.getParent());
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    properties.load(reader);
                }
            }
            boolean changed = false;
            Properties defaults = RealmsConfig.defaultProperties();
            for (String key : defaults.stringPropertyNames()) {
                if (!properties.containsKey(key)) {
                    properties.setProperty(key, defaults.getProperty(key));
                    changed = true;
                }
            }
            if (changed || !Files.isRegularFile(path)) {
                try (Writer writer = Files.newBufferedWriter(path)) {
                    properties.store(writer, "Paradigm Realms server configuration");
                }
            }
            RealmsConfig config = RealmsConfig.fromProperties(properties);
            if (config.presetSelection().allowedPresets().isEmpty()) {
                ParadigmRealms.LOGGER.warn("Preset allowlist is empty; no realm preset can be selected");
            }
            return config;
        } catch (IOException | IllegalArgumentException exception) {
            ParadigmRealms.LOGGER.error("Invalid Realms config {}; using protected defaults: {}",
                    path, exception.getMessage());
            return RealmsConfig.DEFAULTS;
        }
    }
}
