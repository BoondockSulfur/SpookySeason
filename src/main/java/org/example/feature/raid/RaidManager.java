package org.example.feature.raid;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Attributes;
import org.example.util.Scheduler;
import org.example.util.YamlSaver;

/**
 * Wave-based assault on a defended target.
 *
 * <p>Shape of it: announcement with a countdown, then wave after wave of attackers walking at the
 * target, with a breather in between. Holding every wave wins; if the target falls (see
 * {@link RaidTarget}) the raid is lost.
 *
 * <p><b>Folia:</b> the state machine ticks on the global scheduler and never touches blocks or
 * entities directly. Spawns go through the region scheduler of the target location, any work on an
 * attacker through that entity's scheduler. Tasks are only ever cancelled through
 * {@link Scheduler.TaskHandle}.
 *
 * <p>Existing features are left alone: the wave leader is the plugin's own Halloween boss, spawned
 * via {@link org.example.feature.HalloweenBossManager#forceSpawnAt(Location)}.
 */
public class RaidManager implements Listener {

    public enum State {
        IDLE,
        COUNTDOWN,
        WAVE,
        BREAK,
        AFTERMATH
    }

    private static final int TARGET_READY_GRACE_SECONDS = 15;
    private static final int LEADER_GRACE_SECONDS = 15;
    /** How far below the surface a spawn point may be looked for, in blocks. */
    private static final int SURFACE_SEARCH_DEPTH = 6;

    private final Plugin plugin;
    private Scheduler.TaskHandle tickTask;

    // State is written by the global tick and read from events on arbitrary region threads.
    private volatile State state = State.IDLE;
    private volatile RaidTarget target;
    private volatile RaidSettings settings;
    private volatile int waveIndex;

    private final List<Entity> attackers = new CopyOnWriteArrayList<Entity>();
    private final Set<UUID> attackerIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final AtomicInteger remainingSpawns = new AtomicInteger();
    private final AtomicInteger spawnsInFlight = new AtomicInteger();
    private final AtomicInteger spawnAttempts = new AtomicInteger();
    // Failed spawns always have the same cause. Without this brake a wave puts dozens of
    // identical warnings in the log and buries everything else.
    private final AtomicBoolean spawnFailureLogged = new AtomicBoolean();
    // How many of each archetype have spawned this wave — for maxPerWave and minPerWave.
    private final java.util.Map<String, Integer> spawnedThisWave = new ConcurrentHashMap<String, Integer>();
    private final NamespacedKey targetDamageKey;
    private final NamespacedKey rangedKey;
    private final NamespacedKey lastDistanceKey;

    private final BossBar waveBar;
    private final BossBar targetBar;

    private int waveQuota;
    private int secondsLeft;
    private int waveSeconds;
    private int retargetTicker;
    private int fogTicker;
    private int readyGrace;
    private int aftermathSeconds;
    private Location aftermathCenter;

    private boolean leaderRequested;
    private boolean leaderSeen;
    private int leaderGrace;

    private volatile boolean musicPlaying;
    private Location musicLoc;

    private final YamlConfiguration stateCfg;
    private final YamlSaver stateSaver;

    public RaidManager(Plugin plugin) {
        this.plugin = plugin;
        this.targetDamageKey = new NamespacedKey(plugin, "spooky_raid_target_damage");
        this.rangedKey = new NamespacedKey(plugin, "spooky_raid_ranged");
        this.lastDistanceKey = new NamespacedKey(plugin, "spooky_raid_last_distance");
        this.waveBar = Bukkit.createBossBar(Lang.get("raid.bar.wave", "wave", "1", "total", "1", "alive", "0"),
                BarColor.RED, BarStyle.SEGMENTED_10, new BarFlag[0]);
        this.waveBar.setVisible(false);
        this.targetBar = Bukkit.createBossBar(Lang.get("raid.bar.target", new String[0]),
                BarColor.GREEN, BarStyle.SEGMENTED_20, new BarFlag[0]);
        this.targetBar.setVisible(false);
        File stateFile = new File(plugin.getDataFolder(), "raid-state.yml");
        this.stateCfg = YamlConfiguration.loadConfiguration(stateFile);
        this.stateSaver = new YamlSaver(plugin, stateFile, this::snapshotState);
    }

    // ── Lebenszyklus ────────────────────────────────────────────────────────

    /**
     * Starts the state machine. A raid already in progress is deliberately NOT aborted:
     * {@code /spooky reload} must not pull the ground out from under the defenders mid-assault.
     * The running raid keeps its frozen configuration.
     */
    public void start() {
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        this.tickTask = Scheduler.runTimer(this.plugin, this::tick, 20L, 20L);
    }

    /** Aborts everything and cleans up — for onDisable and {@code /spooky off}. */
    public void stop() {
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        this.abort(false);
        this.stateSaver.flushIfPending();
    }

    public State state() {
        return this.state;
    }

    public boolean isRunning() {
        return this.state != State.IDLE;
    }

    public int currentWave() {
        return this.waveIndex;
    }

    /** Does this entity belong to a running raid? Shields it from the entity cleanup. */
    public boolean isTracked(UUID id) {
        if (id == null) {
            return false;
        }
        if (this.attackerIds.contains(id)) {
            return true;
        }
        RaidTarget current = this.target;
        return current != null && current.isTracked(id);
    }

    // ── Start / Abbruch ─────────────────────────────────────────────────────

    /**
     * Starts a raid.
     *
     * @return language key of the failure, or {@code null} on success
     */
    public synchronized String begin() {
        if (this.state != State.IDLE) {
            return "raid.error.already-running";
        }
        FileConfiguration cfg = this.plugin.getConfig();
        if (!cfg.getBoolean("raid.enabled", true)) {
            return "raid.error.disabled";
        }
        RaidSettings cfgSnapshot = RaidSettings.from(cfg);
        if (cfgSnapshot.waveCount <= 0) {
            return "raid.error.no-waves";
        }
        if (!cfgSnapshot.hasComposition()) {
            return "raid.error.no-composition";
        }
        RaidTarget newTarget = this.createTarget(cfg);
        if (newTarget == null) {
            return "raid.error.no-target";
        }
        // enabledWorlds is the plugin's safety net, and the raid honours it like everything else.
        if (newTarget.world() == null || !SpookySeason.get().isWorldEnabled(newTarget.world())) {
            return "raid.error.world-disabled";
        }
        // On Peaceful the server refuses every monster spawn, so the raid would run completely
        // empty instead of failing visibly.
        if (newTarget.world().getDifficulty() == Difficulty.PEACEFUL) {
            return "raid.error.peaceful";
        }
        if (!newTarget.prepare()) {
            return newTarget.failure() != null ? newTarget.failure() : "raid.error.no-target";
        }
        this.settings = cfgSnapshot;
        this.target = newTarget;
        this.waveIndex = 0;
        this.waveQuota = 0;
        this.secondsLeft = cfgSnapshot.countdownSeconds;
        this.readyGrace = 0;
        this.fogTicker = 0;
        this.aftermathCenter = null;
        this.participants.clear();
        this.attackerIds.clear();
        this.attackers.clear();
        this.remainingSpawns.set(0);
        this.spawnsInFlight.set(0);
        this.spawnAttempts.set(0);
        for (int wave = 1; wave <= cfgSnapshot.waveCount; ++wave) {
            int minimums = cfgSnapshot.minimumsForWave(wave);
            int quota = cfgSnapshot.quotaForWave(wave);
            if (minimums <= quota) continue;
            this.plugin.getLogger().warning("Raid wave " + wave + " guarantees " + minimums
                    + " attackers through minPerWave but only spawns " + quota
                    + " - some archetypes will not appear. Raise baseMobs/mobsPerWave or lower minPerWave.");
        }
        this.state = State.COUNTDOWN;
        this.broadcast("raid.announce", "seconds", String.valueOf(cfgSnapshot.countdownSeconds));
        this.showTitle("raid.title.incoming", "raid.subtitle.incoming", Sound.ENTITY_WITHER_SPAWN, 0.7f);
        return null;
    }

    /** Aborts a running raid: no victory, no defeat, no rewards. */
    public synchronized void abort(boolean announce) {
        if (this.state == State.IDLE) {
            return;
        }
        if (announce) {
            this.broadcast("raid.aborted", new String[0]);
        }
        this.clearAttackers();
        this.despawnLeader();
        this.stopMusic();
        RaidTarget current = this.target;
        if (current != null) {
            current.cleanup();
        }
        this.finish();
    }

    private RaidTarget createTarget(FileConfiguration cfg) {
        String mode = cfg.getString("raid.target.mode", "objective");
        if ("region".equalsIgnoreCase(mode)) {
            return RegionTarget.from(this.plugin, cfg.getConfigurationSection("raid.target.region"));
        }
        return ObjectiveTarget.from(this.plugin, cfg.getConfigurationSection("raid.target.objective"));
    }

    // ── Zustandsautomat ─────────────────────────────────────────────────────

    private void tick() {
        try {
            switch (this.state) {
                case IDLE: {
                    this.maybeAutoStart();
                    break;
                }
                case COUNTDOWN: {
                    this.tickCountdown();
                    break;
                }
                case WAVE: {
                    this.tickWave();
                    break;
                }
                case BREAK: {
                    this.tickBreak();
                    break;
                }
                case AFTERMATH: {
                    this.tickAftermath();
                    break;
                }
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Raid tick error: " + e.getMessage());
        }
    }

    private void tickCountdown() {
        RaidTarget current = this.target;
        if (current == null) {
            this.finish();
            return;
        }
        if (current.failure() != null) {
            this.failStart(current.failure());
            return;
        }
        current.tick();
        if (this.secondsLeft > 0) {
            if (this.secondsLeft <= 5 || this.secondsLeft % 15 == 0) {
                this.broadcast("raid.countdown", "seconds", String.valueOf(this.secondsLeft));
            }
            --this.secondsLeft;
            return;
        }
        if (!current.isReady()) {
            // The target location is still loading (chunk/region). A limited grace period, then
            // give up — otherwise the raid hangs in the countdown forever without anyone noticing.
            if (++this.readyGrace > TARGET_READY_GRACE_SECONDS) {
                this.failStart("raid.error.target-timeout");
            }
            return;
        }
        this.beginWave(1);
    }

    private void tickWave() {
        RaidTarget current = this.target;
        RaidSettings cfg = this.settings;
        if (current == null || cfg == null) {
            this.finish();
            return;
        }
        current.tick();
        this.pruneAttackers();
        this.spawnPending(cfg, current);
        this.retarget(cfg, current);
        this.fogPulse(cfg, current.center());
        this.collectParticipants(cfg, current.center());
        this.updateBars(cfg, current);
        if (current.isLost()) {
            this.defeat();
            return;
        }
        ++this.waveSeconds;
        if (cfg.waveTimeoutSeconds > 0 && this.waveSeconds > cfg.waveTimeoutSeconds) {
            this.plugin.getLogger().warning("Raid wave " + this.waveIndex + " hit the timeout - forcing it to end.");
            this.clearAttackers();
            this.finishWave(cfg);
            return;
        }
        if (this.waveComplete()) {
            this.finishWave(cfg);
        }
    }

    private void tickBreak() {
        RaidTarget current = this.target;
        RaidSettings cfg = this.settings;
        if (current == null || cfg == null) {
            this.finish();
            return;
        }
        current.tick();
        this.pruneAttackers();
        this.collectParticipants(cfg, current.center());
        this.updateBars(cfg, current);
        if (current.isLost()) {
            this.defeat();
            return;
        }
        if (this.secondsLeft > 0) {
            if (this.secondsLeft <= 3 || this.secondsLeft % 10 == 0) {
                this.broadcast("raid.break", "seconds", String.valueOf(this.secondsLeft), "wave", String.valueOf(this.waveIndex + 1));
            }
            --this.secondsLeft;
            return;
        }
        this.beginWave(this.waveIndex + 1);
    }

    private void tickAftermath() {
        RaidSettings cfg = this.settings;
        Location center = this.aftermathCenter;
        if (cfg == null || center == null || this.aftermathSeconds <= 0) {
            this.finish();
            return;
        }
        --this.aftermathSeconds;
        Location effectLoc = center.clone();
        Scheduler.runAtLocation(this.plugin, effectLoc, () -> effectLoc.getWorld().spawnParticle(
                Particle.LARGE_SMOKE, effectLoc.clone().add(0.0, 1.5, 0.0), 30, 1.8, 1.2, 1.8, 0.01));
        if (cfg.fogEnabled) {
            this.fogPulse(cfg, center);
        }
    }

    // Deliberately log-only: a configuration or loading error is an admin matter and has no
    // business appearing as raw text in front of the players.
    private void failStart(String langKey) {
        this.plugin.getLogger().warning("Raid could not start: " + Lang.get(langKey, new String[0]));
        this.abort(false);
    }

    // ── Wellen ──────────────────────────────────────────────────────────────

    private void beginWave(int wave) {
        RaidSettings cfg = this.settings;
        RaidTarget current = this.target;
        if (cfg == null || current == null) {
            this.finish();
            return;
        }
        this.waveIndex = wave;
        this.waveQuota = cfg.quotaForWave(wave);
        this.waveSeconds = 0;
        this.retargetTicker = 0;
        this.leaderRequested = false;
        this.leaderSeen = false;
        this.leaderGrace = 0;
        this.remainingSpawns.set(this.waveQuota);
        this.spawnsInFlight.set(0);
        this.spawnAttempts.set(0);
        this.spawnFailureLogged.set(false);
        this.spawnedThisWave.clear();
        this.state = State.WAVE;
        this.startMusic(cfg, current.center());
        this.broadcast("raid.wave-start", "wave", String.valueOf(wave), "total", String.valueOf(cfg.waveCount));
        this.showTitle("raid.title.wave", "raid.subtitle.wave", Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f,
                "wave", String.valueOf(wave), "total", String.valueOf(cfg.waveCount));
        if (cfg.leaderEnabled && wave == cfg.resolvedLeaderWave()) {
            this.requestLeader(cfg, current);
        }
    }

    private void finishWave(RaidSettings cfg) {
        this.despawnLeaderIfDone();
        if (this.waveIndex >= cfg.waveCount) {
            this.victory();
            return;
        }
        this.state = State.BREAK;
        this.secondsLeft = cfg.breakSeconds;
        this.broadcast("raid.wave-cleared", "wave", String.valueOf(this.waveIndex), "total", String.valueOf(cfg.waveCount));
    }

    private boolean waveComplete() {
        if (this.remainingSpawns.get() > 0 || this.spawnsInFlight.get() > 0) {
            return false;
        }
        if (!this.attackers.isEmpty()) {
            return false;
        }
        return !this.leaderStillOut();
    }

    private boolean leaderStillOut() {
        if (!this.leaderRequested) {
            return false;
        }
        boolean active = SpookySeason.get().boss() != null && SpookySeason.get().boss().isActive();
        if (active) {
            this.leaderSeen = true;
            return true;
        }
        if (this.leaderSeen) {
            return false;
        }
        // The spawn runs on a region thread and has not come through yet. If it never does, the
        // wave must not wait on it forever.
        if (++this.leaderGrace > LEADER_GRACE_SECONDS) {
            this.plugin.getLogger().warning("Raid leader never appeared - continuing without it.");
            this.leaderRequested = false;
            return false;
        }
        return true;
    }

    private void requestLeader(RaidSettings cfg, RaidTarget current) {
        if (SpookySeason.get().boss() == null) {
            return;
        }
        Location center = current.center();
        if (center == null) {
            return;
        }
        this.leaderRequested = true;
        if (SpookySeason.get().boss().isActive()) {
            // A boss is already standing in the world (auto spawn or /spookyboss spawn) — that
            // one counts as the leader instead of clearing it away and placing a new one.
            this.leaderSeen = true;
        } else {
            SpookySeason.get().boss().forceSpawnAt(this.ringLocation(center, cfg));
        }
        this.broadcast("raid.leader", new String[0]);
        this.showTitle("raid.title.leader", "raid.subtitle.leader", Sound.ENTITY_WITHER_SPAWN, 0.8f);
    }

    private void despawnLeaderIfDone() {
        this.leaderRequested = false;
        this.leaderSeen = false;
    }

    private void despawnLeader() {
        if (this.leaderRequested && SpookySeason.get().boss() != null) {
            SpookySeason.get().boss().despawnBoss();
        }
        this.leaderRequested = false;
        this.leaderSeen = false;
    }

    // ── Angreifer ───────────────────────────────────────────────────────────

    private Location ringLocation(Location center, RaidSettings cfg) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
        double radius = ThreadLocalRandom.current().nextDouble(cfg.spawnRadiusMin, cfg.spawnRadiusMax);
        return center.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
    }

    private void spawnPending(RaidSettings cfg, RaidTarget current) {
        int remaining = this.remainingSpawns.get();
        if (remaining <= 0) {
            return;
        }
        // Too many failed attempts means there is no permitted spot around the target. Better to
        // run the wave with fewer attackers than to keep rolling dice forever.
        if (this.spawnAttempts.get() > this.waveQuota * 10 + 20) {
            this.plugin.getLogger().warning("Raid could not place " + remaining
                    + " attacker(s) around the target - check raid.waves.spawnRadius* and region protection.");
            this.remainingSpawns.set(0);
            return;
        }
        int room = cfg.maxAlive - this.attackers.size() - this.spawnsInFlight.get();
        int batch = Math.min(Math.min(cfg.spawnPerSecond, room), remaining - this.spawnsInFlight.get());
        for (int i = 0; i < batch; ++i) {
            // With several targets the anchor rotates, so the waves do not all converge on the
            // same target from the same direction.
            Location anchor = current.spawnAnchor();
            if (anchor == null) {
                return;
            }
            this.dispatchSpawn(cfg, anchor);
        }
    }

    private void dispatchSpawn(RaidSettings cfg, Location center) {
        Location candidate = this.ringLocation(center, cfg);
        this.spawnsInFlight.incrementAndGet();
        this.spawnAttempts.incrementAndGet();
        int wave = this.waveIndex;
        try {
            Scheduler.runAtLocation(this.plugin, candidate, () -> {
                try {
                    this.spawnAttacker(cfg, candidate, wave);
                }
                catch (Exception e) {
                    if (this.spawnFailureLogged.compareAndSet(false, true)) {
                        this.plugin.getLogger().warning("Raid attacker spawn failed: " + e.getMessage()
                                + " (further failures in this wave are not logged)");
                    }
                }
                finally {
                    this.spawnsInFlight.decrementAndGet();
                }
            });
        }
        catch (RuntimeException e) {
            // Folia refuses scheduling for a disabled plugin, for instance — without this the
            // counter would stay put and no wave would ever complete.
            this.spawnsInFlight.decrementAndGet();
            throw e;
        }
    }

    private void spawnAttacker(RaidSettings cfg, Location candidate, int wave) {
        Location loc = RaidManager.groundAt(candidate);
        if (loc == null) {
            // No footing here — treated like any other failed attempt and retried elsewhere.
            return;
        }
        if (cfg.respectRegionProtection && !SpookySeason.get().regions().canSpawnAt(loc)) {
            return;
        }
        RaidMob archetype = cfg.pick(wave, this.spawnedThisWave);
        if (archetype == null) {
            // Everything allowed in this wave has used up its quota.
            this.remainingSpawns.set(0);
            return;
        }
        Entity spawned = loc.getWorld().spawnEntity(loc, archetype.type());
        if (!(spawned instanceof LivingEntity)) {
            spawned.remove();
            return;
        }
        this.configureAttacker(cfg, archetype, (LivingEntity)spawned, wave);
        this.spawnedThisWave.merge(archetype.id(), 1, Integer::sum);
        this.attackers.add(spawned);
        this.attackerIds.add(spawned.getUniqueId());
        this.remainingSpawns.decrementAndGet();
        loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0.0, 1.0, 0.0), 12, 0.4, 0.6, 0.4, 0.02);
    }

    /**
     * Finds somewhere to actually stand at these coordinates, or {@code null} if there is nowhere.
     *
     * <p>{@code getHighestBlockYAt} alone is not enough: over a forest it returns the top of the
     * canopy, and attackers were spawning in the treetops. This walks down from there for the
     * first genuinely solid block that is not part of a tree, with two blocks of clear air above
     * it — which also rules out spawning inside water or under an overhang.
     *
     * <p>Must run on the region thread of that location: it reads blocks.
     */
    private static Location groundAt(Location candidate) {
        World world = candidate.getWorld();
        int x = candidate.getBlockX();
        int z = candidate.getBlockZ();
        int top = Math.min(world.getHighestBlockYAt(x, z), world.getMaxHeight() - 3);
        // Only ever look just below the surface. Searching all the way down finds the first cave
        // instead, and an attacker spawned in one is stuck there for the whole raid - measured
        // with withers hovering 56 and 72 blocks under the target.
        int lowest = Math.max(world.getMinHeight() + 1, top - SURFACE_SEARCH_DEPTH);
        for (int y = top; y >= lowest; --y) {
            Material standing = world.getBlockAt(x, y, z).getType();
            if (!standing.isSolid()) continue;
            if (Tag.LEAVES.isTagged(standing) || Tag.LOGS.isTagged(standing)) continue;
            if (!RaidManager.isClear(world.getBlockAt(x, y + 1, z))) continue;
            if (!RaidManager.isClear(world.getBlockAt(x, y + 2, z))) continue;
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    /**
     * Room to stand in. Passable rather than strictly air: grass, flowers and snow layers sit on
     * top of perfectly good ground, and demanding air there rejected the surface and sent the
     * search down into the caves. Liquids are still refused - nobody should spawn in water.
     */
    private static boolean isClear(Block block) {
        return !block.isLiquid() && block.isPassable();
    }

    private void configureAttacker(RaidSettings cfg, RaidMob archetype, LivingEntity le, int wave) {
        le.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        le.getPersistentDataContainer().set(SpookySeason.get().raidMarker(), PersistentDataType.BYTE, (byte)1);
        le.setPersistent(true);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        String name = cfg.resolveName(archetype.nameOr(null), le.getType(), wave);
        if (name != null) {
            le.customName(LegacyComponentSerializer.legacySection().deserialize(name));
            le.setCustomNameVisible(cfg.nameVisible);
        }
        double health = archetype.healthOr(cfg.healthForWave(wave));
        this.setBaseValue(le, Attributes.MAX_HEALTH, health);
        double effectiveMax = le.getAttribute(Attributes.MAX_HEALTH) == null
                ? le.getHealth()
                : le.getAttribute(Attributes.MAX_HEALTH).getValue();
        le.setHealth(Math.min(health, effectiveMax));
        this.setBaseValue(le, Attributes.ATTACK_DAMAGE, archetype.damageOr(cfg.damageForWave(wave)));
        this.setBaseValue(le, Attributes.MOVEMENT_SPEED, archetype.speedOr(cfg.speed));
        // Without a generous follow range the attackers lose the target halfway there.
        this.setBaseValue(le, Attributes.FOLLOW_RANGE, cfg.spawnRadiusMax * 2.0 + 16.0);
        if (archetype.scale() > 0.0 && Attributes.SCALE != null) {
            this.setBaseValue(le, Attributes.SCALE, archetype.scale());
        }
        // Damage against the target depends on the archetype and is read later on the entity's
        // own region thread — hence stored on the entity rather than in a map in the manager.
        le.getPersistentDataContainer().set(this.targetDamageKey, PersistentDataType.DOUBLE,
                archetype.targetDamageOr(cfg.targetDamageForWave(wave)));
        le.getPersistentDataContainer().set(this.rangedKey, PersistentDataType.BYTE,
                (byte)(archetype.isRanged() ? 1 : 0));
        if (!cfg.babies && le instanceof org.bukkit.entity.Zombie) {
            // Baby zombies are small and quick, and nearly impossible to hit in the scrum between
            // defenders and target. Anyone after a predictable event turns them off here.
            ((org.bukkit.entity.Zombie)le).setBaby(false);
        }
        if (cfg.hideBossBars && le instanceof Boss) {
            // Withers and dragons bring their own vanilla boss bar. In a wave of several of them
            // the screen fills with bars and the raid's own two are pushed out of sight. The one
            // bar that should stand out is the wave leader's, and that one is drawn by the plugin,
            // not by the entity — so it is unaffected by this.
            BossBar entityBar = ((Boss)le).getBossBar();
            if (entityBar != null) {
                entityBar.setVisible(false);
                entityBar.removeAll();
            }
        }
        if (le instanceof Wither) {
            // Without this the summoning phase and its explosion run first, before the wither
            // attacks at all — useless for a wave that is already on its way.
            ((Wither)le).setInvulnerabilityTicks(0);
        }
        EntityEquipment eq = le.getEquipment();
        if (eq == null) {
            return;
        }
        if (archetype.hasEquipment()) {
            archetype.equip(eq);
        }
        if (cfg.debug) {
            this.plugin.getLogger().info("[raid-debug] spawn " + archetype.id()
                    + " type=" + le.getType()
                    + " hasEquipment=" + archetype.hasEquipment()
                    + " mainhand=" + (eq.getItemInMainHand() == null ? "null" : eq.getItemInMainHand().getType())
                    + " helmet=" + (eq.getHelmet() == null ? "null" : eq.getHelmet().getType())
                    + " scale=" + archetype.scale()
                    + " ranged=" + archetype.isRanged()
                    + " entityBossBar=" + (le instanceof Boss
                            ? String.valueOf(((Boss)le).getBossBar() == null ? "none" : ((Boss)le).getBossBar().isVisible())
                            : "n/a"));
        }
        if (cfg.pumpkinHeads && (eq.getHelmet() == null || eq.getHelmet().getType() == Material.AIR)) {
            // Serves two purposes: it looks like Halloween AND the undead do not burn when the
            // raid runs into daylight. An archetype's own helmet takes precedence.
            eq.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            eq.setHelmetDropChance(0.0f);
        }
    }

    private void setBaseValue(LivingEntity entity, org.bukkit.attribute.Attribute attribute, double value) {
        org.bukkit.attribute.AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        attr.setBaseValue(value);
    }

    private void pruneAttackers() {
        for (Entity a : this.attackers) {
            if (a.isValid() && !a.isDead()) continue;
            this.attackers.remove(a);
            this.attackerIds.remove(a.getUniqueId());
        }
    }

    /**
     * Once per second per attacker: the target check always, re-targeting only on the configured
     * interval. The check must NOT be throttled along with it — in region mode an attacker could
     * otherwise cross the region between two ticks without ever counting as a breach.
     */
    private void retarget(RaidSettings cfg, RaidTarget current) {
        boolean retargetNow = ++this.retargetTicker >= cfg.retargetSeconds;
        if (retargetNow) {
            this.retargetTicker = 0;
        }
        double waveDamage = cfg.targetDamageForWave(this.waveIndex);
        double aggroSquared = cfg.playerAggroRange * cfg.playerAggroRange;
        for (Entity a : this.attackers) {
            // Reading the location, setting a target and kicking off pathfinding all belong on
            // the attacker's region thread — the global tick must not touch it.
            Scheduler.runOnEntity(this.plugin, a, () -> {
                if (!a.isValid() || a.isDead()) {
                    return;
                }
                double attackerDamage = a.getPersistentDataContainer()
                        .getOrDefault(this.targetDamageKey, PersistentDataType.DOUBLE, waveDamage);
                boolean attackerRanged = a.getPersistentDataContainer()
                        .getOrDefault(this.rangedKey, PersistentDataType.BYTE, (byte)0) == (byte)1;
                current.inspectAttacker(a, attackerDamage, attackerRanged, cfg.rangedReach);
                if (!(a instanceof Mob) || !a.isValid() || a.isDead()) {
                    return;
                }
                Mob mob = (Mob)a;
                Location from = a.getLocation();
                // Every second, not on the retarget interval: a single impulse every few seconds
                // is damped away long before the next one, which is exactly what left withers
                // drifting 70 blocks off the target.
                this.unstick(cfg, mob, from, current.navTarget(from));
                if (!retargetNow) {
                    return;
                }
                LivingEntity currentTarget = mob.getTarget();
                if (currentTarget != null && (!currentTarget.isValid() || currentTarget.isDead())) {
                    currentTarget = null;
                }
                if (currentTarget != null && !cfg.friendlyFire
                        && this.attackerIds.contains(currentTarget.getUniqueId())) {
                    // Locked on to one of its own. Cancelling the damage alone is not enough —
                    // the mob would stand there swinging at an ally instead of advancing.
                    mob.setTarget(null);
                    currentTarget = null;
                }
                if (currentTarget != null && !this.keepsTarget(cfg, currentTarget, from, aggroSquared)) {
                    // Anything else it picked up on its own is a distraction. Withers are the
                    // obvious case: they attack every non-undead mob, so one stray chicken parks
                    // a wither twenty-five blocks off the target for the rest of the wave.
                    mob.setTarget(null);
                    currentTarget = null;
                }
                if (currentTarget != null) {
                    return;
                }
                LivingEntity attackTarget = current.attackTarget(from);
                Location nav = current.navTarget(from);
                if (attackTarget != null) {
                    mob.setTarget(attackTarget);
                }
                if (attackTarget == null || mob.getTarget() == null) {
                    // No acceptable target (or the AI discarded it again immediately) — then at
                    // least refresh the path.
                    if (nav != null) {
                        mob.getPathfinder().moveTo(nav, 1.1);
                    }
                }
                if (cfg.debug) {
                    LivingEntity after = mob.getTarget();
                    this.plugin.getLogger().info("[raid-debug] " + a.getType()
                            + " at " + from.getBlockX() + "/" + from.getBlockY() + "/" + from.getBlockZ()
                            + " dist=" + (nav == null ? "?" : String.format("%.1f", from.distance(nav)))
                            + " wanted=" + (attackTarget == null ? "none" : attackTarget.getType().toString())
                            + " actualTarget=" + (after == null ? "none" : after.getType().toString())
                            + " pathing=" + mob.getPathfinder().hasPath()
                            + " name=" + (a.customName() == null
                                    ? "none"
                                    : LegacyComponentSerializer.legacySection().serialize(a.customName()))
                            + " nameShown=" + a.isCustomNameVisible());
                }
            });
        }
    }

    /**
     * Whether an attacker may keep the target its own AI picked.
     *
     * <p>With {@code focus: players} vanilla decides, as before. With {@code focus: target} only a
     * defender who is actually in the way counts — every other creature the mob wandered into is
     * dropped, so it carries on towards the objective.
     */
    private boolean keepsTarget(RaidSettings cfg, LivingEntity target, Location from, double aggroSquared) {
        if (!cfg.focusTarget) {
            return true;
        }
        if (!(target instanceof Player)) {
            return false;
        }
        return target.getWorld().equals(from.getWorld())
                && target.getLocation().distanceSquared(from) <= aggroSquared;
    }

    /**
     * Pushes along an attacker that is not getting anywhere.
     *
     * <p>A path is not enough for everything. A wither moves by its own flight control, so
     * {@code Pathfinder.moveTo} leaves it hovering: measured with two of them parked 34 and 29
     * blocks off the target, unmoved for 45 seconds, while a path was supposedly set. Rather than
     * special-casing mob types this watches whether the distance is actually shrinking and nudges
     * whatever is not making progress; ground mobs caught on terrain benefit from the same.
     *
     * <p>The previous distance rides along on the entity, since this runs on its region thread.
     */
    private void unstick(RaidSettings cfg, Mob mob, Location from, Location nav) {
        if (cfg.unstickSpeed <= 0.0 || nav == null || !nav.getWorld().equals(from.getWorld())) {
            return;
        }
        double distance = from.distance(nav);
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        double previous = pdc.getOrDefault(this.lastDistanceKey, PersistentDataType.DOUBLE, -1.0);
        pdc.set(this.lastDistanceKey, PersistentDataType.DOUBLE, distance);
        if (distance <= cfg.unstickMinDistance || previous < 0.0) {
            return;
        }
        if (distance < previous - 0.5) {
            return;
        }
        Vector push = nav.toVector().subtract(from.toVector());
        if (push.lengthSquared() < 1.0E-4) {
            return;
        }
        mob.setVelocity(push.normalize().multiply(cfg.unstickSpeed));
    }

    private void clearAttackers() {
        for (Entity a : this.attackers) {
            Scheduler.runOnEntity(this.plugin, a, () -> {
                if (a.isValid() && !a.isDead()) {
                    a.remove();
                }
            });
        }
        this.attackers.clear();
        this.attackerIds.clear();
        this.remainingSpawns.set(0);
        this.spawnsInFlight.set(0);
    }

    // ── Ausgang ─────────────────────────────────────────────────────────────

    private void victory() {
        RaidSettings cfg = this.settings;
        this.broadcast("raid.victory", "waves", String.valueOf(this.waveIndex));
        this.showTitle("raid.title.victory", "raid.subtitle.victory", Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
        this.clearAttackers();
        this.despawnLeader();
        this.stopMusic();
        if (cfg != null) {
            this.runRewards(cfg.victoryCommands);
        }
        RaidTarget current = this.target;
        if (current != null) {
            current.cleanup();
        }
        this.finish();
    }

    private void defeat() {
        RaidSettings cfg = this.settings;
        RaidTarget current = this.target;
        this.aftermathCenter = current == null ? null : current.center();
        this.broadcast("raid.defeat", "wave", String.valueOf(this.waveIndex));
        this.showTitle("raid.title.defeat", "raid.subtitle.defeat", Sound.ENTITY_WITHER_DEATH, 0.8f);
        this.clearAttackers();
        this.despawnLeader();
        this.stopMusic();
        if (cfg != null) {
            this.runRewards(cfg.consolationCommands);
        }
        if (current != null) {
            current.cleanup();
        }
        this.target = null;
        this.hideBars();
        if (cfg != null && cfg.defeatEffectEnabled && cfg.defeatEffectSeconds > 0 && this.aftermathCenter != null) {
            // The place stays visibly marked for a while — particles and darkness, no block
            // changes. After that the raid returns to idle on its own.
            this.aftermathSeconds = cfg.defeatEffectSeconds;
            this.fogTicker = 0;
            this.state = State.AFTERMATH;
            return;
        }
        this.finish();
    }

    private void finish() {
        this.state = State.IDLE;
        this.target = null;
        this.settings = null;
        this.waveIndex = 0;
        this.waveQuota = 0;
        this.secondsLeft = 0;
        this.aftermathSeconds = 0;
        this.aftermathCenter = null;
        this.leaderRequested = false;
        this.leaderSeen = false;
        this.participants.clear();
        this.hideBars();
    }

    private void runRewards(List<String> commands) {
        if (commands.isEmpty() || this.participants.isEmpty()) {
            return;
        }
        int wave = this.waveIndex;
        for (UUID id : Set.copyOf(this.participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            List<String> resolved = commands.stream()
                    .map(c -> c.replace("{player}", p.getName()).replace("{wave}", String.valueOf(wave)))
                    .toList();
            // The reward commands touch the inventory — on Folia only from the player's region
            // thread, never from the global tick.
            Scheduler.runOnEntity(this.plugin, p, () -> {
                if (!p.isOnline()) {
                    return;
                }
                for (String c : resolved) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
                }
            });
        }
    }

    // ── Stimmung ────────────────────────────────────────────────────────────

    private void fogPulse(RaidSettings cfg, Location center) {
        if (!cfg.fogEnabled || center == null) {
            return;
        }
        if (++this.fogTicker < cfg.fogIntervalSeconds) {
            return;
        }
        this.fogTicker = 0;
        for (Player p : this.nearbyPlayers(center, cfg.effectRadius)) {
            if (SpookySeason.get().prefs().isOptedOut(p.getUniqueId())) continue;
            Scheduler.runAtLocation(this.plugin, p.getLocation(), () -> {
                if (!p.isOnline()) {
                    return;
                }
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, cfg.fogDurationTicks, 0, false, false, false));
            });
        }
    }

    private void startMusic(RaidSettings cfg, Location center) {
        if (this.musicPlaying || center == null || cfg.discId.isEmpty() || !SpookySeason.get().jukebox().isAvailable()) {
            return;
        }
        Location loc = center.clone();
        this.musicLoc = loc;
        Scheduler.runAtLocation(this.plugin, loc, () -> {
            if (SpookySeason.get().jukebox().playDisc(loc, cfg.discId, cfg.discLoop)) {
                this.musicPlaying = true;
            }
        });
    }

    private void stopMusic() {
        if (this.musicPlaying && this.musicLoc != null) {
            SpookySeason.get().jukebox().stopPlayback(this.musicLoc);
        }
        this.musicPlaying = false;
        this.musicLoc = null;
    }

    // ── Anzeige ─────────────────────────────────────────────────────────────

    private void updateBars(RaidSettings cfg, RaidTarget current) {
        Location center = current.center();
        if (center == null) {
            this.hideBars();
            return;
        }
        List<Player> viewers = this.nearbyPlayers(center, cfg.announceRadius);
        if (viewers.isEmpty()) {
            this.hideBars();
            return;
        }
        int outstanding = this.remainingSpawns.get() + this.spawnsInFlight.get() + this.attackers.size();
        double waveProgress = this.waveQuota <= 0
                ? 0.0
                : Math.max(0.0, Math.min(1.0, 1.0 - (double)outstanding / (double)this.waveQuota));
        this.waveBar.setTitle(Lang.get("raid.bar.wave",
                "wave", String.valueOf(this.waveIndex),
                "total", String.valueOf(cfg.waveCount),
                "alive", String.valueOf(this.attackers.size())));
        this.waveBar.setProgress(waveProgress);
        double integrity = Math.max(0.0, Math.min(1.0, current.integrity()));
        this.targetBar.setTitle(Lang.get("raid.bar.target", "percent", String.valueOf((int)Math.round(integrity * 100.0))));
        this.targetBar.setProgress(integrity);
        this.targetBar.setColor(integrity > 0.5 ? BarColor.GREEN : (integrity > 0.25 ? BarColor.YELLOW : BarColor.RED));
        this.syncBar(this.waveBar, viewers);
        this.syncBar(this.targetBar, viewers);
    }

    // Send only the difference, as the blood moon bar does — otherwise that is two pointless
    // packets per player per second.
    private void syncBar(BossBar bar, List<Player> viewers) {
        Set<Player> wanted = new HashSet<Player>(viewers);
        for (Player p : new ArrayList<Player>(bar.getPlayers())) {
            if (wanted.contains(p)) continue;
            bar.removePlayer(p);
        }
        Set<Player> currentViewers = new HashSet<Player>(bar.getPlayers());
        for (Player p : wanted) {
            if (currentViewers.contains(p)) continue;
            bar.addPlayer(p);
        }
        if (!bar.isVisible()) {
            bar.setVisible(true);
        }
    }

    private void hideBars() {
        if (this.waveBar.isVisible()) {
            this.waveBar.setVisible(false);
        }
        this.waveBar.removeAll();
        if (this.targetBar.isVisible()) {
            this.targetBar.setVisible(false);
        }
        this.targetBar.removeAll();
    }

    private void collectParticipants(RaidSettings cfg, Location center) {
        if (center == null) {
            return;
        }
        for (Player p : this.nearbyPlayers(center, cfg.participationRadius)) {
            this.participants.add(p.getUniqueId());
        }
    }

    private List<Player> nearbyPlayers(Location center, double radius) {
        ArrayList<Player> result = new ArrayList<Player>();
        if (center == null || center.getWorld() == null) {
            return result;
        }
        double radiusSquared = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) > radiusSquared) continue;
            result.add(p);
        }
        return result;
    }

    private Location announceAnchor() {
        RaidTarget current = this.target;
        if (current != null && current.center() != null) {
            return current.center();
        }
        return this.aftermathCenter;
    }

    private void broadcast(String key, String ... replacements) {
        RaidSettings cfg = this.settings;
        Location center = this.announceAnchor();
        if (center == null) {
            return;
        }
        String message = Lang.get(key, replacements);
        for (Player p : this.nearbyPlayers(center, cfg == null ? 128.0 : cfg.announceRadius)) {
            p.sendMessage(message);
        }
    }

    private void showTitle(String titleKey, String subtitleKey, Sound sound, float pitch, String ... replacements) {
        RaidSettings cfg = this.settings;
        Location center = this.announceAnchor();
        if (center == null) {
            return;
        }
        Component title = LegacyComponentSerializer.legacySection().deserialize(Lang.get(titleKey, replacements));
        Component subtitle = LegacyComponentSerializer.legacySection().deserialize(Lang.get(subtitleKey, replacements));
        Title.Times times = Title.Times.times(Duration.ofMillis(300L), Duration.ofMillis(2500L), Duration.ofMillis(700L));
        for (Player p : this.nearbyPlayers(center, cfg == null ? 128.0 : cfg.announceRadius)) {
            Scheduler.runAtLocation(this.plugin, p.getLocation(), () -> {
                if (!p.isOnline()) {
                    return;
                }
                p.showTitle(Title.title(title, subtitle, times));
                p.playSound(p.getLocation(), sound, SoundCategory.HOSTILE, 1.0f, pitch);
            });
        }
    }

    /** Multi-line overview for {@code /spookyraid status}. */
    public List<String> statusLines() {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(Lang.get("raid.status.header", new String[0]));
        lines.add(Lang.get("raid.status.state", "state", this.state.name()));
        RaidSettings cfg = this.settings;
        RaidTarget current = this.target;
        if (this.state == State.IDLE || cfg == null) {
            lines.add(Lang.get("raid.status.idle", new String[0]));
            return lines;
        }
        lines.add(Lang.get("raid.status.wave",
                "wave", String.valueOf(this.waveIndex),
                "total", String.valueOf(cfg.waveCount),
                "alive", String.valueOf(this.attackers.size()),
                "pending", String.valueOf(this.remainingSpawns.get())));
        if (current != null) {
            lines.addAll(current.describe());
        }
        lines.add(Lang.get("raid.status.participants", "count", String.valueOf(this.participants.size())));
        return lines;
    }

    // ── Zeitplan ────────────────────────────────────────────────────────────

    private void maybeAutoStart() {
        FileConfiguration cfg = this.plugin.getConfig();
        if (!cfg.getBoolean("raid.enabled", true) || !cfg.getBoolean("raid.schedule.enabled", false)) {
            return;
        }
        int startDay = cfg.getInt("raid.schedule.startDay", 0);
        int endDay = cfg.getInt("raid.schedule.endDay", 0);
        if (startDay <= 0 || endDay <= 0) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (now.getMonthValue() != cfg.getInt("raid.schedule.month", 10)) {
            return;
        }
        int day = now.getDayOfMonth();
        if (day < startDay || day > endDay) {
            return;
        }
        LocalTime startTime = RaidManager.parseTime(cfg.getString("raid.schedule.startTime", "20:00"));
        if (startTime == null) {
            return;
        }
        LocalTime nowTime = now.toLocalTime();
        // Only a limited window after the start time — otherwise a server started at midnight
        // would still kick off that day's raid after the fact.
        int windowMinutes = Math.max(1, cfg.getInt("raid.schedule.windowMinutes", 15));
        if (nowTime.isBefore(startTime) || nowTime.isAfter(startTime.plusMinutes(windowMinutes))) {
            return;
        }
        String today = LocalDate.now().toString();
        synchronized (this.stateCfg) {
            if (today.equals(this.stateCfg.getString("lastAutoStart", ""))) {
                return;
            }
        }
        // No defenders, no assault: the day stays open so the raid can still start once somebody
        // turns up inside the window.
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        synchronized (this.stateCfg) {
            this.stateCfg.set("lastAutoStart", today);
        }
        this.stateSaver.save();
        String error = this.begin();
        if (error != null) {
            this.plugin.getLogger().warning("Scheduled raid could not start: " + Lang.get(error, new String[0]));
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        }
        catch (DateTimeParseException e) {
            return null;
        }
    }

    private String snapshotState() {
        synchronized (this.stateCfg) {
            return this.stateCfg.saveToString();
        }
    }

    // ── Events ──────────────────────────────────────────────────────────────

    /**
     * The target is invulnerable as far as the world is concerned — the plugin tracks its health
     * itself (see {@link ObjectiveTarget}). That rules out players, fire, explosions and stray
     * mobs alike; the attackers do their damage through proximity.
     */
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onTargetDamage(EntityDamageEvent e) {
        RaidTarget current = this.target;
        if (current == null || !current.isTracked(e.getEntity().getUniqueId())) {
            return;
        }
        e.setCancelled(true);
    }

    /**
     * Keeps the raid from rearranging the world. Without this a single wither wave leaves the
     * defended village full of craters — and the whole point is defending it, not rebuilding it.
     * Explosions still hurt players, only the block damage is dropped.
     */
    @EventHandler(ignoreCancelled=true)
    public void onRaidExplosion(EntityExplodeEvent e) {
        RaidSettings cfg = this.settings;
        if (cfg == null || !cfg.noGriefing || !this.isRaidEntity(e.getEntity())) {
            return;
        }
        e.blockList().clear();
    }

    /** Same idea for the blocks a wither smashes on its way through. */
    @EventHandler(ignoreCancelled=true)
    public void onRaidBlockChange(EntityChangeBlockEvent e) {
        RaidSettings cfg = this.settings;
        if (cfg == null || !cfg.noGriefing || !this.isRaidEntity(e.getEntity())) {
            return;
        }
        e.setCancelled(true);
    }

    /** Resolves projectiles back to whoever fired them, so wither skulls count too. */
    private boolean isRaidEntity(Entity entity) {
        Entity source = RaidManager.resolveSource(entity);
        return source != null && this.attackerIds.contains(source.getUniqueId());
    }

    /** Resolves a projectile back to whoever fired it; anything else is returned unchanged. */
    private static Entity resolveSource(Entity entity) {
        if (entity instanceof Projectile) {
            ProjectileSource shooter = ((Projectile)entity).getShooter();
            return shooter instanceof Entity ? (Entity)shooter : null;
        }
        return entity;
    }

    /**
     * Attackers do not fight each other.
     *
     * <p>A stray arrow is enough to start it: a skeleton hits a zombie, the zombie turns on the
     * skeleton and kills it, and the wave thins itself out before it ever reaches the target. That
     * goes for wither skulls and creeper blasts as well, which is why the damager is resolved back
     * through projectiles.
     */
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onAttackerInfighting(EntityDamageByEntityEvent e) {
        RaidSettings cfg = this.settings;
        if (cfg == null || cfg.friendlyFire) {
            return;
        }
        if (!this.attackerIds.contains(e.getEntity().getUniqueId())) {
            return;
        }
        Entity damager = RaidManager.resolveSource(e.getDamager());
        if (damager != null && this.attackerIds.contains(damager.getUniqueId())) {
            e.setCancelled(true);
        }
    }

    /**
     * The other half of not fighting each other: damage over time.
     *
     * <p>Cancelling the hit is not enough. A wither skull applies the wither effect, a witch
     * throws poison — and that damage arrives later as a plain EntityDamageEvent with no damager
     * attached, so the handler above never sees it. Measured: with infighting supposedly off, a
     * wave still lost attackers to their own withers.
     */
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onAttackerEffectDamage(EntityDamageEvent e) {
        RaidSettings cfg = this.settings;
        if (cfg == null || cfg.friendlyFire) {
            return;
        }
        if (!this.attackerIds.contains(e.getEntity().getUniqueId())) {
            return;
        }
        switch (e.getCause()) {
            case WITHER:
            case POISON:
            case MAGIC: {
                e.setCancelled(true);
                break;
            }
            default: {
                break;
            }
        }
    }

    /** Stops an ally's effect landing at all, so attackers do not walk around visibly withering. */
    @EventHandler(ignoreCancelled=true)
    public void onAttackerPotionEffect(EntityPotionEffectEvent e) {
        RaidSettings cfg = this.settings;
        if (cfg == null || cfg.friendlyFire) {
            return;
        }
        if (e.getCause() != EntityPotionEffectEvent.Cause.ATTACK) {
            return;
        }
        if (this.attackerIds.contains(e.getEntity().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (!this.attackerIds.remove(id)) {
            return;
        }
        this.attackers.removeIf(a -> id.equals(a.getUniqueId()));
        RaidSettings cfg = this.settings;
        if (cfg != null && !cfg.dropLoot) {
            // Otherwise the raid can be abused as a mob farm.
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            this.participants.add(killer.getUniqueId());
        }
    }

}
