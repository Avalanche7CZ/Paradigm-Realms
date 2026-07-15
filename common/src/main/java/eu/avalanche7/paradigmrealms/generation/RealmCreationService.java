package eu.avalanche7.paradigmrealms.generation;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.allocation.RealmAllocator;
import eu.avalanche7.paradigmrealms.domain.CreationTimestamp;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.RealmOwner;
import eu.avalanche7.paradigmrealms.domain.RealmPresetId;
import eu.avalanche7.paradigmrealms.domain.SchemaVersion;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.domain.realm.RealmFailure;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.domain.realm.RealmOperation;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmCreationService {
    private static final int MAX_FAILURE_DETAIL = 240;

    private final RealmRepository repository;
    private final RealmAllocator allocator;
    private final Supplier<RealmPresetCatalog> presets;
    private final PresetPlacementPlanner placementPlanner;
    private final RealmGenerationPort generator;
    private final Clock clock;
    private final Supplier<UUID> operationIds;

    public RealmCreationService(
            RealmRepository repository,
            RealmAllocator allocator,
            RealmPresetCatalog presets,
            RealmGenerationPort generator,
            Clock clock,
            Supplier<UUID> operationIds) {
        this(repository, allocator, () -> presets, new PresetPlacementPlanner(), generator, clock, operationIds);
    }

    public RealmCreationService(
            RealmRepository repository,
            RealmAllocator allocator,
            Supplier<RealmPresetCatalog> presets,
            PresetPlacementPlanner placementPlanner,
            RealmGenerationPort generator,
            Clock clock,
            Supplier<UUID> operationIds) {
        this.repository = repository;
        this.allocator = allocator;
        this.presets = presets;
        this.placementPlanner = placementPlanner;
        this.generator = generator;
        this.clock = clock;
        this.operationIds = operationIds;
    }

    public Realm create(UUID ownerUuid, RealmPresetId presetId) {
        if (repository.findByOwner(ownerUuid).isPresent()) {
            throw new RealmAlreadyExistsException(ownerUuid);
        }
        RealmPresetDefinition preset = presets.get().resolve(presetId)
                .orElseThrow(() -> new UnknownRealmPresetException(presetId));
        return create(ownerUuid, preset);
    }

    public Realm create(UUID ownerUuid, RealmPresetDefinition preset) {
        if (!preset.enabled()) {
            throw new IllegalArgumentException("cannot create from disabled preset " + preset.id());
        }
        if (repository.findByOwner(ownerUuid).isPresent()) {
            throw new RealmAlreadyExistsException(ownerUuid);
        }
        CreationTimestamp now = now();
        PresetPlacementPlan[] capturedPlan = new PresetPlacementPlan[1];
        Realm allocated = repository.allocateRealm(id -> {
            var allocation = allocator.preview(id);
            PresetPlacementPlan plan = placementPlanner.plan(allocation, preset);
            capturedPlan[0] = plan;
            RealmOperation operation = new RealmOperation(
                    operationIds.get(), preset.revision(), 1, now);
            return new Realm(id, new RealmOwner(ownerUuid), RealmLifecycleState.ALLOCATED,
                    DimensionId.REALMS, allocation, plan.spawn(), preset.id(), Set.of(), Set.of(),
                    RealmAccessPolicy.PRIVATE, now, SchemaVersion.CURRENT,
                    Optional.of(operation), Optional.empty());
        });
        return generate(allocated, capturedPlan[0]);
    }

    public List<Realm> recoverInterrupted() {
        List<Realm> recovered = new ArrayList<>();
        for (Realm realm : repository.list()) {
            if (realm.state() == RealmLifecycleState.ALLOCATED
                    || realm.state() == RealmLifecycleState.GENERATING) {
                RealmPresetDefinition preset = presets.get().resolve(realm.preset()).orElse(null);
                if (preset == null || !preset.enabled() || realm.operation().isEmpty()
                        || !realm.operation().orElseThrow().presetRevision().equals(preset.revision())) {
                    recovered.add(fail(realm, "PRESET_UNAVAILABLE",
                            "captured preset or revision is unavailable during recovery"));
                    continue;
                }
                try {
                    recovered.add(generate(realm, placementPlanner.recover(realm, preset)));
                } catch (RuntimeException exception) {
                    recovered.add(fail(realm, "PRESET_PLAN_INVALID", failureDetail(exception)));
                }
            }
        }
        return List.copyOf(recovered);
    }

    private Realm generate(Realm realm, PresetPlacementPlan plan) {
        Realm generating = realm.state() == RealmLifecycleState.ALLOCATED
                ? repository.updateLifecycle(realm.id(), RealmLifecycleState.GENERATING, Optional.empty())
                : realm;
        try {
            generator.generate(generating, plan);
            return repository.updateLifecycle(generating.id(), RealmLifecycleState.ACTIVE, Optional.empty());
        } catch (Exception exception) {
            return fail(generating, "GENERATION_FAILED", failureDetail(exception));
        }
    }

    private Realm fail(Realm realm, String code, String detail) {
        RealmOperation operation = realm.operation().orElseThrow();
        RealmFailure failure = new RealmFailure(code, detail, realm.state(),
                operation.operationId(), operation.attempt(), now());
        return repository.updateLifecycle(realm.id(), RealmLifecycleState.FAILED, Optional.of(failure));
    }

    private static String failureDetail(Exception exception) {
        String detail = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        detail = detail.replaceAll("[\\r\\n\\t]", " ");
        return detail.length() > MAX_FAILURE_DETAIL ? detail.substring(0, MAX_FAILURE_DETAIL) : detail;
    }

    private CreationTimestamp now() {
        return CreationTimestamp.from(clock.instant());
    }
}
