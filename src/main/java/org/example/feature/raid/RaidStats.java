package org.example.feature.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import org.bukkit.Bukkit;
import org.example.api.RaidResult;

/**
 * Per-player bookkeeping for the raid that is currently running.
 *
 * <p>Damage and death events arrive on the region thread of the entity involved, so on Folia
 * several threads write here at once; every counter is atomic and the maps are concurrent.
 */
final class RaidStats {

    /** Running totals of one defender. */
    static final class Tally {
        private final UUID id;
        private volatile String name;
        private final AtomicInteger kills = new AtomicInteger();
        private final AtomicInteger leaderKills = new AtomicInteger();
        private final AtomicInteger defenseKills = new AtomicInteger();
        private final AtomicInteger deaths = new AtomicInteger();
        private final DoubleAdder damage = new DoubleAdder();

        private Tally(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        void addKill(boolean leader, boolean defense) {
            if (leader) {
                this.leaderKills.incrementAndGet();
            } else {
                this.kills.incrementAndGet();
            }
            if (defense) {
                this.defenseKills.incrementAndGet();
            }
        }

        void addDamage(double amount) {
            if (amount > 0.0) {
                this.damage.add(amount);
            }
        }

        void addDeath() {
            this.deaths.incrementAndGet();
        }
    }

    /** The most recent player hit on a raid mob, for kill credit. */
    record Hit(UUID player, String name, long at) {
    }

    private final ConcurrentHashMap<UUID, Tally> tallies = new ConcurrentHashMap<UUID, Tally>();
    private final ConcurrentHashMap<UUID, Hit> lastHits = new ConcurrentHashMap<UUID, Hit>();

    void reset() {
        this.tallies.clear();
        this.lastHits.clear();
    }

    /** The tally of this player, created on first use. A known name is kept up to date. */
    Tally tally(UUID player, String name) {
        Tally tally = this.tallies.computeIfAbsent(player, id -> new Tally(id, name));
        if (name != null) {
            tally.name = name;
        }
        return tally;
    }

    void recordHit(UUID mob, UUID player, String name) {
        this.lastHits.put(mob, new Hit(player, name, System.currentTimeMillis()));
    }

    /**
     * Who gets the kill for this mob: the last player who hurt it, provided that was no longer
     * than {@code windowMillis} ago. Consumes the entry.
     */
    Hit claimKill(UUID mob, long windowMillis) {
        Hit hit = this.lastHits.remove(mob);
        if (hit == null || System.currentTimeMillis() - hit.at() > windowMillis) {
            return null;
        }
        return hit;
    }

    void forget(UUID mob) {
        this.lastHits.remove(mob);
    }

    /**
     * Results for everyone with a tally plus every participant, the most effective defender
     * first. Participants without a tally appear with zeros.
     */
    List<RaidResult.PlayerResult> results(Collection<UUID> participants) {
        for (UUID id : Set.copyOf(participants)) {
            this.tallies.computeIfAbsent(id, k -> new Tally(k, null));
        }
        ArrayList<RaidResult.PlayerResult> result = new ArrayList<RaidResult.PlayerResult>();
        for (Tally tally : this.tallies.values()) {
            String name = tally.name;
            if (name == null) {
                name = Bukkit.getOfflinePlayer(tally.id).getName();
            }
            result.add(new RaidResult.PlayerResult(tally.id, name == null ? tally.id.toString() : name,
                    tally.kills.get(), tally.leaderKills.get(), tally.damage.sum(),
                    tally.defenseKills.get(), tally.deaths.get()));
        }
        result.sort(Comparator.comparingInt((RaidResult.PlayerResult p) -> p.kills() + p.leaderKills()).reversed()
                .thenComparing(Comparator.comparingDouble(RaidResult.PlayerResult::damage).reversed())
                .thenComparing(RaidResult.PlayerResult::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
