package eu.avalanche7.paradigmrealms.platform.wilds;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.avalanche7.paradigmrealms.wilds.WildsProfileId;

class WildsBootContextTest {
    @TempDir Path root;

    @AfterEach
    void clearBootPlan() { WildsBootContext.clear(); }

    @Test
    void disablingSkipsMalformedPendingResetAndPreservesControlFiles() throws Exception {
        Path pending = root.resolve(eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsManifestFile.RELATIVE_PATH);
        Files.createDirectories(pending.getParent());
        Files.writeString(pending, "malformed pending reset");
        WildsBootContext.persistActive(root, 42L, new WildsProfileId("paradigm_realms:overworld_like"));
        assertEquals(42L, WildsBootContext.seed().orElseThrow());
        WildsBootContext.beforeWorlds(root, false);
        assertFalse(WildsBootContext.enabled());
        assertTrue(WildsBootContext.seed().isEmpty());
        assertTrue(WildsBootContext.manifest().isEmpty());
        assertEquals("malformed pending reset", Files.readString(pending));
        assertTrue(Files.exists(root.resolve("paradigm-realms/wilds-active.properties")));
    }

    @Test
    void disabledBootstrapDoesNotCreateMissingWorldDirectories() {
        Path missing = root.resolve("missing-world");
        WildsBootContext.beforeWorlds(missing, false);
        assertFalse(Files.exists(missing));
    }

    @Test
    void reenabledBootstrapRestoresPersistedSeed() throws Exception {
        WildsBootContext.persistActive(root, 42L, new WildsProfileId("paradigm_realms:overworld_like"));
        WildsBootContext.beforeWorlds(root, false);
        WildsBootContext.beforeWorlds(root, true);
        assertTrue(WildsBootContext.enabled());
        assertEquals(42L, WildsBootContext.seed().orElseThrow());
    }
}
