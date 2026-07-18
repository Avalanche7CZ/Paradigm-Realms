package eu.avalanche7.paradigmrealms.platform.wilds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.ParadigmRealms;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNode;
import eu.avalanche7.paradigmrealms.integration.permission.RealmPermissionNodes;
import eu.avalanche7.paradigmrealms.platform.ForgeRealmRuntime;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessenger;
import eu.avalanche7.paradigmrealms.platform.permission.ForgePermissionGate;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkAccessFailure;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLease;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadPurpose;
import eu.avalanche7.paradigmrealms.platform.chunk.ChunkLoadRequest;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsRtpPort;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsRtpProbeResult;
import eu.avalanche7.paradigmrealms.platform.world.StandingSafety;
import eu.avalanche7.paradigmrealms.region.BlockCoordinate;
import eu.avalanche7.paradigmrealms.region.BlockPosition;
import eu.avalanche7.paradigmrealms.region.ChunkCoordinate;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsManifestFile;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsBackupService;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsPathSafety;
import eu.avalanche7.paradigmrealms.platform.wilds.offline.WildsWorldIdentity;
import eu.avalanche7.paradigmrealms.wilds.WildsConfig;
import eu.avalanche7.paradigmrealms.wilds.WildsEntryDecision;
import eu.avalanche7.paradigmrealms.wilds.WildsEntryMode;
import eu.avalanche7.paradigmrealms.wilds.WildsEntryPolicy;
import eu.avalanche7.paradigmrealms.wilds.WildsLifecycleService;
import eu.avalanche7.paradigmrealms.wilds.WildsLifecycleState;
import eu.avalanche7.paradigmrealms.wilds.WildsManifestStage;
import eu.avalanche7.paradigmrealms.wilds.WildsResetManifest;
import eu.avalanche7.paradigmrealms.wilds.WildsResetOperation;
import eu.avalanche7.paradigmrealms.wilds.WildsRtpCoordinator;
import eu.avalanche7.paradigmrealms.wilds.WildsResetScheduleCoordinator;
import eu.avalanche7.paradigmrealms.wilds.RtpCandidate;
import eu.avalanche7.paradigmrealms.wilds.WildsSpawn;
import eu.avalanche7.paradigmrealms.wilds.WildsState;
import eu.avalanche7.paradigmrealms.wilds.WildsStateStore;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

public final class ForgeWildsService {
    public static final RegistryKey<World> WILDS_KEY = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("paradigm_realms", "wilds"));
    private final MinecraftServer server;
    private final ForgeRealmRuntime realms;
    private final ForgePermissionGate permissions;
    private final CommandMessenger messages;
    private final StateStore store;
    private final WildsLifecycleService lifecycle;
    private final WildsEntryPolicy entryPolicy = new WildsEntryPolicy();
    private final WildsGenerationValidator generationValidator = new WildsGenerationValidator();
    private final WildsRtpCoordinator rtp;
    private final WildsResetScheduleCoordinator schedule;
    private final WildsManifestFile manifests = new WildsManifestFile();
    private final WildsBackupService backups = new WildsBackupService();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Set<UUID> evacuating = new HashSet<>();
    private WildsConfig config;
    private boolean preparingReset;

    public ForgeWildsService(
            MinecraftServer server, ForgeRealmRuntime realms, WildsConfig config,
            ForgePermissionGate permissions, CommandMessenger messages) {
        this.server = server;
        this.realms = realms;
        this.config = config;
        this.permissions = permissions;
        this.messages = messages;
        this.store = new StateStore(server.getOverworld().getPersistentStateManager());
        this.lifecycle = new WildsLifecycleService(store, Clock.systemUTC(), UUID::randomUUID, secureRandom::nextLong);
        this.rtp = new WildsRtpCoordinator(
                lifecycle, () -> this.config, new RtpEffects(server, realms),
                Clock.systemUTC(), secureRandom::nextLong);
        this.schedule = new WildsResetScheduleCoordinator(
                lifecycle, () -> this.config, Clock.systemUTC());
    }

    public void start() {
        if (!store.writable()) {
            ParadigmRealms.LOGGER.error("Wilds storage is malformed and read-only: {}", store.loadError().orElse("unknown"));
            return;
        }
        if (!config.enabled()) return;
        WildsState state = lifecycle.state();
        try {
            Optional<WildsResetManifest> bootManifest = WildsBootContext.manifest();
            if (bootManifest.isPresent()
                    && bootManifest.orElseThrow().stage() == WildsManifestStage.RESTORED_PENDING_VERIFICATION) {
                verifyRestoredGeneration(bootManifest.orElseThrow());
            } else if (state.lifecycle() == WildsLifecycleState.DISABLED) {
                initializeFirstGeneration();
            } else if (state.lifecycle() == WildsLifecycleState.OFFLINE_RESET_PENDING
                    || state.lifecycle() == WildsLifecycleState.SAVE_BARRIER) {
                if (state.lifecycle() == WildsLifecycleState.SAVE_BARRIER) lifecycle.markOfflinePending();
                verifyPendingGeneration();
            } else if (state.lifecycle() == WildsLifecycleState.VERIFYING) {
                verifyPendingGeneration();
            } else if (state.lifecycle() == WildsLifecycleState.RESET_SCHEDULED) {
                skipElapsedWarnings();
            } else if (state.lifecycle() == WildsLifecycleState.ACTIVE
                    || state.lifecycle() == WildsLifecycleState.ENTRY_CLOSED) {
                validateActiveOrFail();
            }
        } catch (RuntimeException | IOException exception) {
            fail("STARTUP_VERIFICATION_FAILED", exception.getMessage());
        }
    }

    public WildsState state() { return lifecycle.state(); }
    public WildsConfig config() { return config; }

    public void updateConfig(WildsConfig replacement) {
        this.config = java.util.Objects.requireNonNull(replacement, "replacement");
    }

    public void tick() {
        tickSchedule();
        if (lifecycle.state().lifecycle() == WildsLifecycleState.EVACUATING && !preparingReset) {
            continueEvacuationAndPrepare();
        }
        rtp.tick();
    }

    public WildsActionResult enter(ServerPlayerEntity player) {
        return config.entryMode() == WildsEntryMode.RANDOM ? requestRtp(player) : teleportSpawn(player);
    }

    public WildsActionResult teleportSpawn(ServerPlayerEntity player) {
        WildsEntryDecision decision = entryDecision(player, false);
        if (!decision.allowed()) return fromEntry(decision);
        WildsState state = lifecycle.state();
        WildsSpawn spawn = state.spawn().orElse(null);
        ServerWorld world = server.getWorld(WILDS_KEY);
        if (spawn == null || spawn.epoch() != state.activeEpoch() || world == null) return WildsActionResult.UNVERIFIED;
        TeleportResult result = realms.teleports().teleportToLocation(
                player.getUuid(), DimensionId.WILDS,
                new BlockPosition(spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()), false);
        if (result == TeleportResult.SUCCESS) {
            lifecycle.approvePlayer(player.getUuid());
            return WildsActionResult.SUCCESS;
        }
        return result == TeleportResult.RIDING_OR_HAS_PASSENGERS
                ? WildsActionResult.RIDING_OR_HAS_PASSENGERS : WildsActionResult.UNSAFE_DESTINATION;
    }

    public WildsActionResult requestRtp(ServerPlayerEntity player) {
        WildsEntryDecision decision = entryDecision(player, false);
        if (!decision.allowed()) return fromEntry(decision);
        if (!has(player, RealmPermissionNodes.WILDS_RTP)) return WildsActionResult.PERMISSION_DENIED;
        if (player.hasVehicle() || player.hasPassengers()) return WildsActionResult.RIDING_OR_HAS_PASSENGERS;
        return rtp.request(player.getUuid());
    }

    public Duration cooldownRemaining(UUID player) {
        return rtp.cooldownRemaining(player);
    }

    public boolean validatePresence(ServerPlayerEntity player, boolean joiningFromSavedWilds) {
        if (!isWilds(player.getWorld())) return true;
        WildsEntryDecision decision = entryDecision(player, joiningFromSavedWilds);
        if (decision.allowed()) {
            lifecycle.approvePlayer(player.getUuid());
            return true;
        }
        evacuate(player, decision.reason().name());
        return false;
    }

    public void validateAllPresence() {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) validatePresence(player, false);
    }

    public void disconnect(UUID player) {
        rtp.disconnect(player);
        evacuating.remove(player);
    }

    public WildsActionResult scheduleReset(Instant when) {
        boolean profileValid = generationValidator.validateProfileResource(
                server, config.generationProfile()).isEmpty();
        WildsActionResult result = schedule.schedule(when, profileValid);
        if (result == WildsActionResult.SUCCESS) {
            broadcast("Only Wilds will reset at " + when + "; personal realms are unaffected.");
        }
        return result;
    }

    public WildsActionResult cancelReset() {
        WildsActionResult result = schedule.cancel();
        if (result == WildsActionResult.SUCCESS) {
            broadcast("The scheduled Wilds reset was cancelled.");
        }
        return result;
    }

    public WildsActionResult closeEntry() {
        try { lifecycle.closeEntry(); rtp.cancelAll("Wilds entry closed"); return WildsActionResult.SUCCESS; }
        catch (RuntimeException exception) { return WildsActionResult.INVALID_STATE; }
    }

    public WildsActionResult openEntry() {
        try { lifecycle.openEntry(); return WildsActionResult.SUCCESS; }
        catch (RuntimeException exception) { return WildsActionResult.INVALID_STATE; }
    }

    public WildsActionResult prepareReset() {
        WildsState state = lifecycle.state();
        try {
            if (state.lifecycle() == WildsLifecycleState.RESET_SCHEDULED) lifecycle.blockEntry();
            else if (state.lifecycle() != WildsLifecycleState.ENTRY_BLOCKED
                    && state.lifecycle() != WildsLifecycleState.EVACUATING) return WildsActionResult.INVALID_STATE;
            rtp.cancelAll("Wilds reset preparation started");
            if (lifecycle.state().lifecycle() == WildsLifecycleState.ENTRY_BLOCKED) lifecycle.beginEvacuation();
            return continueEvacuationAndPrepare();
        } catch (RuntimeException exception) {
            fail("RESET_PREPARATION_FAILED", exception.getMessage());
            return WildsActionResult.INVALID_STATE;
        }
    }

    public WildsActionResult retryVerification() {
        try { lifecycle.beginVerification(); verifyPendingGeneration(); return lifecycle.state().lifecycle() == WildsLifecycleState.ACTIVE
                ? WildsActionResult.SUCCESS : WildsActionResult.UNVERIFIED; }
        catch (RuntimeException | IOException exception) { fail("VERIFICATION_RETRY_FAILED", exception.getMessage()); return WildsActionResult.UNVERIFIED; }
    }

    public List<String> validationIssues() {
        WildsState state = lifecycle.state();
        ArrayList<String> issues = new ArrayList<>();
        ServerWorld world = server.getWorld(WILDS_KEY);
        WildsProfileIdAndSeed expected = expectedGeneration(state);
        issues.addAll(generationValidator.validate(server, world, expected.profile(), expected.seed()));
        state.spawn().ifPresentOrElse(spawn -> {
            if (spawn.epoch() != state.activeEpoch()) issues.add("Wilds spawn epoch is stale");
            else if (world != null && !isSafe(world, BlockPos.ofFloored(spawn.x(), spawn.y(), spawn.z()))) {
                issues.add("Wilds spawn is unsafe");
            }
        }, () -> issues.add("Wilds spawn is missing"));
        if (!store.writable()) issues.add("Wilds state is read-only: " + store.loadError().orElse("malformed"));
        return List.copyOf(issues);
    }

    public String terrainSample(int centerX, int centerZ) {
        ServerWorld world = requireWorld();
        ChunkCoordinate chunk = new ChunkCoordinate(
                Math.floorDiv(centerX, 16), Math.floorDiv(centerZ, 16));
        try (ChunkLease ignored = realms.serverPlatform().chunks().acquire(ChunkLoadRequest.one(
                DimensionId.WILDS, chunk, ChunkLoadPurpose.WILDS_SEARCH, true))) {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int x = Math.addExact(centerX, dx);
                    int z = Math.addExact(centerZ, dz);
                    BlockPos top = world.getTopPosition(Heightmap.Type.WORLD_SURFACE,
                            new BlockPos(x, world.getBottomY(), z));
                    String sample = x + "," + z + "," + top.getY() + ","
                            + net.minecraft.registry.Registries.BLOCK.getId(world.getBlockState(top.down()).getBlock())
                            + "," + world.getBiome(top).getKey().map(RegistryKey::getValue).orElse(Identifier.of("minecraft", "unknown")) + ";";
                    digest.update(sample.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        } catch (ChunkAccessFailure exception) {
            throw new IllegalStateException("Wilds terrain sample chunk is unavailable", exception);
        }
    }

    public WildsActionResult setSpawn(ServerPlayerEntity player) {
        WildsState state = lifecycle.state();
        if (state.lifecycle() != WildsLifecycleState.ACTIVE) return WildsActionResult.ENTRY_BLOCKED;
        if (!isWilds(player.getWorld())) return WildsActionResult.NOT_IN_WILDS;
        BlockPos pos = player.getBlockPos();
        if (!isSafe(player.getServerWorld(), pos)) return WildsActionResult.UNSAFE_DESTINATION;
        WildsSpawn spawn = new WildsSpawn(state.activeEpoch(), player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch());
        replaceSpawn(spawn);
        return WildsActionResult.SUCCESS;
    }

    public boolean mutationAllowed(ServerPlayerEntity player, World world, BlockPos target) {
        if (!isWilds(world)) return true;
        WildsState state = lifecycle.state();
        if ((state.lifecycle() != WildsLifecycleState.ACTIVE
                && state.lifecycle() != WildsLifecycleState.RESET_SCHEDULED)
                || !state.generationVerified()) return false;
        WildsSpawn spawn = state.spawn().orElse(null);
        if (spawn == null || config.spawnProtectionRadius() == 0) return true;
        long dx = (long) target.getX() - (long) Math.floor(spawn.x());
        long dz = (long) target.getZ() - (long) Math.floor(spawn.z());
        long radius = config.spawnProtectionRadius();
        if (dx * dx + dz * dz > radius * radius) return true;
        return realms.bypass().enabled(player.getUuid());
    }

    public boolean environmentalMutationAllowed(World world, BlockPos target) {
        if (!isWilds(world)) return true;
        WildsState state = lifecycle.state();
        if (state.lifecycle() != WildsLifecycleState.ACTIVE
                && state.lifecycle() != WildsLifecycleState.RESET_SCHEDULED) return false;
        WildsSpawn spawn = state.spawn().orElse(null);
        if (spawn == null || config.spawnProtectionRadius() == 0) return true;
        long dx = (long) target.getX() - (long) Math.floor(spawn.x());
        long dz = (long) target.getZ() - (long) Math.floor(spawn.z());
        long radius = config.spawnProtectionRadius();
        return dx * dx + dz * dz > radius * radius;
    }

    public List<String> backupList() {
        try {
            return backups.list(worldRoot());
        } catch (IOException exception) {
            return List.of("ERROR: " + exception.getMessage());
        }
    }

    public int pruneBackups() throws IOException {
        if (state().lifecycle() != WildsLifecycleState.ACTIVE || !state().generationVerified()) {
            throw new IOException("backups may only be pruned after successful verification");
        }
        return backups.prune(worldRoot(), config.backupRetentionCount());
    }

    public void shutdown() { rtp.cancelAll("server stopping"); rtp.clear(); evacuating.clear(); }

    private void initializeFirstGeneration() throws IOException {
        ServerWorld world = requireWorld();
        long seed = world.getSeed();
        List<String> issues = generationValidator.validate(server, world, config.generationProfile(), seed);
        if (!issues.isEmpty()) throw new IllegalStateException(String.join("; ", issues));
        WildsSpawn spawn = resolveSpawn(world, 1);
        lifecycle.activateInitial(seed, config.generationProfile(), spawn);
        WildsBootContext.persistActive(worldRoot(), seed, config.generationProfile());
        scheduleRecurringIfRequired();
        ParadigmRealms.LOGGER.info("Wilds generation epoch 1 ACTIVE, profile {}, seed verified",
                config.generationProfile());
    }

    private void validateActiveOrFail() throws IOException {
        WildsState state = lifecycle.state();
        List<String> issues = generationValidator.validate(server, requireWorld(), state.activeProfile().orElseThrow(), state.activeSeed());
        if (!issues.isEmpty()) throw new IllegalStateException(String.join("; ", issues));
        if (state.spawn().isEmpty() || state.spawn().orElseThrow().epoch() != state.activeEpoch()
                || !isSafe(requireWorld(), BlockPos.ofFloored(state.spawn().orElseThrow().x(),
                        state.spawn().orElseThrow().y(), state.spawn().orElseThrow().z()))) {
            throw new IllegalStateException("active Wilds spawn is missing or unsafe");
        }
        WildsBootContext.persistActive(worldRoot(), state.activeSeed(), state.activeProfile().orElseThrow());
    }

    private void verifyPendingGeneration() throws IOException {
        WildsState state = lifecycle.state();
        if (state.lifecycle() != WildsLifecycleState.VERIFYING) lifecycle.beginVerification();
        state = lifecycle.state();
        WildsResetOperation operation = state.operation().orElseThrow();
        WildsResetManifest manifest = manifests.read(worldRoot())
                .orElseThrow(() -> new IllegalStateException("offline reset manifest is missing"));
        if (!manifest.operationId().equals(operation.operationId())) throw new IllegalStateException("manifest operation mismatch");
        if (manifest.stage() != WildsManifestStage.SERVER_BOOTED && manifest.stage() != WildsManifestStage.FAILED) {
            throw new IllegalStateException("manifest stage is " + manifest.stage());
        }
        Path backup = WildsPathSafety.resolve(worldRoot(), manifest.quarantineRelativePath(), false);
        if (!Files.isDirectory(backup, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("quarantine disappeared");
        List<String> issues = generationValidator.validate(server, requireWorld(), operation.targetProfile(), operation.targetSeed());
        if (!issues.isEmpty()) throw new IllegalStateException(String.join("; ", issues));
        WildsSpawn spawn = resolveSpawn(requireWorld(), operation.targetEpoch());
        if (!requireWorld().getPlayers(player -> true).isEmpty()) throw new IllegalStateException("players entered during verification");
        lifecycle.completeVerification(spawn);
        store.flush();
        WildsBootContext.persistActive(worldRoot(), operation.targetSeed(), operation.targetProfile());
        manifests.write(worldRoot(), manifest.withStage(WildsManifestStage.COMPLETED, Instant.now()));
        scheduleRecurringIfRequired();
        broadcast("Wilds reset completed. Personal realms were not changed.");
        ParadigmRealms.LOGGER.info("Wilds reset {} completed at epoch {} with verified seed {}",
                operation.operationId(), operation.targetEpoch(), operation.targetSeed());
        if (operation.settings().deleteOldBackupsAfterVerification()) {
            try { pruneBackups(); } catch (IOException exception) {
                ParadigmRealms.LOGGER.warn("Wilds backup pruning failed after verification: {}", exception.getMessage());
            }
        }
    }

    private void verifyRestoredGeneration(WildsResetManifest manifest) throws IOException {
        WildsState state = lifecycle.state();
        if (state.lifecycle() != WildsLifecycleState.FAILED
                && state.lifecycle() != WildsLifecycleState.OFFLINE_RESET_PENDING
                && state.lifecycle() != WildsLifecycleState.VERIFYING) {
            throw new IllegalStateException("rollback requires an incomplete reset lifecycle state");
        }
        WildsResetOperation operation = state.operation().orElseThrow();
        if (!operation.operationId().equals(manifest.operationId())) {
            throw new IllegalStateException("rollback manifest operation mismatch");
        }
        List<String> issues = generationValidator.validate(
                server, requireWorld(), manifest.sourceProfile(), manifest.sourceSeed());
        if (!issues.isEmpty()) throw new IllegalStateException(String.join("; ", issues));
        WildsSpawn spawn = state.spawn()
                .filter(value -> value.epoch() == manifest.sourceEpoch())
                .filter(value -> isSafe(requireWorld(), BlockPos.ofFloored(value.x(), value.y(), value.z())))
                .orElseGet(() -> resolveSpawn(requireWorld(), manifest.sourceEpoch()));
        if (!requireWorld().getPlayers(player -> true).isEmpty()) {
            throw new IllegalStateException("players entered during rollback verification");
        }
        lifecycle.completeRollback(spawn);
        store.flush();
        WildsBootContext.persistActive(worldRoot(), manifest.sourceSeed(), manifest.sourceProfile());
        manifests.write(worldRoot(), manifest.withStage(WildsManifestStage.ROLLED_BACK, Instant.now()));
        scheduleRecurringIfRequired();
        ParadigmRealms.LOGGER.warn("Wilds reset {} rolled back to verified source epoch {}",
                manifest.operationId(), manifest.sourceEpoch());
    }

    private WildsActionResult continueEvacuationAndPrepare() {
        if (preparingReset) return WildsActionResult.SUCCESS;
        preparingReset = true;
        try {
            for (ServerPlayerEntity player : List.copyOf(requireWorld().getPlayers(value -> true))) {
                evacuate(player, "WILDS_RESET");
            }
            if (!requireWorld().getPlayers(value -> true).isEmpty()) return WildsActionResult.PLAYERS_REMAIN;
            lifecycle.beginSaveBarrier();
            store.flush();
            boolean saved = server.saveAll(false, true, true);
            if (!saved) {
                fail("SAVE_BARRIER_FAILED", "Minecraft saveAll reported failure");
                return WildsActionResult.SAVE_FAILED;
            }
            WildsResetOperation operation = lifecycle.state().operation().orElseThrow();
            Path root = worldRoot();
            Path wildsPath = DimensionType.getSaveDirectory(WILDS_KEY, root).normalize();
            String wildsRelative = root.relativize(wildsPath).toString().replace('\\', '/');
            if (!"dimensions/paradigm_realms/wilds".equals(wildsRelative)) {
                throw new IOException("unexpected mapped Wilds path: " + wildsRelative);
            }
            String quarantine = "paradigm-realms-backups/wilds/epoch-" + operation.sourceEpoch()
                    + "-operation-" + operation.operationId();
            WildsResetManifest manifest = new WildsResetManifest(1, WildsWorldIdentity.of(root),
                    DimensionId.WILDS.toString(), operation.operationId(), operation.sourceEpoch(),
                    operation.targetEpoch(), operation.sourceSeed(), operation.targetSeed(),
                    operation.sourceProfile(), operation.targetProfile(), wildsRelative, quarantine,
                    WildsManifestStage.PREPARED, Instant.now(), Instant.now(), Optional.empty());
            manifests.write(root, manifest);
            lifecycle.markOfflinePending();
            store.flush();
            broadcast("Wilds is saved and ready for an offline reset. Stop the server and run the reset tool.");
            ParadigmRealms.LOGGER.warn("Wilds reset {} is OFFLINE_RESET_PENDING. Run the offline reset tool after shutdown.",
                    operation.operationId());
            if (operation.settings().shutdownWhenPrepared()) server.stop(false);
            return WildsActionResult.SUCCESS;
        } catch (IOException | RuntimeException exception) {
            fail("RESET_PREPARATION_FAILED", exception.getMessage());
            return WildsActionResult.MANIFEST_FAILED;
        } finally {
            preparingReset = false;
        }
    }

    private void tickSchedule() {
        var tick = schedule.tick();
        tick.crossedWarningsSeconds().forEach(seconds -> broadcast(
                "Wilds resets in " + seconds + " seconds. Personal realms are unaffected."));
        if (tick.resetDue()) prepareReset();
    }

    private void skipElapsedWarnings() {
        schedule.skipElapsedWarnings();
    }

    private void scheduleRecurringIfRequired() {
        schedule.nextRecurringReset().ifPresent(this::scheduleReset);
    }

    private WildsSpawn resolveSpawn(ServerWorld world, long epoch) {
        BlockPos configured = world.getSpawnPos();
        for (int attempt = 0; attempt < 32; attempt++) {
            int ring = attempt / 8;
            int x = configured.getX() + ((attempt % 3) - 1) * ring * 16;
            int z = configured.getZ() + (((attempt / 3) % 3) - 1) * ring * 16;
            ChunkCoordinate chunk = new ChunkCoordinate(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            try (ChunkLease ignored = realms.serverPlatform().chunks().acquire(ChunkLoadRequest.one(
                    DimensionId.WILDS, chunk, ChunkLoadPurpose.WILDS_SPAWN, true))) {
                BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(x, world.getBottomY(), z));
                if (isSafe(world, top)) return new WildsSpawn(epoch, top.getX() + 0.5, top.getY(),
                        top.getZ() + 0.5, 0, 0);
            } catch (ChunkAccessFailure failure) {
                if (failure.reason() == ChunkAccessFailure.Reason.WORLD_UNAVAILABLE) {
                    throw new IllegalStateException("Wilds dimension became unavailable", failure);
                }
            }
        }
        throw new IllegalStateException("bounded Wilds spawn search found no safe position");
    }

    private boolean isSafe(ServerWorld world, BlockPos feet) {
        return realms.serverPlatform().worlds().safeStanding(
                DimensionId.WILDS,
                new BlockCoordinate(feet.getX(), feet.getY(), feet.getZ()),
                new StandingSafety(config.rtp().avoidFluids(), config.rtp().avoidLeaves(),
                        config.rtp().avoidPowderSnow(), true));
    }

    private void evacuate(ServerPlayerEntity player, String reason) {
        if (!evacuating.add(player.getUuid())) return;
        try {
            player.stopRiding();
            player.getPassengerList().forEach(entity -> entity.stopRiding());
            Optional<eu.avalanche7.paradigmrealms.domain.realm.Realm> owned = realms.repository().findByOwner(player.getUuid());
            if (owned.isPresent() && owned.orElseThrow().state() == RealmLifecycleState.ACTIVE
                    && realms.teleportHome(player, owned.orElseThrow()) == TeleportResult.SUCCESS) {
                player.sendMessage(Text.literal("You were evacuated from Wilds to your personal realm (" + reason + ")."), false);
                return;
            }
            if (realms.teleports().teleportToOverworldSpawn(player.getUuid()) == TeleportResult.SUCCESS) {
                player.sendMessage(Text.literal("You were evacuated from Wilds to the Overworld (" + reason + ")."), false);
                return;
            }
            ParadigmRealms.LOGGER.error("Could not evacuate player {} from Wilds: {}", player.getUuid(), reason);
        } finally { evacuating.remove(player.getUuid()); }
    }

    private WildsEntryDecision entryDecision(ServerPlayerEntity player, boolean savedJoin) {
        return entryPolicy.evaluate(lifecycle.state(), player.getUuid(),
                has(player, RealmPermissionNodes.WILDS_ENTER), savedJoin);
    }

    private boolean has(ServerPlayerEntity player, RealmPermissionNode node) {
        return permissions.hasPermission(player.getCommandSource(), node.node(), node.fallbackOpLevel());
    }

    private static WildsActionResult fromEntry(WildsEntryDecision decision) {
        return switch (decision.reason()) {
            case DISABLED -> WildsActionResult.DISABLED;
            case PERMISSION_DENIED -> WildsActionResult.PERMISSION_DENIED;
            case GENERATION_UNVERIFIED, STALE_PLAYER_EPOCH -> WildsActionResult.UNVERIFIED;
            case LIFECYCLE_BLOCKED -> WildsActionResult.ENTRY_BLOCKED;
            case ALLOWED -> WildsActionResult.SUCCESS;
        };
    }

    private void replaceSpawn(WildsSpawn replacement) {
        WildsState state = lifecycle.state();
        store.save(new WildsState(1, state.revision() + 1, state.lifecycle(), state.activeEpoch(),
                state.activeSeed(), state.activeProfile(), Optional.of(replacement), state.generationVerified(),
                state.activatedAt(), state.lastSuccessfulResetAt(), state.nextScheduledReset(), state.operation(),
                state.approvedPlayerEpochs(), state.failure()));
    }

    private ServerWorld requireWorld() {
        ServerWorld world = server.getWorld(WILDS_KEY);
        if (world == null) throw new IllegalStateException("Wilds dimension is unavailable");
        return world;
    }

    private Path worldRoot() throws IOException { return server.getSavePath(WorldSavePath.ROOT).toRealPath(); }

    private WildsProfileIdAndSeed expectedGeneration(WildsState state) {
        if (state.operation().isPresent() && (state.lifecycle() == WildsLifecycleState.VERIFYING
                || state.lifecycle() == WildsLifecycleState.OFFLINE_RESET_PENDING
                || state.lifecycle() == WildsLifecycleState.FAILED)) {
            WildsResetOperation operation = state.operation().orElseThrow();
            return new WildsProfileIdAndSeed(operation.targetProfile(), operation.targetSeed());
        }
        return new WildsProfileIdAndSeed(state.activeProfile().orElse(config.generationProfile()), state.activeSeed());
    }

    private void fail(String code, String detail) {
        try {
            Path root = worldRoot();
            Optional<WildsResetManifest> manifest = manifests.read(root);
            if (manifest.isPresent() && manifest.orElseThrow().stage() != WildsManifestStage.COMPLETED) {
                manifests.write(root, manifest.orElseThrow().withStage(WildsManifestStage.FAILED, Instant.now()));
            }
        } catch (IOException ignored) {
            ParadigmRealms.LOGGER.warn("Could not update Wilds reset manifest to FAILED");
        }
        try { lifecycle.fail(code, detail == null ? "unspecified failure" : detail); store.flush(); }
        catch (RuntimeException nested) { ParadigmRealms.LOGGER.error("Wilds failure could not be persisted", nested); }
        ParadigmRealms.LOGGER.error("Wilds entered FAILED: {} - {}", code, detail);
    }

    private void broadcast(String text) {
        server.getPlayerManager().broadcast(Text.literal(text), false);
    }

    private static boolean isWilds(World world) {
        return world.getRegistryKey().getValue().toString().equals(DimensionId.WILDS.toString());
    }

    private static final class RtpEffects implements WildsRtpPort {
        private final MinecraftServer server;
        private final ForgeRealmRuntime realms;

        private RtpEffects(MinecraftServer server, ForgeRealmRuntime realms) {
            this.server = java.util.Objects.requireNonNull(server, "server");
            this.realms = java.util.Objects.requireNonNull(realms, "realms");
        }

        @Override public boolean online(UUID player) {
            return server.getPlayerManager().getPlayer(player) != null;
        }

        @Override public WildsRtpProbeResult probe(
                UUID playerId, RtpCandidate candidate, StandingSafety safety,
                boolean allowChunkGeneration) {
            var world = server.getWorld(WILDS_KEY);
            if (world == null) return WildsRtpProbeResult.rejected(
                    WildsRtpProbeResult.Status.WORLD_UNAVAILABLE);
            BlockCoordinate borderProbe = new BlockCoordinate(candidate.x(), 64, candidate.z());
            if (!realms.serverPlatform().worlds().insideWorldBorder(DimensionId.WILDS, borderProbe)) {
                return WildsRtpProbeResult.rejected(WildsRtpProbeResult.Status.WORLD_BORDER);
            }
            ChunkCoordinate chunk = borderProbe.chunk();
            boolean loaded = realms.serverPlatform().chunks().loaded(DimensionId.WILDS, chunk);
            if (!loaded && !allowChunkGeneration) {
                return WildsRtpProbeResult.rejected(WildsRtpProbeResult.Status.CHUNK_BUDGET);
            }
            try (ChunkLease ignored = realms.serverPlatform().chunks().acquire(ChunkLoadRequest.one(
                    DimensionId.WILDS, chunk, ChunkLoadPurpose.WILDS_SEARCH, !loaded))) {
                BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        new BlockPos(candidate.x(), world.getBottomY(), candidate.z()));
                BlockCoordinate feet = new BlockCoordinate(top.getX(), top.getY(), top.getZ());
                if (!realms.serverPlatform().worlds().safeStanding(DimensionId.WILDS, feet, safety)) {
                    return WildsRtpProbeResult.rejected(WildsRtpProbeResult.Status.UNSAFE_SURFACE);
                }
                var player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) return WildsRtpProbeResult.rejected(
                        WildsRtpProbeResult.Status.WORLD_UNAVAILABLE);
                return WildsRtpProbeResult.safe(new BlockPosition(
                        top.getX() + 0.5, top.getY(), top.getZ() + 0.5,
                        player.getYaw(), player.getPitch()), !loaded);
            } catch (ChunkAccessFailure failure) {
                return WildsRtpProbeResult.rejected(
                        failure.reason() == ChunkAccessFailure.Reason.WORLD_UNAVAILABLE
                                ? WildsRtpProbeResult.Status.WORLD_UNAVAILABLE
                                : WildsRtpProbeResult.Status.CHUNK_BUDGET);
            }
        }

        @Override public TeleportResult teleport(UUID player, BlockPosition destination) {
            return realms.teleports().teleportToLocation(player, DimensionId.WILDS, destination, false);
        }

        @Override public void notify(UUID player, String message) {
            var online = server.getPlayerManager().getPlayer(player);
            if (online != null) online.sendMessage(Text.literal(message), false);
        }
    }

    private static final class StateStore implements WildsStateStore {
        private static final String STORAGE_KEY = "paradigm_realms_wilds";
        private final PersistentStateManager manager;
        private final WildsPersistentState persistent;

        private StateStore(PersistentStateManager manager) {
            this.manager = manager;
            this.persistent = manager.getOrCreate(WildsPersistentState.TYPE, STORAGE_KEY);
        }

        @Override public WildsState load() { return persistent.state(); }
        @Override public void save(WildsState state) { persistent.replace(state); }
        @Override public void flush() { manager.save(); }
        private boolean writable() { return persistent.writable(); }
        private Optional<String> loadError() { return persistent.loadError(); }
    }

    private record WildsProfileIdAndSeed(
            eu.avalanche7.paradigmrealms.wilds.WildsProfileId profile, long seed) {}
}
