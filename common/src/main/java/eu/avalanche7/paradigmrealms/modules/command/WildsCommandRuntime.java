package eu.avalanche7.paradigmrealms.modules.command;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.platform.wilds.WildsActionResult;
import eu.avalanche7.paradigmrealms.wilds.WildsState;

public interface WildsCommandRuntime {
    WildsState wildsState();
    Duration wildsCooldownRemaining(UUID player);
    WildsActionResult enterWilds(UUID player);
    WildsActionResult teleportWildsSpawn(UUID player);
    WildsActionResult requestWildsRtp(UUID player);
    WildsActionResult setWildsSpawn(UUID player);
    WildsActionResult openWildsEntry();
    WildsActionResult closeWildsEntry();
    WildsActionResult scheduleWildsReset(Instant when);
    WildsActionResult cancelWildsReset();
    WildsActionResult prepareWildsReset();
    WildsActionResult retryWildsVerification();
    void reloadWildsConfig();
    List<String> wildsValidationIssues();
    String wildsTerrainSample(int centerX, int centerZ);
    List<String> wildsBackups();
    int pruneWildsBackups() throws IOException;
}
