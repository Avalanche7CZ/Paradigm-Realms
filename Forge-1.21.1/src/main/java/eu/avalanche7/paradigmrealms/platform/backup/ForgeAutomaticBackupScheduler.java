package eu.avalanche7.paradigmrealms.platform.backup;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import eu.avalanche7.paradigmrealms.backup.BackupSchedulePolicy;
import eu.avalanche7.paradigmrealms.backup.RealmBackupConfig;
import eu.avalanche7.paradigmrealms.domain.DimensionId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

final class ForgeAutomaticBackupScheduler {
    private final RealmRepository realms;
    private final RealmBackupConfig config;
    private final ForgeBackupCatalogService catalog;
    private final Clock clock;
    private final Instant enabledAt;
    private final BackupSchedulePolicy policy = new BackupSchedulePolicy();

    ForgeAutomaticBackupScheduler(
            RealmRepository realms,
            RealmBackupConfig config,
            ForgeBackupCatalogService catalog,
            Clock clock,
            Instant enabledAt) {
        this.realms = realms;
        this.config = config;
        this.catalog = catalog;
        this.clock = clock;
        this.enabledAt = enabledAt;
    }

    List<Realm> due(Set<Long> queuedOrRunning) {
        Instant now = clock.instant();
        return realms.list().stream()
                .filter(this::eligible)
                .filter(realm -> policy.decide(
                        realm.id().value(),
                        now,
                        enabledAt,
                        catalog.latestVerified(realm.id().value()),
                        true,
                        queuedOrRunning.contains(realm.id().value()),
                        config).due())
                .toList();
    }

    Optional<Instant> nextDue() {
        return realms.list().stream()
                .filter(this::eligible)
                .map(realm -> policy.nextDue(
                        realm.id().value(),
                        enabledAt,
                        catalog.latestVerified(realm.id().value()),
                        config.automatic(),
                        config.schedulingSpread()))
                .min(Instant::compareTo);
    }

    private boolean eligible(Realm realm) {
        if (!config.enabled() || !config.automatic().enabled()) {
            return false;
        }
        if (!realm.dimension().equals(DimensionId.REALMS)
                || realm.lifecycleOperation().isPresent()) {
            return false;
        }
        if (realm.state() == RealmLifecycleState.ARCHIVED) {
            return !config.automatic().activeRealmsOnly()
                    && config.automatic().includeArchivedRealms();
        }
        return realm.state() == RealmLifecycleState.ACTIVE;
    }
}
