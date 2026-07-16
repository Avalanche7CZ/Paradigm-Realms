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
import eu.avalanche7.paradigmrealms.application.RealmDirectoryService;
import eu.avalanche7.paradigmrealms.application.RealmOwnerManagementService;
import eu.avalanche7.paradigmrealms.application.RealmLifecycleManagementService;
import eu.avalanche7.paradigmrealms.application.RealmConfirmationService;
import eu.avalanche7.paradigmrealms.application.RealmLifecycleEffects;
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
import eu.avalanche7.paradigmrealms.config.RealmSettingsPolicy;
import eu.avalanche7.paradigmrealms.application.RealmOwnershipTransferService;
import eu.avalanche7.paradigmrealms.ownership.PreviousOwnerRole;

public final class RealmsRuntime {
    private final RealmRepository repository;
    private final RealmInspectionService inspection;
    private final RealmMemberInspectionService memberInspection;
    private final RealmVisitService visits;
    private final AllocationPreviewService allocationPreview;
    private final StartupValidationService startupValidation;
    private final RealmCreationService creation;
    private final RealmMembershipService membership;
    private final RealmOwnerManagementService ownerManagement;
    private final RealmDirectoryService directory;
    private final RealmLifecycleManagementService lifecycleManagement;
    private final RealmConfirmationService confirmations;
    private final RealmOwnershipTransferService ownershipTransfers;
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
        this(repository, allocator, presetCatalog, presetSelection, generation, serverPlatform,
                membershipLimits, clock, operationIds, RealmSettingsPolicy.secureDefaults(),
                RealmLifecycleEffects.immediate(), hooks);
    }

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
            RealmLifecycleEffects lifecycleEffects,
            RealmsRuntimeHooks hooks) {
        this(repository, allocator, presetCatalog, presetSelection, generation, serverPlatform,
                membershipLimits, clock, operationIds, RealmSettingsPolicy.secureDefaults(), lifecycleEffects, hooks);
    }

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
            RealmSettingsPolicy settingsPolicy,
            RealmLifecycleEffects lifecycleEffects,
            RealmsRuntimeHooks hooks) {
        this(repository, allocator, presetCatalog, presetSelection, generation, serverPlatform,
                membershipLimits, clock, operationIds, settingsPolicy, lifecycleEffects,
                java.time.Duration.ofMinutes(15), PreviousOwnerRole.MANAGER, hooks);
    }

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
            RealmSettingsPolicy settingsPolicy,
            RealmLifecycleEffects lifecycleEffects,
            java.time.Duration transferExpiry,
            PreviousOwnerRole previousOwnerRole,
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
                Objects.requireNonNull(operationIds, "operationIds"), settingsPolicy.defaults());
        this.teleports = new RealmTeleportService(
                Objects.requireNonNull(serverPlatform, "serverPlatform"));
        this.spawns = new RealmSpawnService(repository, teleports, serverPlatform.players());
        this.membership = new RealmMembershipService(
                repository, Objects.requireNonNull(membershipLimits, "membershipLimits"), clock);
        this.ownerManagement = new RealmOwnerManagementService(repository, clock, settingsPolicy);
        this.directory = new RealmDirectoryService(repository);
        this.lifecycleManagement = new RealmLifecycleManagementService(repository, allocator, presetCatalog,
                generation, lifecycleEffects, clock, operationIds, hooks::realmIndexChanged);
        this.confirmations = new RealmConfirmationService(clock);
        this.ownershipTransfers = new RealmOwnershipTransferService(
                repository, clock, operationIds, transferExpiry, previousOwnerRole);
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

    public List<RealmLifecycleManagementService.Result> recoverInterruptedLifecycle() {
        List<RealmLifecycleManagementService.Result> recovered = lifecycleManagement.recover();
        hooks.realmIndexChanged();
        return recovered;
    }

    public RealmMembershipService membership() { return membership; }
    public RealmOwnerManagementService ownerManagement() { return ownerManagement; }
    public RealmDirectoryService directory() { return directory; }

    public RealmOwnerManagementService.Result setRealmName(UUID actor, String name) {
        return ownerMutation(ownerManagement.setName(actor, name), false);
    }

    public RealmOwnerManagementService.Result setRealmDescription(UUID actor, String description) {
        return ownerMutation(ownerManagement.setDescription(actor, description), false);
    }

    public RealmOwnerManagementService.Result setRealmListed(UUID actor, boolean listed) {
        return ownerMutation(ownerManagement.setListed(actor, listed), false);
    }

    public RealmOwnerManagementService.Result setRealmRole(
            UUID actor, UUID target, eu.avalanche7.paradigmrealms.domain.realm.RealmMemberRole role) {
        return ownerMutation(ownerManagement.setRole(actor, target, role), true);
    }

    public RealmOwnerManagementService.Result banFromRealm(
            UUID actor, UUID target, String targetName, Optional<String> reason) {
        return ownerMutation(ownerManagement.ban(actor, target, targetName, reason), true);
    }

    public RealmOwnerManagementService.Result unbanFromRealm(UUID actor, UUID target) {
        return ownerMutation(ownerManagement.unban(actor, target), false);
    }

    public RealmOwnerManagementService.Result setRealmSetting(
            UUID actor, eu.avalanche7.paradigmrealms.domain.realm.RealmSetting setting, boolean value) {
        return ownerMutation(ownerManagement.setSetting(actor, setting, value), false);
    }

    private RealmOwnerManagementService.Result ownerMutation(
            RealmOwnerManagementService.Result result, boolean revalidate) {
        if (result.succeeded()) {
            hooks.realmIndexChanged();
            if (revalidate) result.realm().ifPresent(realm -> hooks.revalidateRealmPresence(realm.id()));
        }
        return result;
    }
    public RealmLifecycleManagementService lifecycleManagement() { return lifecycleManagement; }
    public RealmOwnershipTransferService ownershipTransfers() { return ownershipTransfers; }

    public Optional<String> requestResetConfirmation(UUID owner, RealmPresetId preset) {
        return repository.findByOwner(owner).filter(realm -> realm.lifecycleOperation().isEmpty())
                .map(realm -> confirmations.issue(owner, realm.id(), RealmConfirmationService.Kind.RESET,
                        Optional.of(preset.value())));
    }

    public RealmLifecycleManagementService.Result confirmReset(UUID owner, String token) {
        ResetConfirmation confirmation = consumeResetConfirmation(owner, token);
        if (!confirmation.valid()) {
            return confirmation.invalidResult();
        }
        return executeReset(owner, confirmation.preset().orElseThrow());
    }

    public ResetConfirmation consumeResetConfirmation(UUID owner, String token) {
        Realm realm = repository.findByOwner(owner).orElse(null);
        if (realm == null) {
            return ResetConfirmation.noRealm();
        }
        Optional<RealmConfirmationService.Confirmation> confirmation = confirmations.consume(
                owner,
                realm.id(),
                RealmConfirmationService.Kind.RESET,
                token);
        return confirmation
                .map(value -> new ResetConfirmation(
                        Optional.of(realm),
                        Optional.of(new RealmPresetId(value.preset().orElseThrow())),
                        true))
                .orElseGet(() -> new ResetConfirmation(Optional.of(realm), Optional.empty(), false));
    }

    public RealmLifecycleManagementService.Result executeReset(UUID owner, RealmPresetId preset) {
        return lifecycleManagement.reset(owner, preset);
    }

    public Optional<String> requestDeleteConfirmation(UUID owner) {
        return repository.findByOwner(owner).filter(realm -> realm.lifecycleOperation().isEmpty())
                .map(realm -> confirmations.issue(owner, realm.id(), RealmConfirmationService.Kind.DELETE, Optional.empty()));
    }

    public RealmLifecycleManagementService.Result confirmDelete(UUID owner, String token) {
        DeleteConfirmation confirmation = consumeDeleteConfirmation(owner, token);
        if (!confirmation.valid()) {
            return confirmation.invalidResult();
        }
        return executeDelete(owner);
    }

    public DeleteConfirmation consumeDeleteConfirmation(UUID owner, String token) {
        Realm realm = repository.findByOwner(owner).orElse(null);
        if (realm == null) {
            return DeleteConfirmation.noRealm();
        }
        boolean valid = confirmations.consume(
                owner,
                realm.id(),
                RealmConfirmationService.Kind.DELETE,
                token).isPresent();
        return new DeleteConfirmation(Optional.of(realm), valid);
    }

    public RealmLifecycleManagementService.Result executeDelete(UUID owner) {
        return lifecycleManagement.delete(owner);
    }

    public void cancelReset(UUID owner) {
        repository.findByOwner(owner).ifPresent(realm -> confirmations.cancel(owner, realm.id(), RealmConfirmationService.Kind.RESET));
    }

    public void cancelDelete(UUID owner) {
        repository.findByOwner(owner).ifPresent(realm -> confirmations.cancel(owner, realm.id(), RealmConfirmationService.Kind.DELETE));
    }
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

    public record ResetConfirmation(
            Optional<Realm> realm,
            Optional<RealmPresetId> preset,
            boolean valid) {
        static ResetConfirmation noRealm() {
            return new ResetConfirmation(Optional.empty(), Optional.empty(), false);
        }

        public RealmLifecycleManagementService.Result invalidResult() {
            if (realm.isEmpty()) {
                return RealmLifecycleManagementService.Result.noRealm();
            }
            return new RealmLifecycleManagementService.Result(
                    RealmLifecycleManagementService.Status.CONFIRMATION_INVALID,
                    realm,
                    Optional.empty());
        }
    }

    public record DeleteConfirmation(Optional<Realm> realm, boolean valid) {
        static DeleteConfirmation noRealm() {
            return new DeleteConfirmation(Optional.empty(), false);
        }

        public RealmLifecycleManagementService.Result invalidResult() {
            if (realm.isEmpty()) {
                return RealmLifecycleManagementService.Result.noRealm();
            }
            return new RealmLifecycleManagementService.Result(
                    RealmLifecycleManagementService.Status.CONFIRMATION_INVALID,
                    realm,
                    Optional.empty());
        }
    }
}
