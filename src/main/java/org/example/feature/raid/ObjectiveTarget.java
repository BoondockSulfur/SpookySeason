package org.example.feature.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Attributes;
import org.example.util.Scheduler;

/**
 * Target flavour "objects with health": one or more fixed points the attackers have to bring
 * down.
 *
 * <ul>
 * <li>The plugin tracks the health itself rather than using the entity attribute, which vanilla
 *     caps at 1024.</li>
 * <li>By default the target is a display entity ({@code style: display}). Display entities have
 *     no hitbox, so player attacks aimed at attackers next to the target are not intercepted by
 *     it. A mob can still be used via {@code style: entity}.</li>
 * </ul>
 *
 * <p>Damage comes from proximity rather than from real hits: every attacker within {@code reach}
 * strikes once per second, identically in both styles.
 */
public class ObjectiveTarget implements RaidTarget {

    /** A single point to defend. */
    private static final class Objective {
        private final Location anchor;
        private final double maxHealth;
        private final DoubleAdder damageTaken = new DoubleAdder();
        private volatile Entity visual;
        private volatile Entity label;
        private volatile boolean announcedLoss;

        private Objective(Location anchor, double maxHealth) {
            this.anchor = anchor;
            this.maxHealth = maxHealth;
        }

        private double health() {
            return Math.max(0.0, this.maxHealth - this.damageTaken.sum());
        }

        private boolean destroyed() {
            return this.health() <= 0.0;
        }
    }

    private static final int LOSE_ALL = -1;
    private static final int LOSE_ANY = -2;

    private final Plugin plugin;
    private final ConfigurationSection cfg;
    private final World world;
    private final List<Objective> objectives = new CopyOnWriteArrayList<Objective>();
    private final List<UUID> tracked = new CopyOnWriteArrayList<UUID>();

    private final boolean displayStyle;
    private final double reach;
    private final int loseMode;

    private final AtomicInteger pendingSpawns = new AtomicInteger();
    private volatile boolean ready;
    private volatile String failure;
    private volatile int spawnCursor;

    private ObjectiveTarget(Plugin plugin, ConfigurationSection cfg, World world, List<Objective> objectives) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.world = world;
        this.objectives.addAll(objectives);
        this.displayStyle = !"entity".equalsIgnoreCase(cfg.getString("style", "display"));
        this.reach = Math.max(1.0, cfg.getDouble("reach", 4.0));
        this.loseMode = ObjectiveTarget.parseLoseMode(cfg.getString("lose", "all"));
    }

    /**
     * Reads the target configuration: either the {@code points} list or, while that is empty, the
     * single point from {@code world/x/y/z}. Returns {@code null} if no usable point remains.
     */
    public static ObjectiveTarget from(Plugin plugin, ConfigurationSection cfg) {
        if (cfg == null) {
            return null;
        }
        double defaultHealth = Math.max(1.0, cfg.getDouble("health", 1024.0));
        List<String> points = cfg.getStringList("points");
        ArrayList<Objective> parsed = new ArrayList<Objective>();
        World world = null;
        if (!points.isEmpty()) {
            for (String entry : points) {
                Objective objective = ObjectiveTarget.parsePoint(plugin, entry, defaultHealth);
                if (objective == null) continue;
                if (world == null) {
                    world = objective.anchor.getWorld();
                } else if (!world.equals(objective.anchor.getWorld())) {
                    // A raid takes place in exactly one world — otherwise the spawn ring, the fog
                    // and the bossbars fall into two halves that know nothing of each other.
                    plugin.getLogger().warning("Raid objective '" + entry + "' is in a different world than the first one - ignored.");
                    continue;
                }
                parsed.add(objective);
            }
        } else {
            world = Bukkit.getWorld(cfg.getString("world", ""));
            if (world != null) {
                parsed.add(new Objective(new Location(world,
                        cfg.getDouble("x", 0.5), cfg.getDouble("y", 64.0), cfg.getDouble("z", 0.5)), defaultHealth));
            }
        }
        if (world == null || parsed.isEmpty()) {
            return null;
        }
        return new ObjectiveTarget(plugin, cfg, world, parsed);
    }

    /** Format: {@code world,x,y,z} or {@code world,x,y,z,health}. */
    private static Objective parsePoint(Plugin plugin, String entry, double defaultHealth) {
        String[] parts = entry.split(",");
        if (parts.length < 4) {
            plugin.getLogger().warning("Raid objective point '" + entry + "' is malformed (expected world,x,y,z[,health]).");
            return null;
        }
        World world = Bukkit.getWorld(parts[0].trim());
        if (world == null) {
            plugin.getLogger().warning("Raid objective point '" + entry + "' names an unknown world.");
            return null;
        }
        try {
            Location loc = new Location(world,
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
            double health = parts.length > 4 ? Math.max(1.0, Double.parseDouble(parts[4].trim())) : defaultHealth;
            return new Objective(loc, health);
        }
        catch (NumberFormatException e) {
            plugin.getLogger().warning("Raid objective point '" + entry + "' has non-numeric coordinates.");
            return null;
        }
    }

    private static int parseLoseMode(String value) {
        String v = value == null ? "all" : value.trim().toLowerCase(Locale.ROOT);
        if ("any".equals(v)) {
            return LOSE_ANY;
        }
        if ("all".equals(v)) {
            return LOSE_ALL;
        }
        try {
            return Math.max(1, Integer.parseInt(v));
        }
        catch (NumberFormatException e) {
            return LOSE_ALL;
        }
    }

    /**
     * Places the visuals. On Folia that happens on each objective's region thread, so the target
     * only counts as ready once every placement has completed; the manager waits within its
     * grace period.
     */
    @Override
    public boolean prepare() {
        this.pendingSpawns.set(this.objectives.size());
        for (Objective objective : this.objectives) {
            Scheduler.runAtLocation(this.plugin, objective.anchor, () -> this.spawnVisual(objective));
        }
        return true;
    }

    private void spawnVisual(Objective objective) {
        try {
            if (this.displayStyle) {
                this.spawnDisplay(objective);
            } else {
                this.spawnEntityStyle(objective);
            }
            this.spawnLabel(objective);
        }
        catch (Exception e) {
            this.failure = "raid.error.objective-spawn";
            this.plugin.getLogger().log(Level.WARNING, "Raid objective could not be placed", e);
        }
        finally {
            if (this.pendingSpawns.decrementAndGet() <= 0 && this.failure == null) {
                this.ready = true;
            }
        }
    }

    private void spawnDisplay(Objective objective) {
        // A block display sits with its corner on the entity position — spawn it half a block
        // offset so it ends up centred on the configured point.
        Location loc = objective.anchor.clone().add(-0.5, 0.0, -0.5);
        BlockDisplay display = (BlockDisplay)loc.getWorld().spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        display.setBlock(this.blockData());
        display.setBrightness(new Display.Brightness(15, 15));
        display.setGlowing(this.cfg.getBoolean("glowing", true));
        display.setGlowColorOverride(Color.fromRGB(255, 140, 0));
        this.register(display);
        objective.visual = display;
    }

    private void spawnEntityStyle(Objective objective) {
        Entity spawned = objective.anchor.getWorld().spawnEntity(objective.anchor, this.entityType());
        if (!(spawned instanceof LivingEntity)) {
            spawned.remove();
            this.failure = "raid.error.objective-entity";
            return;
        }
        LivingEntity le = (LivingEntity)spawned;
        // A sentry, not a fighter: no AI, no pathfinding, no fighting back.
        le.setAI(false);
        le.setSilent(this.cfg.getBoolean("silent", true));
        le.setCollidable(false);
        le.setGlowing(this.cfg.getBoolean("glowing", true));
        // Invulnerable: the plugin tracks the health, the entity is only the visual.
        le.setInvulnerable(true);
        AttributeInstance max = le.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            le.setHealth(max.getValue());
        }
        le.customName(ObjectiveTarget.legacy(Lang.get("raid.objective-name", new String[0])));
        le.setCustomNameVisible(false);
        this.register(le);
        objective.visual = le;
    }

    private void spawnLabel(Objective objective) {
        if (!this.cfg.getBoolean("showLabel", true)) {
            return;
        }
        Location loc = objective.anchor.clone().add(0.0, this.cfg.getDouble("labelHeight", 1.6), 0.0);
        TextDisplay label = (TextDisplay)loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        label.setBillboard(Display.Billboard.CENTER);
        label.setSeeThrough(false);
        label.setDefaultBackground(false);
        label.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
        this.register(label);
        objective.label = label;
        this.updateLabel(objective);
    }

    private void register(Entity entity) {
        entity.setPersistent(true);
        entity.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        entity.getPersistentDataContainer().set(SpookySeason.get().raidMarker(), PersistentDataType.BYTE, (byte)1);
        this.tracked.add(entity.getUniqueId());
    }

    private BlockData blockData() {
        String name = this.cfg.getString("block", "SOUL_LANTERN");
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock()) {
            this.plugin.getLogger().warning("Unknown raid objective block '" + name + "' - falling back to SOUL_LANTERN.");
            material = Material.SOUL_LANTERN;
        }
        return material.createBlockData();
    }

    private EntityType entityType() {
        String name = this.cfg.getString("entity", "IRON_GOLEM");
        try {
            return EntityType.valueOf(name.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            this.plugin.getLogger().warning("Unknown raid objective entity '" + name + "' - falling back to IRON_GOLEM.");
            return EntityType.IRON_GOLEM;
        }
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
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (Objective objective : this.objectives) {
            x += objective.anchor.getX();
            y += objective.anchor.getY();
            z += objective.anchor.getZ();
            ++count;
        }
        if (count == 0) {
            return null;
        }
        return new Location(this.world, x / count, y / count, z / count);
    }

    @Override
    public Location spawnAnchor() {
        List<Objective> alive = this.aliveObjectives();
        if (alive.isEmpty()) {
            return this.center();
        }
        // Round-robin rather than random: with several targets each takes an equal share of the
        // pressure, instead of chance hammering one of them for a whole wave.
        int index = Math.floorMod(this.spawnCursor++, alive.size());
        return alive.get(index).anchor.clone();
    }

    private List<Objective> aliveObjectives() {
        ArrayList<Objective> alive = new ArrayList<Objective>();
        for (Objective objective : this.objectives) {
            if (objective.destroyed()) continue;
            alive.add(objective);
        }
        return alive;
    }

    @Override
    public double integrity() {
        double current = 0.0;
        double max = 0.0;
        for (Objective objective : this.objectives) {
            current += objective.health();
            max += objective.maxHealth;
        }
        if (max <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, current / max));
    }

    @Override
    public boolean isLost() {
        if (!this.ready) {
            return false;
        }
        int destroyed = 0;
        for (Objective objective : this.objectives) {
            if (!objective.destroyed()) continue;
            ++destroyed;
        }
        if (destroyed == 0) {
            return false;
        }
        if (this.loseMode == LOSE_ANY) {
            return true;
        }
        if (this.loseMode == LOSE_ALL) {
            return destroyed >= this.objectives.size();
        }
        return destroyed >= Math.min(this.loseMode, this.objectives.size());
    }

    /** How many targets have already fallen — for announcements and status. */
    public int destroyedCount() {
        int destroyed = 0;
        for (Objective objective : this.objectives) {
            if (!objective.destroyed()) continue;
            ++destroyed;
        }
        return destroyed;
    }

    public int objectiveCount() {
        return this.objectives.size();
    }

    @Override
    public void tick() {
        for (Objective objective : this.objectives) {
            Entity visual = objective.visual;
            if (visual == null) continue;
            Scheduler.runOnEntity(this.plugin, visual, () -> {
                if (!visual.isValid() || visual.isDead()) {
                    return;
                }
                this.updateLabel(objective);
                if (objective.destroyed()) {
                    return;
                }
                visual.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                        objective.anchor.clone().add(0.0, 0.9, 0.0), 5, 0.3, 0.3, 0.3, 0.01);
            });
        }
    }

    private void updateLabel(Objective objective) {
        Entity label = objective.label;
        if (label == null || !(label instanceof TextDisplay) || !label.isValid()) {
            return;
        }
        String text = objective.destroyed()
                ? Lang.get("raid.objective-label-lost", new String[0])
                : Lang.get("raid.objective-label",
                        "health", String.valueOf((long)Math.ceil(objective.health())),
                        "max", String.valueOf((long)objective.maxHealth));
        ((TextDisplay)label).text(ObjectiveTarget.legacy(text));
    }

    @Override
    public void inspectAttacker(Entity attacker, double waveDamage, boolean ranged, double rangedReach) {
        if (!this.ready || !attacker.isValid() || attacker.isDead()) {
            return;
        }
        Location loc = attacker.getLocation();
        Objective objective = this.nearestAlive(loc);
        if (objective == null) {
            return;
        }
        if (!this.withinReach(loc, objective, ranged, rangedReach)) {
            return;
        }
        objective.damageTaken.add(waveDamage);
        if (attacker instanceof LivingEntity) {
            // Damage against the target is bookkeeping inside the plugin; the swing is the
            // visible feedback.
            LivingEntity shooter = (LivingEntity)attacker;
            shooter.swingMainHand();
            if (ranged) {
                this.shootAt(shooter, objective);
            }
        }
        attacker.getWorld().spawnParticle(Particle.CRIT, objective.anchor.clone().add(0.0, 0.9, 0.0), 6, 0.3, 0.3, 0.3, 0.05);
        if (objective.destroyed() && !objective.announcedLoss) {
            objective.announcedLoss = true;
            attacker.getWorld().playSound(objective.anchor, Sound.ENTITY_IRON_GOLEM_DEATH, SoundCategory.HOSTILE, 1.0f, 0.7f);
            attacker.getWorld().spawnParticle(Particle.EXPLOSION, objective.anchor, 6, 0.6, 0.6, 0.6, 0.0);
        } else {
            attacker.getWorld().playSound(objective.anchor, Sound.ENTITY_IRON_GOLEM_HURT, SoundCategory.HOSTILE, 0.6f, 1.2f);
        }
    }

    /** A shooter may stand off; everyone else has to close to melee range. */
    private boolean withinReach(Location from, Objective objective, boolean ranged, double rangedReach) {
        double effectiveReach = ranged ? Math.max(this.reach, rangedReach) : this.reach;
        return from.distanceSquared(objective.anchor) <= effectiveReach * effectiveReach;
    }

    /** An attacker within reach of a living objective holds its position. */
    @Override
    public boolean holdsPosition(Location from, boolean ranged, double rangedReach) {
        if (!this.ready) {
            return false;
        }
        Objective objective = this.nearestAlive(from);
        return objective != null && this.withinReach(from, objective, ranged, rangedReach);
    }

    /**
     * Fires a purely cosmetic arrow at the target. The target has no hitbox, so a real shot could
     * not connect; the damage is applied by the plugin. The arrow deals no damage and is removed
     * shortly afterwards.
     */
    private void shootAt(LivingEntity shooter, Objective objective) {
        Location eye = shooter.getEyeLocation();
        Vector direction = objective.anchor.clone().add(0.0, 0.8, 0.0).toVector().subtract(eye.toVector());
        if (direction.lengthSquared() < 1.0E-4) {
            return;
        }
        Arrow arrow = shooter.launchProjectile(Arrow.class, direction.normalize().multiply(1.8));
        arrow.setDamage(0.0);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        shooter.getWorld().playSound(eye, Sound.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 0.8f, 1.0f);
        Scheduler.runEntityLater(this.plugin, arrow, () -> {
            if (arrow.isValid() && !arrow.isDead()) {
                arrow.remove();
            }
        }, 60L);
    }

    private Objective nearestAlive(Location from) {
        Objective best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Objective objective : this.objectives) {
            if (objective.destroyed()) continue;
            if (!objective.anchor.getWorld().equals(from.getWorld())) continue;
            double distance = objective.anchor.distanceSquared(from);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = objective;
        }
        return best;
    }

    @Override
    public Location navTarget(Location from) {
        Objective objective = this.nearestAlive(from);
        return objective == null ? null : objective.anchor.clone();
    }

    /**
     * Only the entity style offers something that can be targeted. Mob AI discards a
     * {@code setTarget} aimed at an entity it would not attack on its own, so display-style
     * attackers advance via the path from {@link #navTarget}.
     */
    @Override
    public LivingEntity attackTarget(Location from) {
        if (this.displayStyle) {
            return null;
        }
        Objective objective = this.nearestAlive(from);
        if (objective == null) {
            return null;
        }
        Entity visual = objective.visual;
        return visual instanceof LivingEntity && visual.isValid() && !visual.isDead() ? (LivingEntity)visual : null;
    }

    @Override
    public boolean isTracked(UUID id) {
        return id != null && this.tracked.contains(id);
    }

    @Override
    public void cleanup() {
        this.ready = false;
        for (Objective objective : this.objectives) {
            this.despawn(objective.visual);
            this.despawn(objective.label);
            objective.visual = null;
            objective.label = null;
        }
        this.tracked.clear();
    }

    private void despawn(Entity entity) {
        if (entity == null) {
            return;
        }
        Scheduler.runOnEntity(this.plugin, entity, () -> {
            if (entity.isValid() && !entity.isDead()) {
                entity.remove();
            }
        });
    }

    @Override
    public List<String> describe() {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(Lang.get("raid.status.target-objectives",
                "alive", String.valueOf(this.objectives.size() - this.destroyedCount()),
                "total", String.valueOf(this.objectives.size())));
        int index = 1;
        for (Objective objective : this.objectives) {
            lines.add(Lang.get("raid.status.target-objective",
                    "index", String.valueOf(index++),
                    "health", String.valueOf((long)Math.ceil(objective.health())),
                    "max", String.valueOf((long)objective.maxHealth),
                    "x", String.valueOf(objective.anchor.getBlockX()),
                    "y", String.valueOf(objective.anchor.getBlockY()),
                    "z", String.valueOf(objective.anchor.getBlockZ())));
        }
        return lines;
    }

    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}
