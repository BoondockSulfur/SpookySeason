/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.SoundCategory
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.boss.BarColor
 *  org.bukkit.boss.BarFlag
 *  org.bukkit.boss.BarStyle
 *  org.bukkit.boss.BossBar
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 */
package org.example.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Attributes;
import org.example.util.Scheduler;

public class HauntedNightManager {
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;
    private static final double NIGHT_DURATION = 10000.0;
    private final Plugin plugin;
    private BossBar bar;
    private Scheduler.TaskHandle tickTask;
    // stop() kommt vom Command-/Main-Thread, der Tick vom Global-Scheduler — beide fassen die Map an.
    private final Map<UUID, Integer> ghostCooldown = new ConcurrentHashMap<UUID, Integer>();
    // Auf Folia mutieren Region-Thread-Lambdas die Liste, während der Global-Tick sie bereinigt.
    private final List<Entity> activeGhosts = new CopyOnWriteArrayList<Entity>();
    private final NamespacedKey GHOST_MARKER;
    private boolean enabled;
    private double cfgAmbientVol;
    private double cfgGhostVol;
    private int ghostSpawnEvery;
    private int ghostPerPlayer;
    private int ghostLifetimeTicks;
    private boolean fogEnabled;
    private int fogIntervalSeconds;
    private int fogDurationTicks;
    private volatile boolean cjbPlaying;
    private Location cjbPlaybackLoc;
    private int fogTicker;

    public HauntedNightManager(Plugin plugin) {
        this.plugin = plugin;
        this.loadConfig();
        this.bar = Bukkit.createBossBar((String)Lang.get("haunted.bossbar", new String[0]), (BarColor)BarColor.PURPLE, (BarStyle)BarStyle.SEGMENTED_10, (BarFlag[])new BarFlag[0]);
        this.bar.setVisible(false);
        this.GHOST_MARKER = new NamespacedKey(plugin, "spooky_ghost");
    }

    private void loadConfig() {
        FileConfiguration cfg = this.plugin.getConfig();
        this.enabled = cfg.getBoolean("hauntedNight.enabled", true);
        this.cfgAmbientVol = cfg.getDouble("hauntedNight.ambientVolume", 0.7);
        this.cfgGhostVol = cfg.getDouble("hauntedNight.ghostVolume", 0.5);
        this.ghostSpawnEvery = cfg.getInt("hauntedNight.ghostSpawnEverySeconds", 25);
        this.ghostPerPlayer = cfg.getInt("hauntedNight.ghostPerPlayer", 1);
        this.ghostLifetimeTicks = this.ghostSpawnEvery * 20;
        this.fogEnabled = cfg.getBoolean("fog.enabled", true);
        this.fogIntervalSeconds = cfg.getInt("fog.intervalSeconds", 60);
        this.fogDurationTicks = cfg.getInt("fog.durationTicks", 100);
    }

    public void start() {
        this.stop();
        this.loadConfig();
        this.bar.setTitle(Lang.get("haunted.bossbar", new String[0]));
        this.fogTicker = 0;
        this.tickTask = Scheduler.runTimer(this.plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        this.hideBar();
        this.ghostCooldown.clear();
        this.removeAllGhosts();
        this.stopCustomJukebox();
    }

    private void removeAllGhosts() {
        for (Entity ghost : this.activeGhosts) {
            if (!ghost.isValid() || ghost.isDead()) continue;
            // Auf Folia darf remove() nur vom Region-Thread des Ghosts laufen.
            Scheduler.runAtLocation(this.plugin, ghost.getLocation(), () -> {
                if (ghost.isValid() && !ghost.isDead()) {
                    ghost.remove();
                }
            });
        }
        this.activeGhosts.clear();
    }

    private void tick() {
        try {
            this.tickInternal();
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("HauntedNight tick error: " + e.getMessage());
        }
    }

    private void tickInternal() {
        boolean useCjb;
        if (!SpookySeason.get().isSeasonActive() || !this.enabled) {
            this.hideBar();
            this.stopCustomJukebox();
            return;
        }
        this.activeGhosts.removeIf(g -> !g.isValid() || g.isDead());
        this.ghostCooldown.keySet().removeIf(id -> Bukkit.getPlayer((UUID)id) == null);
        ArrayList<Player> showFor = new ArrayList<Player>();
        long representativeTime = 0L;
        World nightWorld = null;
        for (World w : Bukkit.getWorlds()) {
            long time;
            if (!SpookySeason.get().isWorldEnabled(w) || (time = w.getTime()) < 13000L || time > 23000L) continue;
            showFor.addAll(w.getPlayers());
            if (nightWorld != null) continue;
            nightWorld = w;
            representativeTime = time;
        }
        if (showFor.isEmpty()) {
            this.hideBar();
            this.stopCustomJukebox();
            return;
        }
        showFor.removeIf(p -> SpookySeason.get().prefs().isOptedOut(p.getUniqueId()));
        if (showFor.isEmpty()) {
            this.hideBar();
            this.stopCustomJukebox();
            return;
        }
        double progress = 1.0 - (double)(representativeTime - 13000L) / 10000.0;
        this.showBarFor(showFor, Math.max(0.0, Math.min(1.0, progress)));
        String discId = this.plugin.getConfig().getString("customJukebox.ambientDiscId", "");
        useCjb = !discId.isEmpty() && SpookySeason.get().jukebox().isAvailable();
        if (useCjb && !this.cjbPlaying && nightWorld != null) {
            this.cjbPlaybackLoc = nightWorld.getSpawnLocation();
            boolean loop = this.plugin.getConfig().getBoolean("customJukebox.ambientLoop", true);
            Location cjbLoc = this.cjbPlaybackLoc;
            Scheduler.runAtLocation(this.plugin, cjbLoc, () -> {
                if (SpookySeason.get().jukebox().playDisc(cjbLoc, discId, loop)) {
                    this.cjbPlaying = true;
                }
            });
        }
        boolean applyFog = false;
        ++this.fogTicker;
        if (this.fogEnabled && this.fogTicker >= this.fogIntervalSeconds) {
            this.fogTicker = 0;
            applyFog = true;
        }
        boolean doFog = applyFog;
        boolean doVanillaSound = !useCjb || !this.cjbPlaying;
        for (Player p2 : showFor) {
            Scheduler.runAtLocation(this.plugin, p2.getLocation(), () -> {
                if (!p2.isOnline()) {
                    return;
                }
                if (doVanillaSound) {
                    float vol = (float)SpookySeason.get().prefs().getAmbientVol(p2.getUniqueId(), this.cfgAmbientVol);
                    p2.playSound(p2.getLocation(), Sound.AMBIENT_CAVE, SoundCategory.AMBIENT, vol, 0.6f);
                }
                p2.spawnParticle(Particle.SOUL, p2.getLocation(), 8, 2.0, 0.5, 2.0, 0.01);
                if (doFog) {
                    p2.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, this.fogDurationTicks, 0, false, false, false));
                }
            });
        }
        this.maybeSpawnGhosts(showFor);
    }

    private void stopCustomJukebox() {
        if (this.cjbPlaying && this.cjbPlaybackLoc != null) {
            SpookySeason.get().jukebox().stopPlayback(this.cjbPlaybackLoc);
            this.cjbPlaying = false;
            this.cjbPlaybackLoc = null;
        }
    }

    // Nur die Differenz schicken statt jede Sekunde alle Zuschauer zu entfernen und neu
    // hinzuzufügen — das waren pro Spieler zwei überflüssige Pakete pro Sekunde.
    private void showBarFor(List<Player> players, double progress) {
        Set<Player> wanted = new HashSet<Player>(players);
        Set<Player> current = new HashSet<Player>(this.bar.getPlayers());
        for (Player p : current) {
            if (wanted.contains(p)) continue;
            this.bar.removePlayer(p);
        }
        for (Player p : wanted) {
            if (current.contains(p)) continue;
            this.bar.addPlayer(p);
        }
        this.bar.setProgress(progress);
        if (!this.bar.isVisible()) {
            this.bar.setVisible(true);
        }
    }

    private void hideBar() {
        if (this.bar.isVisible()) {
            this.bar.setVisible(false);
        }
        this.bar.removeAll();
    }

    public void refreshBar() {
        List<Player> viewers = new ArrayList<Player>(this.bar.getPlayers());
        if (viewers.isEmpty()) {
            return;
        }
        this.bar.removeAll();
        viewers.forEach(this.bar::addPlayer);
    }

    private void maybeSpawnGhosts(List<Player> players) {
        String ghostName = Lang.get("haunted.ghost-name", new String[0]);
        for (Player p : players) {
            UUID id = p.getUniqueId();
            int left = this.ghostCooldown.getOrDefault(id, 0) - 1;
            this.ghostCooldown.put(id, left);
            if (left > 0) continue;
            this.ghostCooldown.put(id, this.ghostSpawnEvery);
            // Position einmal erfassen — der Spawn muss auf Folia in der Region der
            // geplanten Location bleiben, auch wenn der Spieler weiterläuft.
            Location pLoc = p.getLocation();
            Scheduler.runAtLocation(this.plugin, pLoc, () -> {
                if (!p.isOnline()) {
                    return;
                }
                for (int i = 0; i < this.ghostPerPlayer; ++i) {
                    LivingEntity le;
                    Location loc = pLoc.clone().add(this.random(-6.0, 6.0), 0.0, this.random(-6.0, 6.0));
                    if (!SpookySeason.get().regions().canSpawnAt(loc)) continue;
                    Entity ghost = pLoc.getWorld().spawnEntity(loc, EntityType.VEX);
                    ghost.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
                    ghost.customName(((TextComponent)Component.text((String)ghostName).color((TextColor)NamedTextColor.WHITE)).decorate(TextDecoration.BOLD));
                    ghost.setCustomNameVisible(true);
                    ghost.setGlowing(true);
                    ghost.getPersistentDataContainer().set(this.GHOST_MARKER, PersistentDataType.BYTE, (byte)1);
                    if (ghost instanceof LivingEntity && (le = (LivingEntity)ghost).getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                        le.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
                    }
                    pLoc.getWorld().spawnParticle(Particle.INSTANT_EFFECT, ghost.getLocation().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.01);
                    float vol = (float)SpookySeason.get().prefs().getGhostVol(p.getUniqueId(), this.cfgGhostVol);
                    p.playSound(loc, Sound.ENTITY_PHANTOM_AMBIENT, SoundCategory.AMBIENT, vol, 0.5f);
                    this.activeGhosts.add(ghost);
                    Scheduler.runEntityLater(this.plugin, ghost, () -> {
                        if (ghost.isValid() && !ghost.isDead()) {
                            ghost.remove();
                        }
                        this.activeGhosts.remove(ghost);
                    }, this.ghostLifetimeTicks);
                }
            });
        }
    }

    private double random(double min, double max) {
        return min + Math.random() * (max - min);
    }
}

