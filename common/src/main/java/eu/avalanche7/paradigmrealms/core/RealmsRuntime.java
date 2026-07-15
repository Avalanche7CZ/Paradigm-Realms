package eu.avalanche7.paradigmrealms.core;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.application.AllocationPreviewService;
import eu.avalanche7.paradigmrealms.application.RealmInspectionService;
import eu.avalanche7.paradigmrealms.application.RealmMemberInspectionService;
import eu.avalanche7.paradigmrealms.application.RealmSpawnService;
import eu.avalanche7.paradigmrealms.application.RealmTeleportService;
import eu.avalanche7.paradigmrealms.application.RealmVisitService;
import eu.avalanche7.paradigmrealms.application.StartupValidationService;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.generation.PresetPlacementPlanner;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionConfig;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionResult;
import eu.avalanche7.paradigmrealms.generation.PresetSelectionService;
import eu.avalanche7.paradigmrealms.generation.RealmCreationService;
import eu.avalanche7.paradigmrealms.generation.RealmGenerationPort;
import eu.avalanche7.paradigmrealms.generation.RealmPresetCatalog;
import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.membership.MembershipLimits;
import eu.avalanche7.paradigmrealms.membership.MembershipResult;
import eu.avalanche7.paradigmrealms.membership.RealmInvitation;
import eu.avalanche7.paradigmrealms.membership.RealmMembershipService;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;
import eu.avalanche7.paradigmrealms.persistence.validation.ValidationReport;
import eu.avalanche7.paradigmrealms.platform.RealmsServerPlatformAdapter;

public final class RealmsRuntime {
    private final RealmRepository repository;
    private final RealmInspectionService inspection;
    private final RealmMemberInspectionService memberInspection;
    private final RealmVisitService visits;
    private final AllocationPreviewService allocationPreview;
    private final StartupValidationService startupValidation;
    private final RealmCreationService creation;
    private final RealmMembershipService membership;
    private final RealmTeleportService teleports;
    private final RealmSpawnService spawns;
    private final Supplier<RealmPresetCatalog> presetCatalog;
    private final Supplier<PresetSelectionConfig> presetSelection;
    private final PresetSelectionService selections = new PresetSelectionService();
    private final RealmsRuntimeHooks hooks;

    public RealmsRuntime(
            RealmRepository repository,
            RealmAllocator allocator,
            Supplier<RealmPresetCatalog> presetCatalog,
            Supplier<PresetSelectionConfig> presetSelection,
            RealmGenerationPort generation,
            RealmsServerPlatformAdapter serverPlatform,
            MembershipLimits membershipLimits,
            Clock clock,
            Supplier<UUID> operationIds,
            RealmsRuntimeHooks hooks) {
        this.repository = Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(allocator, "allocator");
        this.presetCatalog = Objects.requireNonNull(presetCatalog, "presetCatalog");
        this.presetSelection = Objects.requireNonNull(presetSelection, "presetSelection");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.inspection = new RealmInspectionService(repository);
        this.memberInspection = new RealmMemberInspectionService(repository);
        this.visits = new RealmVisitService(repository);
        this.allocationPreview = new AllocationPreviewService(allocator);
        this.startupValidation = new StartupValidationService(repository);
        this.creation = new RealmCreationService(
                repository, allocator, presetCatalog, new PresetPlacementPlanner(),
                Objects.requireNonNull(generation, "generation"), Objects.requireNonNull(clock, "clock"),
                Objects.requireNonNull(operationIds, "operationIds"));
        this.teleports = new RealmTeleportService(
                Objects.requireNonNull(serverPlatform, "serverPlatform"));
        this.spawns = new RealmSpawnService(repository, teleports, serverPlatform.players());
        this.membership = new RealmMembershipService(
                repository, Objects.requireNonNull(membershipLimits, "membershipLimits"), clock);
        hooks.realmIndexChanged();
    }

    public RealmInspectionService inspection() { return inspection; }

    public RealmMemberInspectionService.Decision inspectMembers(UUID actor, Optional<UUID> requestedOwner) {
        return memberInspection.inspect(actor, requestedOwner);
    }

    public RealmVisitService.Decision evaluateVisit(UUID visitor, UUID owner, boolean adminBypass) {
        return visits.evaluate(visitor, owner, adminBypass);
    }

    public AllocationPreviewService allocationPreview() { return allocationPreview; }

    public ValidationReport validate(Set<DimensionId> dimensions) {
        return startupValidation.validate(dimensions);
    }

    public RealmRepository repository() { return repository; }

    public Realm createRealm(UUID owner, RealmPresetId preset) {
        Realm realm = creation.create(owner, preset);
        hooks.realmIndexChanged();
        return realm;
    }

    public Realm createRealm(UUID owner, RealmPresetDefinition preset) {
        Realm realm = creation.create(owner, preset);
        hooks.realmIndexChanged();
        return realm;
    }

    public PresetSelectionResult selectPreset(Optional<RealmPresetId> requested) {
        return selections.select(presetCatalog.get(), presetSelection.get(), requested);
    }

    public List<RealmPresetDefinition> selectablePresets() {
        return selections.selectable(presetCatalog.get(), presetSelection.get());
    }

    public List<Realm> recoverInterruptedCreations() {
        List<Realm> recovered = creation.recoverInterrupted();
        hooks.realmIndexChanged();
        return recovered;
    }

    public RealmMembershipService membership() { return membership; }
    public RealmTeleportService teleports() { return teleports; }
    public RealmSpawnService spawns() { return spawns; }

    public MembershipResult invite(UUID owner, String ownerName, UUID target, String targetName) {
        MembershipResult result = membership.invite(owner, ownerName, target, targetName);
        hooks.realmIndexChanged();
        return result;
    }

    public MembershipResult accept(UUID player, UUID owner) {
        MembershipResult result = membership.accept(player, owner);
        hooks.realmIndexChanged();
        return result;
    }

    public MembershipResult decline(UUID player, UUID owner) {
        MembershipResult result = membership.decline(player, owner);
        hooks.realmIndexChanged();
        return result;
    }

    public MembershipResult remove(UUID owner, UUID target) {
        MembershipResult result = membership.remove(owner, target);
        afterMembershipChange(result, true);
        return result;
    }

    public MembershipResult leave(UUID member, UUID owner) {
        MembershipResult result = membership.leave(member, owner);
        afterMembershipChange(result, true);
        return result;
    }

    public MembershipResult setAccess(UUID owner, RealmAccessPolicy policy) {
        MembershipResult result = membership.setAccess(owner, policy);
        afterMembershipChange(result, true);
        return result;
    }

    public List<RealmInvitation> invitationsFor(UUID player) {
        return membership.pendingFor(player);
    }

    private void afterMembershipChange(MembershipResult result, boolean revalidatePresence) {
        hooks.realmIndexChanged();
        if (revalidatePresence) {
            result.realm().ifPresent(realm -> hooks.revalidateRealmPresence(realm.id()));
        }
    }
}
