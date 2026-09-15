/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.NamespacedKey
 *  org.bukkit.World
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Entity
 *  org.bukkit.event.Listener
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package org.example;

import java.io.File;
import java.time.Month;
import java.time.ZonedDateTime;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.example.cmd.PumpkinRainCommand;
import org.example.cmd.SpookyBossCommand;
import org.example.cmd.SpookyRaidCommand;
import org.example.cmd.SpookyToggleCommand;
import org.example.cmd.SpookyVolumeCommand;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.example.feature.BatSwarmManager;
import org.example.feature.EntityCleanupListener;
import org.example.feature.GhostListener;
import org.example.feature.HalloweenBossManager;
import org.example.feature.HalloweenMobListener;
import org.example.feature.HauntedNightManager;
import org.example.feature.JumpScareListener;
import org.example.feature.PumpkinRainManager;
import org.example.feature.SeasonRewardManager;
import org.example.feature.SpookyMenuGUI;
import org.example.feature.TrickOrTreatListener;
import org.example.feature.raid.RaidManager;
import org.example.integration.CustomJukeboxHook;
import org.example.integration.RegionIntegration;
import org.example.integration.SpookyPlaceholders;
import org.example.lang.Lang;
import org.example.prefs.PlayerPrefs;
import org.example.prefs.PlayerStats;
import org.example.update.UpdateChecker;
import org.example.util.Attributes;
import org.example.util.ConfigMerger;
import org.example.util.Scheduler;

public final class SpookySeason
extends JavaPlugin {
    private static SpookySeason instance;
    private NamespacedKey entityMarker;
    private NamespacedKey ghostMarker;
    private NamespacedKey raidMarker;
    private HauntedNightManager hauntedNightManager;
    private PumpkinRainManager pumpkinRainManager;
    private HalloweenBossManager bossManager;
    private BatSwarmManager batSwarmManager;
    private SeasonRewardManager rewardManager;
    private SpookyMenuGUI menuGUI;
    private PlayerPrefs prefs;
    private PlayerStats stats;
    private RegionIntegration regionIntegration;
    private CustomJukeboxHook customJukeboxHook;
    private RaidManager raidManager;

    public static SpookySeason get() {
        return instance;
    }

    public NamespacedKey entityMarker() {
        return this.entityMarker;
    }

    /** Marks the vex ghosts of a blood moon night — in addition to the general entityMarker. */
    public NamespacedKey ghostMarker() {
        return this.ghostMarker;
    }

    /** Marks a raid's attackers and target — in addition to the entityMarker. */
    public NamespacedKey raidMarker() {
        return this.raidMarker;
    }

    public PlayerPrefs prefs() {
        return this.prefs;
    }

    public PlayerStats stats() {
        return this.stats;
    }

    public RegionIntegration regions() {
        return this.regionIntegration;
    }

    public CustomJukeboxHook jukebox() {
        return this.customJukeboxHook;
    }

    public HauntedNightManager haunted() {
        return this.hauntedNightManager;
    }

    public PumpkinRainManager pumpkins() {
        return this.pumpkinRainManager;
    }

    public HalloweenBossManager boss() {
        return this.bossManager;
    }

    public SeasonRewardManager rewards() {
        return this.rewardManager;
    }

    public SpookyMenuGUI menu() {
        return this.menuGUI;
    }

    public RaidManager raid() {
        return this.raidManager;
    }

    public void onEnable() {
        instance = this;
        // Resolves the attributes through the registry (1.21.x and 26.x keys). If that fails it
        // should blow up right here, not later during a boss spawn.
        Attributes.init();
        this.entityMarker = new NamespacedKey((Plugin)this, "spooky_entity");
        this.ghostMarker = new NamespacedKey((Plugin)this, "spooky_ghost");
        this.raidMarker = new NamespacedKey((Plugin)this, "spooky_raid");
        this.saveDefaultConfig();
        ConfigMerger.merge((Plugin)this, "config.yml", new File(this.getDataFolder(), "config.yml"));
        this.reloadConfig();
        Lang.load((Plugin)this);
        this.regionIntegration = new RegionIntegration((Plugin)this);
        this.customJukeboxHook = new CustomJukeboxHook((Plugin)this);
        this.prefs = new PlayerPrefs((Plugin)this);
        this.stats = new PlayerStats((Plugin)this);
        this.pumpkinRainManager = new PumpkinRainManager((Plugin)this);
        this.hauntedNightManager = new HauntedNightManager((Plugin)this);
        this.bossManager = new HalloweenBossManager((Plugin)this);
        this.batSwarmManager = new BatSwarmManager((Plugin)this);
        this.rewardManager = new SeasonRewardManager((Plugin)this);
        this.menuGUI = new SpookyMenuGUI();
        this.raidManager = new RaidManager((Plugin)this);
        this.registerCommand("pumpkinrain", new PumpkinRainCommand(this.pumpkinRainManager));
        this.registerCommand("spooky", new SpookyToggleCommand());
        this.registerCommand("spookyvolume", new SpookyVolumeCommand());
        this.registerCommand("spookyboss", new SpookyBossCommand());
        this.registerCommand("spookyraid", new SpookyRaidCommand());
        Bukkit.getPluginManager().registerEvents((Listener)new TrickOrTreatListener(this), (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)new GhostListener(), (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)new JumpScareListener(this), (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)new HalloweenMobListener(this), (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.bossManager, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.rewardManager, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.menuGUI, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)this.raidManager, (Plugin)this);
        Bukkit.getPluginManager().registerEvents((Listener)new EntityCleanupListener(this), (Plugin)this);
        this.hauntedNightManager.start();
        this.bossManager.start();
        this.batSwarmManager.start();
        this.rewardManager.start();
        this.raidManager.start();
        Metrics metrics = new Metrics((Plugin)this, 30933);
        metrics.addCustomChart(new SimplePie("language", () -> this.getConfig().getString("language", "en")));
        metrics.addCustomChart(new SimplePie("season_active", () -> this.isSeasonActive() ? "active" : "inactive"));
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.hookPlaceholderAPI();
        }
        if (this.getConfig().getBoolean("updateChecker", true)) {
            UpdateChecker updateChecker = new UpdateChecker((Plugin)this);
            Bukkit.getPluginManager().registerEvents((Listener)updateChecker, (Plugin)this);
            updateChecker.checkAsync();
        }
        this.getLogger().info(Lang.get("plugin.enabled", new String[0]));
    }

    public void onDisable() {
        // Every stop() is guarded separately — one failure (rejected scheduling on Folia, say)
        // must not abort the rest of the cleanup.
        this.safeStop(this.hauntedNightManager == null ? null : this.hauntedNightManager::stop, "hauntedNight");
        this.safeStop(this.pumpkinRainManager == null ? null : this.pumpkinRainManager::stop, "pumpkinRain");
        this.safeStop(this.bossManager == null ? null : this.bossManager::stop, "boss");
        this.safeStop(this.batSwarmManager == null ? null : this.batSwarmManager::stop, "batSwarm");
        this.safeStop(this.rewardManager == null ? null : this.rewardManager::stop, "rewards");
        this.safeStop(this.raidManager == null ? null : this.raidManager::stop, "raid");
        if (this.prefs != null) {
            this.prefs.flush();
        }
        if (this.stats != null) {
            this.stats.flush();
        }
    }

    private void safeStop(Runnable stop, String name) {
        if (stop == null) {
            return;
        }
        try {
            stop.run();
        }
        catch (Exception e) {
            this.getLogger().warning("Cleanup of " + name + " failed during disable: " + e.getMessage());
        }
    }

    public void stopAll() {
        if (this.hauntedNightManager != null) {
            this.hauntedNightManager.stop();
        }
        if (this.pumpkinRainManager != null) {
            this.pumpkinRainManager.stop();
        }
        if (this.bossManager != null) {
            this.bossManager.stop();
        }
        if (this.batSwarmManager != null) {
            this.batSwarmManager.stop();
        }
        if (this.raidManager != null) {
            this.raidManager.stop();
        }
        this.removeAllPluginEntities();
    }

    public void removeAllPluginEntities() {
        if (Scheduler.isFolia()) {
            // On Folia a world's entity list belongs to no single thread and cannot be walked
            // safely across regions. The managers clear their own active entities in stopAll();
            // orphans left over from a crash or old chunks are removed by EntityCleanupListener
            // on the next chunk load.
            return;
        }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!e.getPersistentDataContainer().has(this.entityMarker, PersistentDataType.BYTE)) continue;
                if (!e.isValid() || e.isDead()) continue;
                e.remove();
            }
        }
    }

    public void startAll() {
        if (this.hauntedNightManager != null) {
            this.hauntedNightManager.start();
        }
        if (this.bossManager != null) {
            this.bossManager.start();
        }
        if (this.batSwarmManager != null) {
            this.batSwarmManager.start();
        }
        if (this.rewardManager != null) {
            this.rewardManager.start();
        }
        if (this.raidManager != null) {
            this.raidManager.start();
        }
    }

    public void reload() {
        ConfigMerger.merge((Plugin)this, "config.yml", new File(this.getDataFolder(), "config.yml"));
        this.reloadConfig();
        Lang.load((Plugin)this);
        this.hauntedNightManager.start();
        this.bossManager.start();
        this.batSwarmManager.start();
        this.rewardManager.start();
        // Deliberately only the ticker: a running raid carries on with its frozen configuration
        // snapshot and is not torn down by a reload.
        this.raidManager.start();
    }

    public boolean isSeasonActive() {
        return this.getConfig().getBoolean("active", true) && this.isInSeasonWindow();
    }

    /**
     * The calendar window only, without the manual {@code active} switch.
     *
     * <p>The season-end rewards deliberately hang off this: otherwise a {@code /spooky off} in the
     * middle of the season would count as the season ending, handing out rewards and resetting the
     * treat statistics.
     */
    public boolean isInSeasonWindow() {
        FileConfiguration cfg = this.getConfig();
        int start = cfg.getInt("activeWindow.startDay", 0);
        int end = cfg.getInt("activeWindow.endDay", 0);
        if (start == 0 || end == 0) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now();
        if (now.getMonth() != Month.OCTOBER) {
            return false;
        }
        int day = now.getDayOfMonth();
        return day >= start && day <= end;
    }

    public boolean isWorldEnabled(World world) {
        List<String> enabled = this.getConfig().getStringList("enabledWorlds");
        return enabled.isEmpty() || enabled.contains(world.getName());
    }

    private void hookPlaceholderAPI() {
        new SpookyPlaceholders(this).register();
        this.getLogger().info("PlaceholderAPI integration enabled.");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = this.getCommand(name);
        if (cmd == null) {
            this.getLogger().severe("Command '" + name + "' not found in plugin.yml!");
            return;
        }
        cmd.setExecutor((CommandExecutor)executor);
        if (executor instanceof TabCompleter) {
            TabCompleter tc = (TabCompleter)executor;
            cmd.setTabCompleter(tc);
        }
    }
}

