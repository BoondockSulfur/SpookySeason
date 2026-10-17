package org.example.feature;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Scheduler;

/**
 * Blood rain: red drops falling around every player, optionally with real rain for just those
 * players, and the undead nearby growing stronger while it lasts.
 *
 * <p>Started with {@code /bloodrain start} in every enabled world, or on its own with
 * {@code bloodMoonChance} once per blood moon night — then only in worlds where it is night.
 * Vanilla rain cannot be coloured without a resource pack, so the blood is made of particles.
 *
 * <p><b>Folia:</b> the pulse is planned on the global scheduler; particles, weather and sounds run
 * on each player's own thread, the strength effect on each mob's.
 */
public class BloodRainManager {
    private static final Particle.DustOptions STREAK = new Particle.DustOptions(Color.fromRGB(110, 0, 0), 0.7f);
    private static final Particle.DustOptions AURA = new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.3f);

    /** How the drops look; see {@code bloodRain.style}. */
    private enum Style {
        /** Block fragments of {@code bloodRain.block} that fall with gravity and bounce off the ground. */
        DROPS,
        /** Thin dark red vertical streaks, like threads of rain. */
        STREAKS,
        /** Real drop shapes: the falling drips of dripstone under lava, reddish orange. */
        DRIPSTONE;

        /** One style or several, comma-separated ({@code dripstone,streaks}); unknown names are skipped. */
        static List<Style> parse(String value) {
            ArrayList<Style> styles = new ArrayList<Style>();
            for (String part : value.split(",")) {
                try {
                    Style style = Style.valueOf(part.trim().toUpperCase(Locale.ROOT));
                    if (!styles.contains(style)) {
                        styles.add(style);
                    }
                }
                catch (IllegalArgumentException ignored) {
                    // Skipped; an empty result falls back to DRIPSTONE below.
                }
            }
            return styles.isEmpty() ? List.of(DRIPSTONE) : List.copyOf(styles);
        }
    }
    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    /** The configuration of one rain, read when it starts. */
    private record Settings(List<Style> styles, BlockData blood, int period, int maxTicks, double radius, int intensity,
                            double volume, boolean playerWeather, boolean buffUndead, int strengthAmplifier,
                            double undeadRadius) {

        static Settings from(FileConfiguration cfg) {
            Material block = Material.matchMaterial(cfg.getString("bloodRain.block", "NETHER_WART_BLOCK"));
            if (block == null || !block.isBlock()) {
                block = Material.NETHER_WART_BLOCK;
            }
            return new Settings(
                    Style.parse(cfg.getString("bloodRain.style", "dripstone")),
                    block.createBlockData(),
                    Math.max(1, cfg.getInt("bloodRain.tickPeriod", 4)),
                    Math.max(1, cfg.getInt("bloodRain.maxDurationSeconds", 90)) * 20,
                    Math.max(2.0, cfg.getDouble("bloodRain.spawnRadius", 12.0)),
                    Math.max(0, cfg.getInt("bloodRain.intensity", 14)),
                    cfg.getDouble("bloodRain.volume", 0.5),
                    cfg.getBoolean("bloodRain.playerWeather", false),
                    cfg.getBoolean("bloodRain.undead.enabled", true),
                    Math.max(0, cfg.getInt("bloodRain.undead.strengthAmplifier", 0)),
                    Math.max(1.0, cfg.getDouble("bloodRain.undead.radius", 24.0)));
        }
    }

    private final Plugin plugin;
    private Scheduler.TaskHandle ticker;
    private Scheduler.TaskHandle rainTask;
    private volatile boolean running;
    // Started by a blood moon night: only worlds where it is night are affected, and the rain
    // ends with the night.
    private volatile boolean nightOnly;
    private long lastRolledDay = Long.MIN_VALUE;
    // Players currently under the rain — for the closing message and resetting their weather.
    private final Set<UUID> affected = ConcurrentHashMap.newKeySet();

    public BloodRainManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the once-per-second check for blood moon nights. A running rain is left alone. */
    public void startTicker() {
        if (this.ticker != null) {
            this.ticker.cancel();
        }
        this.ticker = Scheduler.runTimer(this.plugin, this::autoTick, 20L, 20L);
    }

    /** Stops the rain and the check — for onDisable and {@code /spooky off}. */
    public void shutdown() {
        if (this.ticker != null) {
            this.ticker.cancel();
            this.ticker = null;
        }
        this.stop();
    }

    public boolean isRunning() {
        return this.running;
    }

    /**
     * Starts the rain in every enabled world.
     *
     * @param force ignore the season and {@code bloodRain.enabled}, as {@code /bloodrain start} does
     */
    public void start(boolean force) {
        this.start(force, false);
    }

    private void start(boolean force, boolean onlyAtNight) {
        FileConfiguration cfg = this.plugin.getConfig();
        if (!force && (!SpookySeason.get().isSeasonActive() || !cfg.getBoolean("bloodRain.enabled", true))) {
            return;
        }
        this.stop();
        Settings settings = Settings.from(cfg);
        this.nightOnly = onlyAtNight;
        this.running = true;
        this.rainTask = Scheduler.runTimer(this.plugin, new Runnable() {
            private int ticks;

            @Override
            public void run() {
                try {
                    this.ticks += settings.period();
                    if (this.ticks >= settings.maxTicks()) {
                        BloodRainManager.this.stop();
                        return;
                    }
                    // Sound and the undead buff every two seconds, the drops on every pulse.
                    boolean slowBeat = this.ticks % 40 < settings.period();
                    BloodRainManager.this.pulse(settings, slowBeat);
                }
                catch (Exception e) {
                    BloodRainManager.this.plugin.getLogger().log(Level.WARNING, "BloodRain tick error", e);
                }
            }
        }, 1L, settings.period());
    }

    public void stop() {
        this.running = false;
        if (this.rainTask != null) {
            this.rainTask.cancel();
            this.rainTask = null;
        }
        for (UUID id : Set.copyOf(this.affected)) {
            this.release(id, true);
        }
        this.affected.clear();
    }

    private void autoTick() {
        try {
            if (this.running) {
                // A rain the night brought ends with the night.
                if (this.nightOnly && this.nightWorld() == null) {
                    this.stop();
                }
                return;
            }
            FileConfiguration cfg = this.plugin.getConfig();
            double chance = cfg.getDouble("bloodRain.bloodMoonChance", 0.25);
            if (chance <= 0.0 || !cfg.getBoolean("bloodRain.enabled", true) || !SpookySeason.get().isSeasonActive()) {
                return;
            }
            World night = this.nightWorld();
            if (night == null) {
                return;
            }
            // One roll per night: the night lies within a single day of the world's full time.
            long day = night.getFullTime() / 24000L;
            if (day == this.lastRolledDay) {
                return;
            }
            this.lastRolledDay = day;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                this.start(false, true);
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "BloodRain check error", e);
        }
    }

    /** The first enabled world with players in which it is night, or null. */
    private World nightWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (SpookySeason.get().isWorldEnabled(w) && BloodRainManager.isNight(w) && !w.getPlayers().isEmpty()) {
                return w;
            }
        }
        return null;
    }

    private static boolean isNight(World w) {
        long time = w.getTime();
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    private void pulse(Settings settings, boolean slowBeat) {
        HashSet<UUID> now = new HashSet<UUID>();
        for (World w : Bukkit.getWorlds()) {
            if (!SpookySeason.get().isWorldEnabled(w) || (this.nightOnly && !BloodRainManager.isNight(w))) continue;
            for (Player p : w.getPlayers()) {
                if (SpookySeason.get().prefs().isOptedOut(p.getUniqueId())) continue;
                now.add(p.getUniqueId());
                boolean first = this.affected.add(p.getUniqueId());
                Scheduler.runOnEntity(this.plugin, p, () -> this.rainOn(p, settings, first, slowBeat));
            }
        }
        // Players who left the rain (opted out, changed world, the night ended there) get their
        // own weather back.
        for (UUID id : Set.copyOf(this.affected)) {
            if (now.contains(id)) continue;
            this.affected.remove(id);
            this.release(id, false);
        }
    }

    /** On the player's own thread. */
    private void rainOn(Player p, Settings settings, boolean first, boolean slowBeat) {
        if (!p.isOnline()) {
            return;
        }
        if (first) {
            p.sendMessage(Lang.get("bloodrain.begins", new String[0]));
            if (settings.playerWeather()) {
                p.setPlayerWeather(WeatherType.DOWNFALL);
            }
        }
        Location base = p.getLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double r = settings.radius();
        // Only this player sees their drops: an opted-out player standing next to them stays dry.
        for (int i = 0; i < settings.intensity(); ++i) {
            Location drop = base.clone().add(random.nextDouble(-r, r), 0.0, random.nextDouble(-r, r));
            // With several styles the drops are shared out evenly between them.
            switch (settings.styles().get(i % settings.styles().size())) {
                case STREAKS: {
                    // A short vertical line of tiny dust; new lines every pulse read as falling threads.
                    drop.add(0.0, random.nextDouble(1.0, 7.0), 0.0);
                    for (int k = 0; k < 5; ++k) {
                        p.spawnParticle(Particle.DUST, drop.clone().add(0.0, k * 0.18, 0.0), 1, 0.0, 0.0, 0.0, 0.0, STREAK);
                    }
                    break;
                }
                case DRIPSTONE: {
                    drop.add(0.0, random.nextDouble(4.0, 9.0), 0.0);
                    p.spawnParticle(Particle.FALLING_DRIPSTONE_LAVA, drop, 1, 0.0, 0.0, 0.0, 0.0);
                    break;
                }
                default: {
                    // Block fragments live only a second or two, so they start low enough to land.
                    drop.add(0.0, random.nextDouble(2.0, 6.0), 0.0);
                    p.spawnParticle(Particle.BLOCK, drop, 1, 0.0, 0.0, 0.0, 0.0, settings.blood());
                    break;
                }
            }
        }
        // Splashes on the ground: fragments of the blood block, not dust (that is what powered
        // redstone gives off).
        for (int i = 0; i < Math.max(1, settings.intensity() / 4); ++i) {
            Location splash = base.clone().add(random.nextDouble(-4.0, 4.0), 0.1, random.nextDouble(-4.0, 4.0));
            p.spawnParticle(Particle.BLOCK, splash, 3, 0.1, 0.0, 0.1, 0.0, settings.blood());
        }
        if (!slowBeat) {
            return;
        }
        float vol = (float)SpookySeason.get().prefs().getRainVol(p.getUniqueId(), settings.volume());
        if (!settings.playerWeather()) {
            // Without real rain the client plays no rain sound of its own.
            p.playSound(base, Sound.WEATHER_RAIN, SoundCategory.WEATHER, vol, 0.6f);
        }
        p.playSound(base, Sound.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, vol * 0.6f, 0.7f);
        if (settings.buffUndead()) {
            this.strengthenUndead(p, settings);
        }
    }

    /**
     * The undead around the player grow stronger while the rain lasts. Renewed every two seconds
     * with a short duration, so the effect ends shortly after the rain. The plugin's own creatures
     * (raid attackers, the Horseman) are left out — their strength is balanced separately.
     */
    private void strengthenUndead(Player p, Settings settings) {
        double r = settings.undeadRadius();
        for (Entity entity : p.getNearbyEntities(r, r / 2.0, r)) {
            if (!(entity instanceof LivingEntity le) || entity instanceof Player) continue;
            if (!Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(entity.getType())) continue;
            if (entity.getPersistentDataContainer().has(SpookySeason.get().entityMarker(), PersistentDataType.BYTE)) continue;
            Scheduler.runOnEntity(this.plugin, le, () -> {
                if (!le.isValid() || le.isDead()) {
                    return;
                }
                le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, settings.strengthAmplifier(), false, true, true));
                le.getWorld().spawnParticle(Particle.DUST, le.getLocation().add(0.0, 1.2, 0.0), 4, 0.25, 0.4, 0.25, 0.0, AURA);
            });
        }
    }

    /** Hands the player their own weather back and, at the end of the rain, says so. */
    private void release(UUID id, boolean rainEnded) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return;
        }
        Scheduler.runOnEntity(this.plugin, p, () -> {
            if (!p.isOnline()) {
                return;
            }
            p.resetPlayerWeather();
            if (rainEnded) {
                p.sendMessage(Lang.get("bloodrain.ends", new String[0]));
            }
        });
    }
}
