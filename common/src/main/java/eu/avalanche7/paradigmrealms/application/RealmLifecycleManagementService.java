package eu.avalanche7.paradigmrealms.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.RealmOwner;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmFailure;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperation;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperationKind;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleOperationStage;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.domain.realm.RealmOperation;
import eu.avalanche7.paradigmrealms.generation.PresetPlacementPlan;
import eu.avalanche7.paradigmrealms.generation.PresetPlacementPlanner;
import eu.avalanche7.paradigmrealms.generation.RealmGenerationPort;
import eu.avalanche7.paradigmrealms.generation.RealmPresetCatalog;
import eu.avalanche7.paradigmrealms.generation.RealmPresetDefinition;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmLifecycleManagementService {
    private final RealmRepository repository;
    private final RealmAllocator allocator;
    private final Supplier<RealmPresetCatalog> presets;
    private final RealmGenerationPort generation;
    private final RealmLifecycleEffects effects;
    private final Clock clock;
    private final Supplier<UUID> operationIds;
    private final Runnable indexesChanged;
    private final PresetPlacementPlanner planner = new PresetPlacementPlanner();

    public RealmLifecycleManagementService(
            RealmRepository repository, RealmAllocator allocator, Supplier<RealmPresetCatalog> presets,
            RealmGenerationPort generation, Clock clock, Supplier<UUID> operationIds) {
        this(repository, allocator, presets, generation, RealmLifecycleEffects.immediate(), clock,
                operationIds, () -> {});
    }

    public RealmLifecycleManagementService(
            RealmRepository repository, RealmAllocator allocator, Supplier<RealmPresetCatalog> presets,
            RealmGenerationPort generation, RealmLifecycleEffects effects, Clock clock,
            Supplier<UUID> operationIds, Runnable indexesChanged) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        this.presets = Objects.requireNonNull(presets, "presets");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
        this.indexesChanged = Objects.requireNonNull(indexesChanged, "indexesChanged");
    }

    public Result reset(UUID owner, RealmPresetId requestedPreset) {
        Realm source = repository.findByOwner(owner).orElse(null);
        if (source == null) return Result.noRealm();
        if (source.lifecycleOperation().isPresent()) return result(Status.OPERATION_IN_PROGRESS, source, null);
        RealmPresetDefinition preset = resolvePreset(requestedPreset);
        if (preset == null) return result(Status.PRESET_UNAVAILABLE, source, null);
        CreationTimestamp now = now();
        RealmLifecycleOperation operation = new RealmLifecycleOperation(operationIds.get(),
                RealmLifecycleOperationKind.RESET, RealmLifecycleOperationStage.REQUESTED,
                Optional.of(preset.id()), Optional.empty(), now, now);
        source = source.withLifecycleOperation(Optional.of(operation));
        repository.save(source);
        return executeReset(source, preset);
    }

    public Result delete(UUID owner) {
        Realm source = repository.findByOwner(owner).orElse(null);
        if (source == null) return Result.noRealm();
        if (source.lifecycleOperation().isPresent()) return result(Status.OPERATION_IN_PROGRESS, source, null);
        CreationTimestamp now = now();
        RealmLifecycleOperation operation = new RealmLifecycleOperation(operationIds.get(),
                RealmLifecycleOperationKind.DELETE, RealmLifecycleOperationStage.REQUESTED,
                Optional.empty(), Optional.empty(), now, now);
        source = source.withLifecycleOperation(Optional.of(operation));
        repository.save(source);
        return executeDelete(source);
    }

    public List<Result> recover() {
        List<Result> recovered = new ArrayList<>();
        for (Realm source : activeOperations()) {
            RealmLifecycleOperation operation = source.lifecycleOperation().orElseThrow();
            if (operation.stage() == RealmLifecycleOperationStage.FAILED) continue;
            if (operation.kind() == RealmLifecycleOperationKind.DELETE) {
                recovered.add(executeDelete(source));
            } else {
                RealmPresetDefinition preset = operation.requestedPreset().map(this::resolvePreset).orElse(null);
                recovered.add(preset == null
                        ? failSource(source, "RESET_PRESET_UNAVAILABLE", "captured reset preset is unavailable")
                        : executeReset(source, preset));
            }
        }
        return List.copyOf(recovered);
    }

    public Result retry(RealmId sourceId) {
        Realm source = repository.findById(sourceId).orElse(null);
        if (source == null || source.lifecycleOperation().isEmpty()) return Result.noRealm();
        RealmLifecycleOperation operation = source.lifecycleOperation().orElseThrow();
        if (operation.stage() != RealmLifecycleOperationStage.FAILED) {
            return result(Status.OPERATION_IN_PROGRESS, source, null);
        }
        RealmLifecycleOperation resumed = operation.withStage(
                operation.targetRealmId().isPresent() ? RealmLifecycleOperationStage.TARGET_RESERVED
                        : RealmLifecycleOperationStage.REQUESTED,
                now());
        source = source.withLifecycleOperation(Optional.of(resumed));
        repository.save(source);
        if (operation.kind() == RealmLifecycleOperationKind.DELETE) return executeDelete(source);
        RealmPresetDefinition preset = operation.requestedPreset().map(this::resolvePreset).orElse(null);
        return preset == null
                ? failSource(source, "RESET_PRESET_UNAVAILABLE", "captured reset preset is unavailable")
                : executeReset(source, preset);
    }

    public Result restore(RealmId realmId) {
        Realm archived = repository.findById(realmId).orElse(null);
        if (archived == null) return Result.noRealm();
        if (archived.state() != RealmLifecycleState.ARCHIVED) {
            return result(Status.NOT_ARCHIVED, archived, null);
        }
        boolean conflict = repository.findByOwner(archived.owner().uuid()).isPresent()
                || repository.list().stream()
                        .filter(realm -> realm.owner().equals(archived.owner()))
                        .filter(realm -> realm.lifecycleOperation().isPresent())
                        .anyMatch(realm -> !realm.id().equals(archived.id()));
        if (conflict) return result(Status.RESTORE_CONFLICT, archived, null);
        Realm restored = archived.restore();
        repository.save(restored);
        indexesChanged.run();
        return result(Status.RESTORED, restored, null);
    }

    private Result executeReset(Realm initialSource, RealmPresetDefinition preset) {
        Realm source = initialSource;
        try {
            RealmLifecycleOperation operation = source.lifecycleOperation().orElseThrow();
            Realm target;
            if (operation.targetRealmId().isEmpty()) {
                RealmId targetId = repository.allocateNextRealmId();
                PresetPlacementPlan plan = planner.plan(allocator.preview(targetId), preset);
                operation = operation.withTarget(targetId, RealmLifecycleOperationStage.TARGET_RESERVED, now());
                source = source.withLifecycleOperation(Optional.of(operation));
                target = replacement(source, targetId, preset, plan, operation);
                repository.save(target);
                repository.save(source);
            } else {
                target = repository.findById(operation.targetRealmId().orElseThrow()).orElse(null);
                if (target == null) return failSource(source, "RESET_TARGET_MISSING", "reserved reset target is absent");
            }
            if (target.state() == RealmLifecycleState.FAILED) {
                RealmOperation previous = target.operation().orElseThrow();
                target = target.withOperation(new RealmOperation(previous.operationId(), previous.presetRevision(),
                        Math.addExact(previous.attempt(), 1), now()));
                repository.save(target);
                target = repository.updateLifecycle(target.id(), RealmLifecycleState.GENERATING, Optional.empty());
            }
            if (target.state() == RealmLifecycleState.ALLOCATED || target.state() == RealmLifecycleState.GENERATING) {
                if (target.state() == RealmLifecycleState.ALLOCATED) {
                    target = repository.updateLifecycle(target.id(), RealmLifecycleState.GENERATING, Optional.empty());
                }
                source = advance(source, RealmLifecycleOperationStage.TARGET_GENERATING);
                generation.generate(target, planner.plan(target.allocation(), preset));
                target = repository.updateLifecycle(target.id(), RealmLifecycleState.READY, Optional.empty());
                source = advance(source, RealmLifecycleOperationStage.TARGET_ACTIVE);
            }
            if (target.state() != RealmLifecycleState.READY) {
                return failSource(source, "RESET_TARGET_NOT_READY", "replacement generation did not become ready");
            }
            source = blockEntry(source);
            RealmLifecycleEffects.EvacuationResult evacuation = effects.evacuateAndVerify(source);
            if (evacuation == RealmLifecycleEffects.EvacuationResult.RETRY) {
                return result(Status.EVACUATION_PENDING, source, target);
            }
            if (evacuation == RealmLifecycleEffects.EvacuationResult.FAILED) {
                return failSource(source, "RESET_EVACUATION_FAILED", "one or more occupants could not be evacuated");
            }
            source = advance(source, RealmLifecycleOperationStage.OCCUPANTS_EVACUATED);
            Realm active = target.withLifecycle(RealmLifecycleState.ACTIVE, Optional.empty());
            Realm archived = source.archive(now(), Optional.of(active.id()));
            repository.saveAll(List.of(archived, active), repository.listInvitations());
            indexesChanged.run();
            return result(Status.RESET_COMPLETED, archived, active);
        } catch (Exception exception) {
            return failGeneration(source, exception);
        }
    }

    private Result executeDelete(Realm initialSource) {
        Realm source = initialSource;
        try {
            source = blockEntry(source);
            RealmLifecycleEffects.EvacuationResult evacuation = effects.evacuateAndVerify(source);
            if (evacuation == RealmLifecycleEffects.EvacuationResult.RETRY) {
                return result(Status.EVACUATION_PENDING, source, null);
            }
            if (evacuation == RealmLifecycleEffects.EvacuationResult.FAILED) {
                return failSource(source, "DELETE_EVACUATION_FAILED", "one or more occupants could not be evacuated");
            }
            source = advance(source, RealmLifecycleOperationStage.OCCUPANTS_EVACUATED);
            Realm archived = source.archive(now(), Optional.empty());
            repository.save(archived);
            indexesChanged.run();
            return result(Status.ARCHIVED, archived, null);
        } catch (Exception exception) {
            return failSource(source, "DELETE_FAILED", detail(exception));
        }
    }

    private Realm replacement(Realm source, RealmId id, RealmPresetDefinition preset,
            PresetPlacementPlan plan, RealmLifecycleOperation lifecycle) {
        return new Realm(id, new RealmOwner(source.owner().uuid()), RealmLifecycleState.ALLOCATED,
                DimensionId.REALMS, allocator.preview(id), plan.spawn(), preset.id(), source.members(),
                source.invitedVisitors(), source.accessPolicy(), now(), SchemaVersion.CURRENT,
                Optional.of(new RealmOperation(lifecycle.operationId(), preset.revision(), 1, now())), Optional.empty())
                .withIdentity(source.displayName(), source.description(), source.listed())
                .withRoles(source.members(), source.managers()).withBans(source.bans())
                .withSettings(source.settings()).asReplacementOf(source.id());
    }

    private Realm blockEntry(Realm source) {
        if (source.lifecycleOperation().orElseThrow().stage() == RealmLifecycleOperationStage.ENTRY_BLOCKED
                || source.lifecycleOperation().orElseThrow().stage() == RealmLifecycleOperationStage.OCCUPANTS_EVACUATED) {
            return source;
        }
        Realm blocked = advance(source, RealmLifecycleOperationStage.ENTRY_BLOCKED);
        indexesChanged.run();
        return blocked;
    }

    private Realm advance(Realm source, RealmLifecycleOperationStage stage) {
        Realm updated = source.withLifecycleOperation(Optional.of(
                source.lifecycleOperation().orElseThrow().withStage(stage, now())));
        repository.save(updated);
        return updated;
    }

    private Result failGeneration(Realm source, Exception exception) {
        RealmLifecycleOperation operation = source.lifecycleOperation().orElse(null);
        if (operation != null && operation.targetRealmId().isPresent()) {
            repository.findById(operation.targetRealmId().orElseThrow()).ifPresent(target -> {
                if (target.state() == RealmLifecycleState.GENERATING || target.state() == RealmLifecycleState.READY) {
                    RealmOperation journal = target.operation().orElseThrow();
                    RealmFailure failure = new RealmFailure("RESET_GENERATION_FAILED", detail(exception), target.state(),
                            journal.operationId(), journal.attempt(), now());
                    repository.updateLifecycle(target.id(), RealmLifecycleState.FAILED, Optional.of(failure));
                }
            });
        }
        return failSource(source, "RESET_GENERATION_FAILED", detail(exception));
    }

    private Result failSource(Realm source, String code, String failureDetail) {
        try {
            Realm failed = source.withLifecycleOperation(Optional.of(
                    source.lifecycleOperation().orElseThrow().failed(code, failureDetail, now())));
            repository.save(failed);
            indexesChanged.run();
            return result(Status.RESET_FAILED_OLD_REALM_PRESERVED, failed, null);
        } catch (RuntimeException persistenceFailure) {
            return result(Status.RESET_FAILED_OLD_REALM_PRESERVED, source, null);
        }
    }

    private List<Realm> activeOperations() {
        return repository.list().stream()
                .filter(realm -> realm.state() == RealmLifecycleState.ACTIVE)
                .filter(realm -> realm.lifecycleOperation().isPresent())
                .toList();
    }

    private RealmPresetDefinition resolvePreset(RealmPresetId id) {
        return presets.get().resolve(id).filter(RealmPresetDefinition::enabled).orElse(null);
    }

    private CreationTimestamp now() {
        return CreationTimestamp.from(clock.instant());
    }

    private static String detail(Exception exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        value = value.replaceAll("[\\r\\n\\t]", " ");
        return value.substring(0, Math.min(240, value.length()));
    }

    private static Result result(Status status, Realm source, Realm replacement) {
        return new Result(status, Optional.ofNullable(source), Optional.ofNullable(replacement));
    }

    public enum Status {
        RESET_COMPLETED,
        RESET_FAILED_OLD_REALM_PRESERVED,
        ARCHIVED,
        EVACUATION_PENDING,
        NO_REALM,
        PRESET_UNAVAILABLE,
        OPERATION_IN_PROGRESS,
        CONFIRMATION_INVALID,
        RESTORED,
        RESTORE_CONFLICT,
        NOT_ARCHIVED,
        PRE_OPERATION_BACKUP_QUEUED,
        PRE_OPERATION_BACKUP_FAILED
    }

    public record Result(Status status, Optional<Realm> source, Optional<Realm> replacement) {
        public static Result noRealm() {
            return new Result(Status.NO_REALM, Optional.empty(), Optional.empty());
        }
    }
}
