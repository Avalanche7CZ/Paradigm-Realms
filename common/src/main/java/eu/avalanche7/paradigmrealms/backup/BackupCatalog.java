package eu.avalanche7.paradigmrealms.backup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BackupCatalog {
    private final Map<BackupId, BackupCatalogEntry> entries = new LinkedHashMap<>();

    public BackupCatalog(List<BackupCatalogEntry> initialEntries) {
        for (BackupCatalogEntry entry : initialEntries) {
            if (entries.putIfAbsent(entry.backupId(), entry) != null) {
                throw new IllegalArgumentException("duplicate backup ID in catalog");
            }
        }
    }

    public synchronized void put(BackupCatalogEntry entry) {
        BackupCatalogEntry existing = entries.get(entry.backupId());
        if (existing != null && !existing.archiveRelativePath().equals(entry.archiveRelativePath())) {
            throw new IllegalArgumentException("backup ID already belongs to another archive");
        }
        entries.put(entry.backupId(), entry);
    }

    public synchronized Optional<BackupCatalogEntry> find(BackupId id) {
        return Optional.ofNullable(entries.get(id));
    }

    public synchronized boolean remove(BackupId id) {
        return entries.remove(id) != null;
    }

    public synchronized List<BackupCatalogEntry> list() {
        return entries.values().stream()
                .sorted(Comparator.comparing(BackupCatalogEntry::createdAt).reversed())
                .toList();
    }

    public synchronized List<BackupCatalogEntry> forRealm(long realmId) {
        return list().stream().filter(entry -> entry.realmId() == realmId).toList();
    }

    public synchronized List<BackupCatalogEntry> forOwner(UUID ownerUuid) {
        return list().stream().filter(entry -> entry.ownerUuid().equals(ownerUuid)).toList();
    }

    public synchronized int size() {
        return entries.size();
    }
}
