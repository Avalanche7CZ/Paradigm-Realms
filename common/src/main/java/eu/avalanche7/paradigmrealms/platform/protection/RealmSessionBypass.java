package eu.avalanche7.paradigmrealms.platform.protection;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealmSessionBypass {
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();

    public boolean enabled(UUID player) { return enabled.contains(player); }
    public boolean enable(UUID player) { return enabled.add(player); }
    public boolean disable(UUID player) { return enabled.remove(player); }
    public void clear(UUID player) { enabled.remove(player); }
    public void clearAll() { enabled.clear(); }
    public int size() { return enabled.size(); }
    public Set<UUID> snapshot() { return Set.copyOf(enabled); }
}
