package eu.avalanche7.paradigmrealms.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.avalanche7.paradigmrealms.domain.RealmId;
import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmInspectionService {
    private final RealmRepository repository;

    public RealmInspectionService(RealmRepository repository) {
        this.repository = repository;
    }

    public List<Realm> list() {
        return repository.list();
    }

    public Optional<Realm> findById(RealmId id) {
        return repository.findById(id);
    }

    public Optional<Realm> findByOwner(UUID ownerUuid) {
        return repository.findByOwner(ownerUuid);
    }
}
