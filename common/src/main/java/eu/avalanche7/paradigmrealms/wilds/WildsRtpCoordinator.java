package eu.avalanche7.paradigmrealms.wilds;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import eu.avalanche7.paradigmrealms.platform.teleport.TeleportResult;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsActionResult;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsRtpPort;
import eu.avalanche7.paradigmrealms.platform.wilds.WildsRtpProbeResult;
import eu.avalanche7.paradigmrealms.platform.world.StandingSafety;

public final class WildsRtpCoordinator {
    private static final int SEARCHES_PER_TICK = 2;
    private final WildsLifecycleService lifecycle;
    private final Supplier<WildsConfig> config;
    private final WildsRtpPort platform;
    private final Clock clock;
    private final LongSupplier seeds;
    private final RtpCandidateGenerator candidates = new RtpCandidateGenerator();
    private final Map<UUID, Search> searches = new HashMap<>();
    private final Map<UUID, Instant> cooldowns = new HashMap<>();

    public WildsRtpCoordinator(
            WildsLifecycleService lifecycle,
            Supplier<WildsConfig> config,
            WildsRtpPort platform,
            Clock clock,
            LongSupplier seeds) {
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
        this.config = java.util.Objects.requireNonNull(config, "config");
        this.platform = java.util.Objects.requireNonNull(platform, "platform");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.seeds = java.util.Objects.requireNonNull(seeds, "seeds");
    }

    public WildsActionResult request(UUID player) {
        java.util.Objects.requireNonNull(player, "player");
        if (searches.containsKey(player)) return WildsActionResult.ALREADY_SEARCHING;
        Instant now = clock.instant();
        Instant cooldown = cooldowns.get(player);
        if (cooldown != null && cooldown.isAfter(now)) return WildsActionResult.COOLDOWN;
        WildsState state = lifecycle.state();
        searches.put(player, new Search(
                player, state.activeEpoch(), seeds.getAsLong(), now,
                new EnumMap<>(RejectReason.class)));
        platform.notify(player, "Searching for a safe Wilds location...");
        return WildsActionResult.SUCCESS;
    }

    public Duration cooldownRemaining(UUID player) {
        Instant now = clock.instant();
        Instant until = cooldowns.get(player);
        return until == null || !until.isAfter(now) ? Duration.ZERO : Duration.between(now, until);
    }

    public void tick() {
        if (searches.isEmpty()) return;
        var iterator = new ArrayList<>(searches.values()).iterator();
        int budget = SEARCHES_PER_TICK;
        while (iterator.hasNext() && budget-- > 0) process(iterator.next());
    }

    public void disconnect(UUID player) { searches.remove(player); }

    public void cancelAll(String reason) {
        for (Search search : List.copyOf(searches.values())) finish(search, reason);
    }

    public void clear() {
        searches.clear();
        cooldowns.clear();
    }

    private void process(Search search) {
        WildsConfig current = config.get();
        WildsState state = lifecycle.state();
        Instant now = clock.instant();
        if (!platform.online(search.player) || !state.lifecycle().entryOpen()
                || state.activeEpoch() != search.epoch) {
            finish(search, "cancelled because Wilds entry or generation changed");
            return;
        }
        if (Duration.between(search.started, now).compareTo(current.rtp().timeout()) > 0) {
            finish(search, "timed out");
            return;
        }
        if (search.attempt >= current.rtp().maximumAttempts()) {
            finish(search, "no safe location after " + search.attempt
                    + " attempts " + search.rejects);
            return;
        }
        RtpCandidate candidate = candidates.candidate(current.rtp(), search.seed, search.attempt++);
        boolean allowGeneration = search.generatedChunks
                < current.rtp().maximumChunksGeneratedPerRequest();
        StandingSafety safety = new StandingSafety(
                current.rtp().avoidFluids(), current.rtp().avoidLeaves(),
                current.rtp().avoidPowderSnow(), true);
        WildsRtpProbeResult probe = platform.probe(
                search.player, candidate, safety, allowGeneration);
        if (probe.generatedChunk()) search.generatedChunks++;
        if (probe.status() != WildsRtpProbeResult.Status.SAFE) {
            search.reject(map(probe.status()));
            return;
        }
        WildsState beforeTeleport = lifecycle.state();
        if (!beforeTeleport.lifecycle().entryOpen() || beforeTeleport.activeEpoch() != search.epoch) {
            finish(search, "generation changed before teleport");
            return;
        }
        TeleportResult result = platform.teleport(search.player, probe.destination().orElseThrow());
        if (result != TeleportResult.SUCCESS) {
            search.reject(RejectReason.TELEPORT_REJECTED);
            return;
        }
        lifecycle.approvePlayer(search.player);
        cooldowns.put(search.player, now.plus(current.rtp().cooldown()));
        searches.remove(search.player);
        platform.notify(search.player,
                "Teleported to Wilds after " + search.attempt + " attempt(s).");
    }

    private void finish(Search search, String detail) {
        searches.remove(search.player);
        if (platform.online(search.player)) platform.notify(search.player, "Wilds RTP failed: " + detail);
    }

    private static RejectReason map(WildsRtpProbeResult.Status status) {
        return switch (status) {
            case WORLD_UNAVAILABLE, WORLD_BORDER -> RejectReason.WORLD_BORDER;
            case CHUNK_BUDGET -> RejectReason.CHUNK_BUDGET;
            case UNSAFE_SURFACE -> RejectReason.UNSAFE_SURFACE;
            case SAFE -> throw new IllegalArgumentException("safe probe is not a rejection");
        };
    }

    private enum RejectReason { WORLD_BORDER, CHUNK_BUDGET, UNSAFE_SURFACE, TELEPORT_REJECTED }

    private static final class Search {
        private final UUID player;
        private final long epoch;
        private final long seed;
        private final Instant started;
        private final EnumMap<RejectReason, Integer> rejects;
        private int attempt;
        private int generatedChunks;

        private Search(
                UUID player, long epoch, long seed, Instant started,
                EnumMap<RejectReason, Integer> rejects) {
            this.player = player;
            this.epoch = epoch;
            this.seed = seed;
            this.started = started;
            this.rejects = rejects;
        }

        private void reject(RejectReason reason) { rejects.merge(reason, 1, Integer::sum); }
    }
}
