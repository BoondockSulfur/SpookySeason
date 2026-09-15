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
import org.example.lang.Lang;
import org.example.util.Scheduler;

/**
 * Target flavour "zone": a box defined in this plugin's own configuration, held the same way a
 * WorldGuard region is in {@link RegionTarget} — every attacker that gets inside counts as a breach
 * and is removed, and {@code breachLimit} breaches lose the raid.
 *
 * <p>Exists so the region mode does not force WorldGuard on anyone. Zones are set up in game with
 * {@code /spookyraid zone pos1|pos2|save}, which is the same shape SiteZero uses for its arenas.
 */
public class ZoneTarget implements RaidTarget {

    private final Plugin plugin;
    private final World world;
    private final String name;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int breachLimit;
    private final AtomicInteger breaches = new AtomicInteger();

    private volatile Location center;
    private volatile boolean ready;
    private volatile String failure;

    private ZoneTarget(Plugin plugin, World world, String name, int[] bounds, int breachLimit) {
        this.plugin = plugin;
        this.world = world;
        this.name = name;
        this.minX = Math.min(bounds[0], bounds[3]);
        this.minY = Math.min(bounds[1], bounds[4]);
        this.minZ = Math.min(bounds[2], bounds[5]);
        this.maxX = Math.max(bounds[0], bounds[3]);
        this.maxY = Math.max(bounds[1], bounds[4]);
        this.maxZ = Math.max(bounds[2], bounds[5]);
        this.breachLimit = breachLimit;
    }

    /**
     * Builds the configured zone. {@code cfg} is the {@code raid.target.zone} section; the zones
     * themselves live in {@code raid.target.zones} as {@code world,x1,y1,z1,x2,y2,z2}.
     */
    public static ZoneTarget from(Plugin plugin, ConfigurationSection cfg, ConfigurationSection zones) {
        if (cfg == null || zones == null) {
            return null;
        }
        String name = cfg.getString("name", "");
        if (name.isEmpty()) {
            return null;
        }
        int[] bounds = ZoneTarget.parse(zones.getString(name, ""));
        if (bounds == null) {
            plugin.getLogger().warning("Raid zone '" + name + "' is missing or malformed in raid.target.zones.");
            return null;
        }
        World world = Bukkit.getWorld(zones.getString(name, "").split(",")[0].trim());
        if (world == null) {
            return null;
        }
        return new ZoneTarget(plugin, world, name, bounds, Math.max(1, cfg.getInt("breachLimit", 20)));
    }

    /** Format: {@code world,x1,y1,z1,x2,y2,z2}. Returns null when it does not parse. */
    public static int[] parse(String entry) {
        if (entry == null) {
            return null;
        }
        String[] parts = entry.split(",");
        if (parts.length < 7) {
            return null;
        }
        int[] bounds = new int[6];
        try {
            for (int i = 0; i < 6; ++i) {
                bounds[i] = (int)Math.floor(Double.parseDouble(parts[i + 1].trim()));
            }
        }
        catch (NumberFormatException e) {
            return null;
        }
        return bounds;
    }

    /** Builds the stored form from two corners. */
    public static String format(Location a, Location b) {
        return a.getWorld().getName() + ","
                + a.getBlockX() + "," + a.getBlockY() + "," + a.getBlockZ() + ","
                + b.getBlockX() + "," + b.getBlockY() + "," + b.getBlockZ();
    }

    @Override
    public boolean prepare() {
        Location raw = new Location(this.world,
                (this.minX + this.maxX) / 2.0 + 0.5,
                (this.minY + this.maxY) / 2.0,
                (this.minZ + this.maxZ) / 2.0 + 0.5);
        this.breaches.set(0);
        // Pull the centre down to ground level so the spawn ring and particles do not hang in
        // mid-air. getHighestBlockYAt() may only run on that location's region thread.
        Scheduler.runAtLocation(this.plugin, raw, () -> {
            Location grounded = raw.clone();
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
    public Location spawnAnchor() {
        return this.center();
    }

    @Override
    public Location navTarget(Location from) {
        return this.center();
    }

    @Override
    public double integrity() {
        return Math.max(0.0, 1.0 - (double)this.breaches.get() / (double)this.breachLimit);
    }

    @Override
    public boolean isLost() {
        return this.breaches.get() >= this.breachLimit;
    }

    public int breaches() {
        return this.breaches.get();
    }

    /** Distance from the centre to a corner — how far out the spawn ring has to sit. */
    public double halfDiagonal() {
        double dx = (this.maxX - this.minX) / 2.0;
        double dz = (this.maxZ - this.minZ) / 2.0;
        return Math.sqrt(dx * dx + dz * dz);
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
    public void inspectAttacker(Entity attacker, double waveDamage, boolean ranged, double rangedReach) {
        if (!this.ready || !attacker.isValid() || attacker.isDead()) {
            return;
        }
        Location loc = attacker.getLocation();
        if (!this.contains(loc)) {
            return;
        }
        // Through: the attacker is removed, the damage stays on the counter. Leaving it standing
        // would count it twice as soon as it moved again.
        attacker.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0.0, 1.0, 0.0), 20, 0.4, 0.6, 0.4, 0.02);
        attacker.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.HOSTILE, 1.0f, 0.6f);
        attacker.remove();
        this.breaches.incrementAndGet();
    }

    private boolean contains(Location loc) {
        if (!this.world.equals(loc.getWorld())) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= this.minX && x <= this.maxX
                && y >= this.minY && y <= this.maxY
                && z >= this.minZ && z <= this.maxZ;
    }

    @Override
    public boolean isTracked(UUID id) {
        // A zone puts nothing of its own into the world.
        return false;
    }

    @Override
    public void cleanup() {
        this.ready = false;
    }

    @Override
    public List<String> describe() {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(Lang.get("raid.status.target-zone",
                "zone", this.name,
                "breaches", String.valueOf(this.breaches.get()),
                "limit", String.valueOf(this.breachLimit)));
        return lines;
    }
}
