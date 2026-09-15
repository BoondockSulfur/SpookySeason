package org.example.feature.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Scheduler;

/**
 * Target flavour "region": a named WorldGuard region counts as held as long as not too many
 * attackers get inside. Every attacker that walks in counts as a breach and is removed — at
 * {@code breachLimit} breaches the raid is lost.
 *
 * <p>Without WorldGuard (or without the region lookup, see
 * {@link org.example.integration.RegionIntegration#hasRegionLookup()}) this mode cannot start.
 * {@link #prepare()} reports that properly instead of quietly doing nothing later on.
 */
public class RegionTarget implements RaidTarget {
    private final Plugin plugin;
    private final World world;
    private final String regionName;
    private final int breachLimit;
    private final AtomicInteger breaches = new AtomicInteger();

    private volatile Location center;
    private volatile boolean ready;
    private volatile String failure;

    private RegionTarget(Plugin plugin, World world, String regionName, int breachLimit) {
        this.plugin = plugin;
        this.world = world;
        this.regionName = regionName;
        this.breachLimit = breachLimit;
    }

    public static RegionTarget from(Plugin plugin, ConfigurationSection cfg) {
        if (cfg == null) {
            return null;
        }
        World world = Bukkit.getWorld(cfg.getString("world", ""));
        String name = cfg.getString("name", "");
        if (world == null || name.isEmpty()) {
            return null;
        }
        return new RegionTarget(plugin, world, name, Math.max(1, cfg.getInt("breachLimit", 20)));
    }

    @Override
    public boolean prepare() {
        if (!SpookySeason.get().regions().hasRegionLookup()) {
            this.failure = "raid.error.no-worldguard";
            return false;
        }
        Location regionCenter = SpookySeason.get().regions().regionCenter(this.world, this.regionName);
        if (regionCenter == null) {
            this.failure = "raid.error.no-region";
            return false;
        }
        this.center = regionCenter;
        this.breaches.set(0);
        // Pull the centre down to ground level so the spawn ring and particles do not hang in
        // mid-air. getHighestBlockYAt() may only run on that location's region thread.
        Scheduler.runAtLocation(this.plugin, regionCenter, () -> {
            Location grounded = regionCenter.clone();
            grounded.setY(this.world.getHighestBlockYAt(grounded) + 1);
            this.center = grounded;
            this.ready = true;
        });
        return true;
    }

    @Override
    public boolean isReady() {
        return this.ready;
    }

    @Override
    public String failure() {
        return this.failure;
    }

    @Override
    public World world() {
        return this.world;
    }

    @Override
    public Location center() {
        Location current = this.center;
        return current == null ? null : current.clone();
    }

    @Override
    public double integrity() {
        return Math.max(0.0, 1.0 - (double)this.breaches.get() / (double)this.breachLimit);
    }

    @Override
    public boolean isLost() {
        return this.breaches.get() >= this.breachLimit;
    }

    /** How many attackers have already got through. */
    public int breaches() {
        return this.breaches.get();
    }

    public int breachLimit() {
        return this.breachLimit;
    }

    @Override
    public void tick() {
        Location current = this.center;
        if (current == null) {
            return;
        }
        Scheduler.runAtLocation(this.plugin, current, () ->
                current.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, current.clone().add(0.0, 1.2, 0.0), 8, 0.6, 0.4, 0.6, 0.01));
    }

    @Override
    public Location spawnAnchor() {
        return this.center();
    }

    @Override
    public Location navTarget(Location from) {
        return this.center();
    }

    @Override
    public void inspectAttacker(Entity attacker, double waveDamage, boolean ranged, double rangedReach) {
        if (!this.ready || !attacker.isValid() || attacker.isDead()) {
            return;
        }
        Location loc = attacker.getLocation();
        if (!SpookySeason.get().regions().isInRegion(loc, this.regionName)) {
            return;
        }
        // Through: the attacker is removed, the damage stays on the counter. Leaving it standing
        // would count it twice as soon as it moved again.
        attacker.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.02);
        attacker.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1.0f, 0.6f);
        attacker.remove();
        this.breaches.incrementAndGet();
    }

    @Override
    public boolean isTracked(UUID id) {
        // Region mode puts nothing of its own into the world.
        return false;
    }

    @Override
    public void cleanup() {
        this.ready = false;
    }

    @Override
    public List<String> describe() {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(Lang.get("raid.status.target-region",
                "region", this.regionName,
                "breaches", String.valueOf(this.breaches.get()),
                "limit", String.valueOf(this.breachLimit)));
        return lines;
    }
}
