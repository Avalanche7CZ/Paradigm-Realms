package eu.avalanche7.paradigmrealms.platform.wilds;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsManifestFile;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsPathSafety;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsWorldIdentity;
import eu.avalanche7.paradigmrealms.wilds.WildsManifestStage;
import eu.avalanche7.paradigmrealms.wilds.WildsProfileId;
import eu.avalanche7.paradigmrealms.wilds.WildsResetManifest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

public final class WildsBootContext {
    private static final String ACTIVE_RELATIVE = "paradigm-realms/wilds-active.properties";
    private static volatile BootPlan plan;

    private WildsBootContext() {}

    public static void beforeWorlds(MinecraftServer server) {
        try {
            Path root = server.getSavePath(WorldSavePath.ROOT).toRealPath();
            WildsManifestFile manifests = new WildsManifestFile();
            Optional<WildsResetManifest> pending = manifests.read(root);
            if (pending.isPresent()) {
                WildsResetManifest manifest = pending.orElseThrow();
                if (!manifest.worldIdentity().equals(WildsWorldIdentity.of(root))) {
                    throw new IllegalStateException("Wilds reset manifest belongs to another save");
                }
                if (manifest.stage() == WildsManifestStage.PREPARED) {
                    throw new IllegalStateException("Wilds reset is PREPARED; stop and run the offline reset tool");
                }
                if (manifest.stage() == WildsManifestStage.OLD_WORLD_QUARANTINED
                        || manifest.stage() == WildsManifestStage.SERVER_BOOTED
                        || manifest.stage() == WildsManifestStage.FAILED) {
                    Path source = WildsPathSafety.resolve(root, manifest.expectedRelativeWildsPath(), true);
                    Path backup = WildsPathSafety.resolve(root, manifest.quarantineRelativePath(), false);
                    if (manifest.stage() == WildsManifestStage.OLD_WORLD_QUARANTINED && Files.exists(source)) {
                        throw new IllegalStateException("both active Wilds and quarantine exist after offline move");
                    }
                    if (!Files.isDirectory(backup)) throw new IllegalStateException("Wilds quarantine is missing");
                    plan = new BootPlan(root, manifest.targetSeed(), manifest.targetProfile(), Optional.of(manifest));
                    if (manifest.stage() == WildsManifestStage.OLD_WORLD_QUARANTINED) {
                        manifests.write(root, manifest.withStage(WildsManifestStage.SERVER_BOOTED, Instant.now()));
                    }
                    ParadigmRealms.LOGGER.info("Wilds bootstrap operation {} target epoch {} seed override installed before world creation",
                            manifest.operationId(), manifest.targetEpoch());
                    return;
                }
                if (manifest.stage() == WildsManifestStage.RESTORED_PENDING_VERIFICATION) {
                    Path source = WildsPathSafety.resolve(root, manifest.expectedRelativeWildsPath(), false);
                    Path backup = WildsPathSafety.resolve(root, manifest.quarantineRelativePath(), true);
                    if (!Files.isDirectory(source) || Files.exists(backup)) {
                        throw new IllegalStateException("restored Wilds path invariant failed");
                    }
                    plan = new BootPlan(root, manifest.sourceSeed(), manifest.sourceProfile(), Optional.of(manifest));
                    ParadigmRealms.LOGGER.warn("Wilds rollback operation {} source epoch {} installed for verification before world creation",
                            manifest.operationId(), manifest.sourceEpoch());
                    return;
                }
            }
            plan = readActive(root).orElse(null);
            if (plan != null) ParadigmRealms.LOGGER.info("Wilds active seed override installed before world creation");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot validate Wilds bootstrap before world creation", exception);
        }
    }

    public static Optional<Long> seed() { return Optional.ofNullable(plan).map(BootPlan::seed); }
    public static Optional<WildsProfileId> profile() { return Optional.ofNullable(plan).map(BootPlan::profile); }
    public static Optional<WildsResetManifest> manifest() {
        return Optional.ofNullable(plan).flatMap(BootPlan::manifest);
    }

    public static void persistActive(Path root, long seed, WildsProfileId profile) throws IOException {
        Path file = root.resolve(ACTIVE_RELATIVE);
        Files.createDirectories(file.getParent());
        Properties values = new Properties();
        values.setProperty("seed", Long.toString(seed));
        values.setProperty("profile", profile.value());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            values.store(writer, "Paradigm Realms active Wilds generation");
        }
        try {
            Files.move(temporary, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        plan = new BootPlan(root, seed, profile, Optional.empty());
    }

    public static void clear() { plan = null; }

    private static Optional<BootPlan> readActive(Path root) throws IOException {
        Path file = root.resolve(ACTIVE_RELATIVE);
        if (!Files.exists(file)) return Optional.empty();
        if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException("active Wilds control is not a regular file");
        }
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
        return Optional.of(new BootPlan(root, Long.parseLong(values.getProperty("seed")),
                new WildsProfileId(values.getProperty("profile")), Optional.empty()));
    }

    private record BootPlan(Path root, long seed, WildsProfileId profile, Optional<WildsResetManifest> manifest) {}
}
