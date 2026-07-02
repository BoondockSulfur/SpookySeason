/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 */
package org.example.feature;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.prefs.PlayerStats;
import org.example.util.Scheduler;

public class SeasonRewardManager
implements Listener {
    private final Plugin plugin;
    private Scheduler.TaskHandle checkTask;
    private final File pendingFile;
    private final YamlConfiguration pendingCfg;
    private final File stateFile;
    private final YamlConfiguration stateCfg;
    private boolean wasActive;

    public SeasonRewardManager(Plugin plugin) {
        this.plugin = plugin;
        this.pendingFile = new File(plugin.getDataFolder(), "pending-rewards.yml");
        this.pendingCfg = YamlConfiguration.loadConfiguration((File)this.pendingFile);
        this.stateFile = new File(plugin.getDataFolder(), "reward-state.yml");
        this.stateCfg = YamlConfiguration.loadConfiguration((File)this.stateFile);
        this.wasActive = SpookySeason.get().isSeasonActive();
    }

    public void start() {
        this.stop();
        this.wasActive = SpookySeason.get().isSeasonActive();
        this.checkTask = Scheduler.runTimer(this.plugin, this::checkTransition, 1200L, 1200L);
    }

    public void stop() {
        if (this.checkTask != null) {
            this.checkTask.cancel();
            this.checkTask = null;
        }
    }

    private void checkTransition() {
        String lastDistributed;
        String today;
        if (!this.plugin.getConfig().getBoolean("seasonEndRewards.enabled", true)) {
            return;
        }
        boolean nowActive = SpookySeason.get().isSeasonActive();
        if (this.wasActive && !nowActive && !(today = LocalDate.now().toString()).equals(lastDistributed = this.stateCfg.getString("lastDistributed", ""))) {
            this.distribute();
            this.stateCfg.set("lastDistributed", (Object)today);
            this.saveState();
        }
        this.wasActive = nowActive;
    }

    public void distributeManually() {
        this.distribute();
        this.stateCfg.set("lastDistributed", (Object)LocalDate.now().toString());
        this.saveState();
    }

    private void distribute() {
        List<Map.Entry<UUID, Integer>> topList = SpookySeason.get().stats().getTopTreats(10);
        if (topList.isEmpty()) {
            this.plugin.getLogger().info("No treats collected \u2013 skipping reward distribution.");
            return;
        }
        ConfigurationSection rewardsSection = this.plugin.getConfig().getConfigurationSection("seasonEndRewards.commands");
        if (rewardsSection == null) {
            this.plugin.getLogger().warning("No seasonEndRewards.commands section found in config.");
            return;
        }
        HashMap<Integer, List<String>> rewardsByRank = new HashMap<Integer, List<String>>();
        for (String key : rewardsSection.getKeys(false)) {
            try {
                int r = Integer.parseInt(key);
                List<String> cmds = rewardsSection.getStringList(key);
                if (cmds.isEmpty()) continue;
                rewardsByRank.put(r, cmds);
            }
            catch (NumberFormatException r) {}
        }
        if (rewardsByRank.isEmpty()) {
            this.plugin.getLogger().warning("No valid reward entries found in seasonEndRewards.commands.");
            return;
        }
        this.plugin.getLogger().info("Distributing season-end rewards...");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : topList) {
            UUID uuid = entry.getKey();
            List<String> commands = rewardsByRank.get(rank);
            if (commands == null || commands.isEmpty()) {
                ++rank;
                continue;
            }
            int currentRank = rank;
            String playerName = PlayerStats.resolveName(uuid);
            List<String> resolved = commands.stream().map(cmd -> cmd.replace("{player}", playerName).replace("{rank}", String.valueOf(currentRank)).replace("{treats}", String.valueOf(entry.getValue()))).toList();
            Player online = Bukkit.getPlayer((UUID)uuid);
            if (online != null && online.isOnline()) {
                this.executeCommands(resolved);
                online.sendMessage(Lang.get("rewards.received", "rank", String.valueOf(currentRank)));
            } else {
                List<String> existing = this.pendingCfg.getStringList("pending." + String.valueOf(uuid));
                ArrayList<String> merged = new ArrayList<String>(existing);
                merged.addAll(resolved);
                this.pendingCfg.set("pending." + String.valueOf(uuid), merged);
                this.savePending();
            }
            this.plugin.getLogger().info("Rank #" + rank + ": " + playerName + " (" + String.valueOf(entry.getValue()) + " treats)");
            ++rank;
        }
        // Ohne Reset würde jede weitere Verteilung dieselbe kumulierte Top-Liste erneut belohnen.
        SpookySeason.get().stats().resetAll();
        this.plugin.getLogger().info("Treat stats reset after reward distribution.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        String path = "pending." + String.valueOf(uuid);
        List<String> commands = this.pendingCfg.getStringList(path);
        if (commands.isEmpty()) {
            return;
        }
        // Sofort ausbuchen — sonst liest ein Relog innerhalb der Wartezeit die Liste
        // erneut und die Reward-Commands laufen doppelt (Item-Dupe).
        this.pendingCfg.set(path, null);
        this.savePending();
        Scheduler.runLater(this.plugin, () -> {
            if (e.getPlayer().isOnline()) {
                this.executeCommands(commands);
                e.getPlayer().sendMessage(Lang.get("rewards.received-late", new String[0]));
            } else {
                // Spieler ist schon wieder weg — für den nächsten Join zurücklegen.
                List<String> existing = this.pendingCfg.getStringList(path);
                ArrayList<String> merged = new ArrayList<String>(existing);
                merged.addAll(commands);
                this.pendingCfg.set(path, merged);
                this.savePending();
            }
        }, 60L);
    }

    private void executeCommands(List<String> commands) {
        for (String cmd : commands) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)cmd);
        }
    }

    private void savePending() {
        try {
            this.pendingCfg.save(this.pendingFile);
        }
        catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save pending-rewards.yml", e);
        }
    }

    private void saveState() {
        try {
            this.stateCfg.save(this.stateFile);
        }
        catch (IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save reward-state.yml", e);
        }
    }
}

