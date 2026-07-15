package eu.avalanche7.paradigmrealms.platform;

import java.util.Set;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.Map;
import java.util.stream.Collectors;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.application.AllocationPreviewService;
import eu.avalanche7.paradigmrealms.application.RealmInspectionService;
import eu.avalanche7.paradigmrealms.application.RealmMemberInspectionService;
import eu.avalanche7.paradigmrealms.application.RealmTeleportService;
import eu.avalanche7.paradigmrealms.application.RealmVisitService;
import eu.avalanche7.paradigmrealms.core.RealmsRuntime;
import eu.avalanche7.paradigmrealms.core.RealmsRuntimeHooks;
import eu.avalanche7.paradigmrealms.core.RealmsCommandRuntime;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionResult;
import eu.avalanche7.paradigmrealms.generation.PresetCatalogSnapshot;
import eu.avalanche7.paradigmrealms.generation.importing.PresetImportResult;
import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.persistence.PersistentRealmRepository;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationReport;
import eu.avalanche7.paradigmrealms.platform.persistence.FabricRealmStateStore;
import eu.avalanche7.paradigmrealms.platform.generation.FabricPresetCatalogManager;
import eu.avalanche7.paradigmrealms.platform.generation.FabricRealmPresetGenerator;
import eu.avalanche7.paradigmrealms.platform.teleport.SetSpawnResult;
import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.membership.MembershipLimits;
import eu.avalanche7.paradigmrealms.membership.MembershipResult;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.membership.RealmMembershipService;
import eu.avalanche7.paradigmrealms.config.RealmsConfig;
import eu.avalanche7.paradigmrealms.platform.config.RealmsConfigLoader;
import eu.avalanche7.paradigmrealms.platform.protection.FabricProtectionHooks;
import eu.avalanche7.paradigmrealms.platform.protection.FabricProtectionService;
import eu.avalanche7.paradigmrealms.platform.protection.RealmPresenceService;
import eu.avalanche7.paradigmrealms.platform.protection.RealmSessionBypass;
import eu.avalanche7.paradigmrealms.region.RealmRegionIndex;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationIssue;
import eu.avalanche7.paradigmrealms.platform.permission.FabricPermissionGate;
import eu.avalanche7.paradigmrealms.platform.message.CommandMessenger;
import eu.avalanche7.paradigmrealms.platform.wilds.FabricWildsService;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsActionResult;

public final class FabricRealmRuntime implements RealmsCommandRuntime {
    private final MinecraftServer server;
    private final PersistentRealmRepository repository;
    private final RealmsRuntime common;
    private final FabricServerPlatformAdapter serverPlatform;
    private final RealmSessionBypass bypass;
    private final FabricProtectionService protection;
    private final RealmPresenceService presence;
    private final RealmsConfig config;
    private final FabricPresetCatalogManager presets;
    private final FabricWildsService wilds;

    private FabricRealmRuntime(
            MinecraftServer server,
            PersistentRealmRepository repository,
            RealmAllocator allocator,
            RealmsConfig config,
            FabricPresetCatalogManager presets,
            FabricPermissionGate permissions,
            CommandMessenger messages) {
        this.server = server;
        this.repository = repository;
        this.serverPlatform = new FabricServerPlatformAdapter(server);
        this.config = config;
        this.presets = presets;
        this.bypass = new RealmSessionBypass();
        this.protection = new FabricProtectionService(
                bypass,
                config.denialMessageCooldownMillis());
        this.common = new RealmsRuntime(
                repository, allocator, presets::catalog, () -> presets.snapshot().selection(),
                new FabricRealmPresetGenerator(server, presets, serverPlatform.chunks()),
                serverPlatform,
                new MembershipLimits(
                        Duration.ofMinutes(config.membershipInviteExpiryMinutes()),
                        config.maximumMembersPerRealm(),
                        config.maximumPendingInvitesPerRealm()),
                Clock.systemUTC(), UUID::randomUUID,
                new RealmsRuntimeHooks() {
                    @Override public void realmIndexChanged() { refreshProtectionIndex(); }
                    @Override public void revalidateRealmPresence(eu.avalanche7.paradigmrealms.domain.RealmId id) {
                        FabricRealmRuntime.this.revalidateRealmPresence(id);
                    }
                });
        this.presence = new RealmPresenceService(server, this, protection, common.teleports());
        FabricProtectionHooks.install(protection);
        this.wilds = new FabricWildsService(server, this, config.wilds(), permissions, messages);
        this.protection.installWilds(wilds);
    }

    public static FabricRealmRuntime start(
            MinecraftServer server, RealmsConfig config, FabricPresetCatalogManager presets,
            FabricPermissionGate permissions, CommandMessenger messages) {
        RealmAllocator allocator = new RealmAllocator();
        FabricRealmStateStore store = new FabricRealmStateStore(
                server.getOverworld().getPersistentStateManager());
        return new FabricRealmRuntime(
                server, new PersistentRealmRepository(store, allocator), allocator, config, presets,
                permissions, messages);
    }

    public RealmInspectionService inspection() {
        return common.inspection();
    }

    @Override public List<Realm> inspectRealms() { return common.inspection().list(); }
    @Override public Optional<Realm> inspectRealm(eu.avalanche7.paradigmrealms.domain.RealmId id) {
        return common.inspection().findById(id);
    }
    @Override public Optional<Realm> inspectRealmOwner(UUID owner) {
        return common.inspection().findByOwner(owner);
    }

    public RealmMemberInspectionService.Decision inspectMembers(UUID actor, Optional<UUID> requestedOwner) {
        return common.inspectMembers(actor, requestedOwner);
    }

    public RealmVisitService.Decision evaluateVisit(UUID visitor, UUID owner) {
        return common.evaluateVisit(visitor, owner, bypass.enabled(visitor));
    }

    public AllocationPreviewService allocationPreview() {
        return common.allocationPreview();
    }

    @Override public eu.avalanche7.paradigmrealms.allocation.RealmAllocation previewAllocation(
            eu.avalanche7.paradigmrealms.domain.RealmId id) {
        return common.allocationPreview().preview(id);
    }

    public ValidationReport validate() {
        Set<DimensionId> dimensions = server.getWorldRegistryKeys().stream()
                .map(key -> DimensionId.parse(key.getValue().toString()))
                .collect(Collectors.toUnmodifiableSet());
        return common.validate(dimensions).plus(presetValidationIssues());
    }

    @Override public ValidationReport validateRealms() { return validate(); }

    public PersistentRealmRepository repository() {
        return repository;
    }

    public Realm createRealm(UUID owner, RealmPresetId preset) {
        return common.createRealm(owner, preset);
    }

    public Realm createRealm(UUID owner, RealmPresetDefinition preset) {
        return common.createRealm(owner, preset);
    }

    public FabricPresetCatalogManager.Snapshot presetSnapshot() { return presets.snapshot(); }

    @Override
    public PresetCatalogSnapshot presetCatalogSnapshot() {
        FabricPresetCatalogManager.Snapshot snapshot = presets.snapshot();
        return new PresetCatalogSnapshot(
                snapshot.catalog(), snapshot.selection(), snapshot.allowExternalPresets(),
                snapshot.compiledImports().keySet(), snapshot.loadedAt());
    }

    @Override
    public eu.avalanche7.paradigmrealms.generation.PresetSelectionConfig presetSelection() {
        return presets.snapshot().selection();
    }

    @Override
    public boolean presetAvailable(RealmPresetId preset) {
        return presets.snapshot().catalog().resolve(preset).isPresent();
    }

    public FabricPresetCatalogManager presetManager() { return presets; }

    public PresetSelectionResult selectPreset(Optional<RealmPresetId> requested) {
        return common.selectPreset(requested);
    }

    public List<RealmPresetDefinition> selectablePresets() {
        return common.selectablePresets();
    }

    public List<ValidationIssue> presetValidationIssues() {
        FabricPresetCatalogManager.Snapshot snapshot = presets.snapshot();
        List<ValidationIssue> issues = new ArrayList<>();
        snapshot.catalog().loadIssues().forEach(issue -> issues.add(ValidationIssue.warning(
                "PRESET_RESOURCE_INVALID", "presets.resources", issue)));
        snapshot.catalog().all().stream().filter(preset -> !preset.enabled()).forEach(preset ->
                preset.disableReasons().forEach(reason -> issues.add(ValidationIssue.warning(
                        "PRESET_DISABLED", "presets." + preset.id().value(), reason))));
        PresetSelectionResult defaultResult = common.selectPreset(Optional.empty());
        if (!defaultResult.selected()) issues.add(ValidationIssue.error(
                "DEFAULT_PRESET_INVALID", "presets.default", defaultResult.detail()));
        snapshot.selection().allowedPresets().stream()
                .filter(id -> snapshot.catalog().resolve(id).isEmpty())
                .forEach(id -> issues.add(ValidationIssue.warning(
                        "ALLOWED_PRESET_MISSING", "presets.allowed", id.value())));
        repository.list().stream()
                .filter(realm -> snapshot.catalog().resolve(realm.preset()).isEmpty())
                .forEach(realm -> issues.add(ValidationIssue.warning(
                        "REALM_PRESET_MISSING", "realms." + realm.id().value() + ".preset",
                        realm.preset().value() + " is unavailable; the existing realm remains usable")));
        ValidationIssue voidIssue = validateVoidDimension();
        if (voidIssue != null) issues.add(voidIssue);
        return List.copyOf(issues);
    }

    private ValidationIssue validateVoidDimension() {
        RegistryKey<World> realmsKey = RegistryKey.of(
                RegistryKeys.WORLD, Identifier.of("paradigm_realms", "realms"));
        var world = server.getWorld(realmsKey);
        String path = "dimension." + DimensionId.REALMS;
        if (world == null) return ValidationIssue.error(
                "REALMS_DIMENSION_MISSING", path, "Realms dimension is not loaded");
        if (!(world.getChunkManager().getChunkGenerator() instanceof FlatChunkGenerator generator)) {
            return ValidationIssue.error("REALMS_NOT_VOID", path,
                    "chunk generator is not the verified vanilla flat generator");
        }
        var definition = generator.getConfig();
        boolean emptyLayers = definition.getLayers().isEmpty()
                && definition.getLayerBlocks().isEmpty();
        boolean noStructures = definition.getStructureOverrides()
                .map(entries -> entries.stream().findAny().isEmpty()).orElse(true);
        boolean voidBiome = definition.getBiome().matchesKey(BiomeKeys.THE_VOID);
        boolean noFeatures = definition.createGenerationSettings(definition.getBiome()).getFeatures().stream()
                .allMatch(entries -> entries.stream().findAny().isEmpty());
        return emptyLayers && noStructures && voidBiome && noFeatures ? null
                : ValidationIssue.error("REALMS_NOT_VOID", path,
                        "expected empty layers, no structures/features, and minecraft:the_void biome");
    }

    public CompletableFuture<FabricPresetCatalogManager.Snapshot> reloadPresets() {
        presets.updateConfiguration(RealmsConfigLoader.load());
        return server.reloadResources(server.getDataPackManager().getEnabledIds())
                .thenApply(ignored -> presets.snapshot());
    }

    @Override
    public CompletionStage<PresetCatalogSnapshot> reloadPresetCatalog() {
        return reloadPresets().thenApply(ignored -> presetCatalogSnapshot());
    }

    @Override public List<String> presetImportFiles() { return presets.importFiles(); }
    @Override public Map<RealmPresetId, String> presetImportBindings() throws java.io.IOException {
        return presets.importBindings();
    }
    @Override public PresetImportResult inspectPresetImport(String sourceFile) {
        return presets.inspectImport(sourceFile);
    }
    @Override public PresetImportResult importPreset(String sourceFile, RealmPresetId presetId) {
        return presets.importPreset(sourceFile, presetId);
    }
    @Override public PresetImportResult removePresetImport(RealmPresetId presetId) {
        return presets.removeImport(presetId);
    }
    @Override public PresetImportResult reimportPreset(RealmPresetId presetId) {
        return presets.reimport(presetId);
    }

    public List<Realm> recoverInterruptedCreations() {
        return common.recoverInterruptedCreations();
    }

    public TeleportResult teleportHome(ServerPlayerEntity player, Realm realm) {
        return common.teleports().teleportToRealm(player.getUuid(), realm);
    }

    @Override
    public TeleportResult teleportHome(UUID player, Realm realm) {
        return common.teleports().teleportToRealm(player, realm);
    }

    public TeleportResult visit(ServerPlayerEntity player, Realm realm) {
        return common.teleports().teleportToRealm(player.getUuid(), realm);
    }

    @Override
    public TeleportResult visit(UUID player, Realm realm) {
        return common.teleports().teleportToRealm(player, realm);
    }

    @Override public List<Realm> realms() { return repository.list(); }
    @Override public List<RealmInvitation> realmInvitations() { return repository.listInvitations(); }

    public RealmMembershipService membership() {
        return common.membership();
    }

    public MembershipResult invite(UUID owner, String ownerName, UUID target, String targetName) {
        return common.invite(owner, ownerName, target, targetName);
    }

    public MembershipResult accept(UUID player, UUID owner) {
        return common.accept(player, owner);
    }

    public MembershipResult decline(UUID player, UUID owner) {
        return common.decline(player, owner);
    }

    public MembershipResult remove(UUID owner, UUID target) {
        return common.remove(owner, target);
    }

    public MembershipResult leave(UUID member, UUID owner) {
        return common.leave(member, owner);
    }

    public MembershipResult setAccess(UUID owner, RealmAccessPolicy policy) {
        return common.setAccess(owner, policy);
    }

    public List<RealmInvitation> invitationsFor(UUID player) {
        return common.invitationsFor(player);
    }

    public FabricProtectionService protection() {
        return protection;
    }

    public RealmSessionBypass bypass() {
        return bypass;
    }

    @Override public void enableSessionBypass(UUID player) { bypass.enable(player); }
    @Override public void disableSessionBypass(UUID player) { bypass.disable(player); }
    @Override public boolean sessionBypassEnabled(UUID player) { return bypass.enabled(player); }

    public RealmPresenceService presence() {
        return presence;
    }

    public RealmsConfig config() {
        return config;
    }

    public RealmTeleportService teleports() { return common.teleports(); }
    public FabricServerPlatformAdapter serverPlatform() { return serverPlatform; }

    public FabricWildsService wilds() { return wilds; }

    @Override public eu.avalanche7.paradigmrealms.wilds.WildsState wildsState() { return wilds.state(); }
    @Override public Duration wildsCooldownRemaining(UUID player) { return wilds.cooldownRemaining(player); }
    @Override public WildsActionResult enterWilds(UUID player) {
        ServerPlayerEntity online = online(player);
        return online == null ? WildsActionResult.WORLD_UNAVAILABLE : wilds.enter(online);
    }
    @Override public WildsActionResult teleportWildsSpawn(UUID player) {
        ServerPlayerEntity online = online(player);
        return online == null ? WildsActionResult.WORLD_UNAVAILABLE : wilds.teleportSpawn(online);
    }
    @Override public WildsActionResult requestWildsRtp(UUID player) {
        ServerPlayerEntity online = online(player);
        return online == null ? WildsActionResult.WORLD_UNAVAILABLE : wilds.requestRtp(online);
    }
    @Override public WildsActionResult setWildsSpawn(UUID player) {
        ServerPlayerEntity online = online(player);
        return online == null ? WildsActionResult.WORLD_UNAVAILABLE : wilds.setSpawn(online);
    }
    @Override public WildsActionResult openWildsEntry() { return wilds.openEntry(); }
    @Override public WildsActionResult closeWildsEntry() { return wilds.closeEntry(); }
    @Override public WildsActionResult scheduleWildsReset(java.time.Instant when) { return wilds.scheduleReset(when); }
    @Override public WildsActionResult cancelWildsReset() { return wilds.cancelReset(); }
    @Override public WildsActionResult prepareWildsReset() { return wilds.prepareReset(); }
    @Override public WildsActionResult retryWildsVerification() { return wilds.retryVerification(); }
    @Override public void reloadWildsConfig() { wilds.updateConfig(RealmsConfigLoader.load().wilds()); }
    @Override public List<String> wildsValidationIssues() { return wilds.validationIssues(); }
    @Override public String wildsTerrainSample(int centerX, int centerZ) {
        return wilds.terrainSample(centerX, centerZ);
    }
    @Override public List<String> wildsBackups() { return wilds.backupList(); }
    @Override public int pruneWildsBackups() throws java.io.IOException { return wilds.pruneBackups(); }

    public void shutdown() {
        wilds.shutdown();
        bypass.clearAll();
        presence.clear();
        FabricProtectionHooks.clear();
    }

    private void refreshProtectionIndex() {
        protection.replaceIndex(RealmRegionIndex.from(repository.list()));
    }

    private void revalidateRealmPresence(eu.avalanche7.paradigmrealms.domain.RealmId realmId) {
        presence.revalidateRealm(realmId);
    }

    private ServerPlayerEntity online(UUID player) {
        return server.getPlayerManager().getPlayer(player);
    }

    @Override
    public SetSpawnResult setSpawn(UUID player) {
        return common.spawns().setSpawn(player);
    }

    @Override
    public Optional<Realm> findRealmByOwner(UUID owner) {
        return common.inspection().findByOwner(owner);
    }
}
