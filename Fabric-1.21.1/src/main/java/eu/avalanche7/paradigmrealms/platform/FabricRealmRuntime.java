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
import java.util.concurrent.atomic.AtomicReference;
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
    private final eu.avalanche7.paradigmrealms.operations.OperationalAuditSink audit;
    private boolean lifecycleRecoveryPending;

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
        this.audit = createAudit(server, config.auditRetentionDays());
        this.bypass = new RealmSessionBypass();
        this.protection = new FabricProtectionService(
                bypass,
                config.denialMessageCooldownMillis(),
                config.realmSettings());
        AtomicReference<RealmPresenceService> lifecyclePresence = new AtomicReference<>();
        this.common = new RealmsRuntime(
                repository, allocator, presets::catalog, () -> presets.snapshot().selection(),
                new FabricRealmPresetGenerator(server, presets, serverPlatform.chunks()),
                serverPlatform,
                new MembershipLimits(
                        Duration.ofMinutes(config.membershipInviteExpiryMinutes()),
                        config.maximumMembersPerRealm(),
                        config.maximumPendingInvitesPerRealm()),
                Clock.systemUTC(), UUID::randomUUID,
                config.realmSettings(),
                source -> {
                    RealmPresenceService service = lifecyclePresence.get();
                    return service == null
                            ? eu.avalanche7.paradigmrealms.application.RealmLifecycleEffects.EvacuationResult.FAILED
                            : service.evacuateAndVerify(source);
                },
                Duration.ofMinutes(config.ownershipTransferExpiryMinutes()),
                config.previousOwnerRoleAfterTransfer(),
                new RealmsRuntimeHooks() {
                    @Override public void realmIndexChanged() { refreshProtectionIndex(); }
                    @Override public void revalidateRealmPresence(eu.avalanche7.paradigmrealms.domain.RealmId id) {
                        FabricRealmRuntime.this.revalidateRealmPresence(id);
                    }
                });
        this.presence = new RealmPresenceService(server, this, protection, common.teleports());
        lifecyclePresence.set(this.presence);
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
        audit("REALM_CREATE_REQUESTED", "REQUESTED", owner, Optional.empty(), false);
        Realm realm = common.createRealm(owner, preset);
        audit("REALM_CREATE_" + (realm.state() == eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ACTIVE
                        ? "COMPLETED" : "FAILED"),
                realm.state().name(), owner, Optional.of(realm.id()), true);
        return realm;
    }

    public Realm createRealm(UUID owner, RealmPresetDefinition preset) {
        audit("REALM_CREATE_REQUESTED", "REQUESTED", owner, Optional.empty(), false);
        Realm realm = common.createRealm(owner, preset);
        audit("REALM_CREATE_" + (realm.state() == eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState.ACTIVE
                        ? "COMPLETED" : "FAILED"),
                realm.state().name(), owner, Optional.of(realm.id()), true);
        return realm;
    }

    @Override public Optional<String> requestResetConfirmation(UUID owner, RealmPresetId preset) {
        return common.requestResetConfirmation(owner, preset);
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result confirmReset(UUID owner, String token) {
        var result = lifecycleResult(common.confirmReset(owner, token));
        audit("REALM_RESET", result.status().name(), owner, result.source().map(Realm::id), true);
        if (result.status()
                == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.RESET_COMPLETED) {
            result.replacement().ifPresent(realm -> common.teleports().teleportToRealm(owner, realm));
        }
        return result;
    }
    @Override public void cancelReset(UUID owner) { common.cancelReset(owner); }
    @Override public Optional<String> requestDeleteConfirmation(UUID owner) {
        return common.requestDeleteConfirmation(owner);
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result confirmDelete(UUID owner, String token) {
        var result = lifecycleResult(common.confirmDelete(owner, token));
        audit("REALM_DELETE_ARCHIVE", result.status().name(), owner, result.source().map(Realm::id), true);
        return result;
    }
    @Override public void cancelDelete(UUID owner) { common.cancelDelete(owner); }

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

    public List<eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result>
            recoverInterruptedLifecycle() {
        var results = common.recoverInterruptedLifecycle();
        results.stream().filter(result -> result.status()
                        == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.RESET_COMPLETED)
                .forEach(result -> result.replacement().ifPresent(realm ->
                        common.teleports().teleportToRealm(realm.owner().uuid(), realm)));
        lifecycleRecoveryPending = results.stream().anyMatch(result ->
                result.status() == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.EVACUATION_PENDING);
        return results;
    }

    public void tickLifecycle() {
        if (!lifecycleRecoveryPending) return;
        recoverInterruptedLifecycle();
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
        ServerPlayerEntity online = online(player);
        if (online != null) presence.rememberReturn(online);
        return common.teleports().teleportToRealm(player, realm);
    }

    @Override public TeleportResult leaveForeignRealm(UUID player) {
        return presence.leaveForeignRealm(player);
    }

    @Override public List<eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.Occupant>
            realmOccupants(UUID actor) {
        return presence.occupantsFor(actor);
    }

    public Optional<Realm> commonManagedRealm(UUID actor) {
        return common.ownerManagement().managedRealm(actor);
    }

    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Result offerTransfer(
            UUID owner, String ownerName, UUID target, String targetName) {
        var result = common.ownershipTransfers().offer(owner, ownerName, target, targetName);
        audit("REALM_TRANSFER_OFFERED", result.status().name(), owner,
                result.realm().map(Realm::id), true);
        return result;
    }

    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Result acceptTransfer(
            UUID target, UUID owner) {
        var result = common.ownershipTransfers().accept(target, owner);
        audit("REALM_TRANSFER_ACCEPTED", result.status().name(), target,
                result.realm().map(Realm::id), true);
        if (result.status() == eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Status.COMPLETED) {
            refreshProtectionIndex();
            result.realm().ifPresent(realm -> presence.revalidateRealm(realm.id()));
        }
        return result;
    }

    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Result declineTransfer(
            UUID target, UUID owner) {
        return common.ownershipTransfers().decline(target, owner);
    }

    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService.Result cancelTransfer(
            UUID owner) {
        return common.ownershipTransfers().cancel(owner);
    }

    @Override public List<Realm> realms() { return repository.list(); }
    @Override public List<RealmInvitation> realmInvitations() { return repository.listInvitations(); }

    public RealmMembershipService membership() {
        return common.membership();
    }

    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result setRealmName(
            UUID actor, String name) { return common.setRealmName(actor, name); }
    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result setRealmDescription(
            UUID actor, String description) { return common.setRealmDescription(actor, description); }
    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result setRealmListed(
            UUID actor, boolean listed) { return common.setRealmListed(actor, listed); }
    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result setRealmRole(
            UUID actor, UUID target, eu.avalanche7.paradigmrealms.domain.realm.RealmMemberRole role) {
        return common.setRealmRole(actor, target, role);
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result banFromRealm(
            UUID actor, UUID target, String targetName, Optional<String> reason) {
        var result = common.banFromRealm(actor, target, targetName, reason);
        if (result.succeeded()) audit("REALM_PLAYER_BANNED", "COMPLETED", actor,
                result.realm().map(Realm::id), false);
        if (result.succeeded()) {
            ServerPlayerEntity targetPlayer = online(target);
            if (targetPlayer != null) presence.validate(targetPlayer);
        }
        return result;
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result unbanFromRealm(
            UUID actor, UUID target) {
        var result = common.unbanFromRealm(actor, target);
        if (result.succeeded()) audit("REALM_PLAYER_UNBANNED", "COMPLETED", actor,
                result.realm().map(Realm::id), false);
        return result;
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService.Result setRealmSetting(
            UUID actor, eu.avalanche7.paradigmrealms.domain.realm.RealmSetting setting, boolean value) {
        return common.setRealmSetting(actor, setting, value);
    }
    @Override public Optional<Realm> managedRealm(UUID actor) { return common.ownerManagement().managedRealm(actor); }
    @Override public Optional<Realm> realmById(eu.avalanche7.paradigmrealms.domain.RealmId id) {
        return repository.findById(id);
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmDirectoryService.Page publicRealms(int page) {
        return common.directory().page(page);
    }
    @Override public eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult kickFromRealm(
            UUID actor, UUID target) {
        Realm realm = common.ownerManagement().managedRealm(actor).orElse(null);
        if (realm == null) return eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult.NO_REALM;
        if (target.equals(realm.owner().uuid())
                || (realm.managers().contains(actor) && realm.managers().contains(target))) {
            return eu.avalanche7.paradigmrealms.modules.command.RealmOwnerCommandRuntime.KickResult.FORBIDDEN_TARGET;
        }
        return presence.kick(realm, target);
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

    @Override public void enableSessionBypass(UUID player) {
        bypass.enable(player);
        audit("ADMIN_BYPASS_ENABLED", "COMPLETED", player, Optional.empty(), false);
    }
    @Override public void disableSessionBypass(UUID player) {
        bypass.disable(player);
        audit("ADMIN_BYPASS_DISABLED", "COMPLETED", player, Optional.empty(), false);
    }
    @Override public boolean sessionBypassEnabled(UUID player) { return bypass.enabled(player); }
    @Override public eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result restoreArchive(
            eu.avalanche7.paradigmrealms.domain.RealmId id) {
        var result = common.lifecycleManagement().restore(id);
        audit("REALM_ARCHIVE_RESTORE", result.status().name(), null, Optional.of(id), true);
        return result;
    }
    @Override public eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result retryRealmOperation(
            eu.avalanche7.paradigmrealms.domain.RealmId id) {
        return lifecycleResult(common.lifecycleManagement().retry(id));
    }

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

    @Override public List<String> repairPreview() {
        ArrayList<String> actions = new ArrayList<>();
        actions.add(validate().isValid()
                ? "indexes: rebuild region and owner-derived indexes from validated authoritative records"
                : "indexes: blocked because authoritative validation has errors");
        actions.add("stale-sessions: remove disconnected presence and bypass session entries");
        actions.add("expired-operations: remove expired ownership transfer offers and in-memory confirmations");
        return List.copyOf(actions);
    }

    @Override public boolean repairIndexes() {
        if (!validate().isValid()) return false;
        refreshProtectionIndex();
        audit("REPAIR_INDEXES", "COMPLETED", null, Optional.empty(), true);
        return true;
    }

    @Override public int repairStaleSessions() {
        int removed = presence.pruneStaleSessions();
        Set<UUID> online = server.getPlayerManager().getPlayerList().stream()
                .map(ServerPlayerEntity::getUuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
        int bypassBefore = bypass.size();
        inspectBypassSessions().stream().filter(player -> !online.contains(player)).forEach(bypass::disable);
        int total = removed + bypassBefore - bypass.size();
        audit("REPAIR_STALE_SESSIONS", "COMPLETED", null, Optional.empty(), false);
        return total;
    }

    @Override public int repairExpiredOperations() {
        int removed = common.ownershipTransfers().cleanupExpired();
        audit("REPAIR_EXPIRED_OPERATIONS", "COMPLETED", null, Optional.empty(), false);
        return removed;
    }

    @Override public Optional<String> exportSupportBundle() {
        try {
            return Optional.of(eu.avalanche7.paradigmrealms.platform.operations.FabricSupportExport.export(
                    server, this));
        } catch (java.io.IOException exception) {
            eu.avalanche7.paradigmrealms.ParadigmRealms.LOGGER.error(
                    "Support export failed: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override public eu.avalanche7.paradigmrealms.operations.ConfigCommandResult validateConfig() {
        var validation = RealmsConfigLoader.validate();
        if (validation.config().isEmpty()) {
            return new eu.avalanche7.paradigmrealms.operations.ConfigCommandResult(
                    false, List.of(), List.of(), List.of(),
                    List.of(validation.error().orElse("unknown configuration error")));
        }
        return new eu.avalanche7.paradigmrealms.operations.ConfigCommandResult(
                true, List.of("configuration parsed and validated"), List.of(), List.of(), List.of());
    }

    @Override public eu.avalanche7.paradigmrealms.operations.ConfigCommandResult reloadConfig() {
        var validation = RealmsConfigLoader.validate();
        if (validation.config().isEmpty()) {
            return new eu.avalanche7.paradigmrealms.operations.ConfigCommandResult(
                    false, List.of(), List.of(), List.of(),
                    List.of(validation.error().orElse("unknown configuration error")));
        }
        RealmsConfig candidate = validation.config().orElseThrow();
        if (candidate.equals(config)) {
            return new eu.avalanche7.paradigmrealms.operations.ConfigCommandResult(
                    true, List.of("configuration unchanged"), List.of(), List.of(), List.of());
        }
        return new eu.avalanche7.paradigmrealms.operations.ConfigCommandResult(
                true, List.of(), List.of(),
                List.of("changed realm, protection, preset, audit and Wilds settings require a server restart"),
                List.of());
    }

    private Set<UUID> inspectBypassSessions() {
        return bypass.snapshot();
    }

    public List<eu.avalanche7.paradigmrealms.operations.OperationalAuditEvent> recentAuditEvents() {
        return audit instanceof eu.avalanche7.paradigmrealms.platform.operations.FabricOperationalAuditLog log
                ? log.recent() : List.of();
    }

    public void shutdown() {
        wilds.shutdown();
        bypass.clearAll();
        presence.clear();
        FabricProtectionHooks.clear();
        audit.close();
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

    private void audit(
            String type, String outcome, UUID actor,
            Optional<eu.avalanche7.paradigmrealms.domain.RealmId> realm, boolean durable) {
        audit.append(eu.avalanche7.paradigmrealms.operations.OperationalAuditEvent.simple(
                java.time.Instant.now(), type, outcome, Optional.ofNullable(actor), realm), durable);
    }

    private static eu.avalanche7.paradigmrealms.operations.OperationalAuditSink createAudit(
            MinecraftServer server, int retentionDays) {
        try {
            return new eu.avalanche7.paradigmrealms.platform.operations.FabricOperationalAuditLog(
                    server.getRunDirectory(), retentionDays);
        } catch (java.io.IOException exception) {
            eu.avalanche7.paradigmrealms.ParadigmRealms.LOGGER.error(
                    "Operational audit log unavailable: {}", exception.getMessage());
            return eu.avalanche7.paradigmrealms.operations.OperationalAuditSink.disabled();
        }
    }

    private eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result lifecycleResult(
            eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Result result) {
        lifecycleRecoveryPending = result.status()
                == eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService.Status.EVACUATION_PENDING;
        return result;
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
