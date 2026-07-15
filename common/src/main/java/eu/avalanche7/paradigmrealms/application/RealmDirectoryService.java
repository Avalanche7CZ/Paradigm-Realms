package eu.avalanche7.paradigmrealms.application;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import eu.avalanche7.paradigmrealms.domain.realm.Realm;
import eu.avalanche7.paradigmrealms.domain.realm.RealmAccessPolicy;
import eu.avalanche7.paradigmrealms.domain.realm.RealmLifecycleState;
import eu.avalanche7.paradigmrealms.persistence.RealmRepository;

public final class RealmDirectoryService {
    private static final int PAGE_SIZE = 8;
    private final RealmRepository repository;
    private volatile Snapshot cached = new Snapshot(-1, List.of());

    public RealmDirectoryService(RealmRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Page page(int requestedPage) {
        if (requestedPage < 1) return new Page(requestedPage, 0, List.of(), false);
        List<RealmDirectoryEntry> entries = snapshot();
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (requestedPage > pages) return new Page(requestedPage, pages, List.of(), false);
        int start = (requestedPage - 1) * PAGE_SIZE;
        return new Page(requestedPage, pages, entries.subList(start, Math.min(entries.size(), start + PAGE_SIZE)), true);
    }

    public List<RealmDirectoryEntry> snapshot() {
        long revision = repository.schemaMetadata().revision();
        Snapshot current = cached;
        if (current.revision() == revision) return current.entries();
        List<RealmDirectoryEntry> entries = repository.list().stream()
                .filter(RealmDirectoryService::listed)
                .map(realm -> new RealmDirectoryEntry(realm.id(), realm.owner().uuid(), realm.displayName(),
                        descriptionPreview(realm.description())))
                .sorted(Comparator.comparing((RealmDirectoryEntry entry) -> entry.displayName().toLowerCase(Locale.ROOT))
                        .thenComparing(RealmDirectoryEntry::realmId)).toList();
        cached = new Snapshot(revision, entries);
        return entries;
    }

    private static boolean listed(Realm realm) {
        return realm.state() == RealmLifecycleState.ACTIVE && realm.listed()
                && realm.accessPolicy() == RealmAccessPolicy.PUBLIC_VISIT;
    }

    private static String descriptionPreview(String description) {
        return description.length() <= 80 ? description : description.substring(0, 77) + "...";
    }

    public record Page(int requestedPage, int pageCount, List<RealmDirectoryEntry> entries, boolean valid) {
        public Page {
            entries = List.copyOf(entries);
        }
    }

    private record Snapshot(long revision, List<RealmDirectoryEntry> entries) {
        private Snapshot {
            entries = List.copyOf(entries);
        }
    }
}
