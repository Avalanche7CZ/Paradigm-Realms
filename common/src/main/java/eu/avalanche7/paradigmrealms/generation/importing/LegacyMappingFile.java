package eu.avalanche7.paradigmrealms.generation.importing;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class LegacyMappingFile {
    public LegacyBlockMapping load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return LegacyBlockMapping.builtIn();
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { values.load(reader); }
        Map<String, SymbolicBlockState> mappings = new HashMap<>();
        for (String key : values.stringPropertyNames()) {
            if (!key.matches("[0-9]{1,4}(:[0-9]{1,2})?")) throw new IOException("invalid legacy mapping key " + key);
            try { mappings.put(key, SymbolicBlockState.parse(values.getProperty(key).trim())); }
            catch (IllegalArgumentException exception) { throw new IOException("invalid symbolic mapping for " + key + ": " + exception.getMessage(), exception); }
        }
        return new LegacyBlockMapping(mappings);
    }
}
