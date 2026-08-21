package org.example.feature;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.example.util.YamlSaver;

public class SeasonRewardManager
implements Listener {
    private final Plugin plugin;
    private Scheduler.TaskHandle checkTask;
    private final YamlConfiguration pendingCfg;
    private final YamlConfiguration stateCfg;
    private final YamlSaver pendingSaver;
    private final YamlSaver stateSaver;
    // Wird über Neustarts hinweg in reward-state.yml gehalten, siehe checkTransition().
    private volatile boolean wasActive;

    public SeasonRewardManager(Plugin plugin) {
        this.plugin = plugin;
        File pendingFile = new File(plugin.getDataFolder(), "pending-rewards.yml");
        this.pendingCfg = YamlConfiguration.loadConfiguration(pendingFile);
        this.pendingSaver = new YamlSaver(plugin, pendingFile, this::snapshotPending);
        File stateFile = new File(plugin.getDataFolder(), "reward-state.yml");
        this.stateCfg = YamlConfiguration.loadConfiguration(stateFile);
        this.stateSaver = new YamlSaver(plugin, stateFile, this::snapshotState);
        // Beim allerersten Start (kein gespeicherter Zustand) den aktuellen Stand übernehmen,
        // damit nicht sofort eine Verteilung ausgelöst wird.
        this.wasActive = this.stateCfg.getBoolean("wasActive", SpookySeason.get().isInSeasonWindow());
    }

    public void start() {
        this.stop();
        // wasActive wird hier bewusst NICHT auf den aktuellen Stand gesetzt: Lief der Server über
        // das Season-Ende hinweg nicht, ist die Flanke sonst für immer verloren und die Rewards
        // werden nie verteilt.
        this.checkTask = Scheduler.runTimer(this.plugin, this::checkTransition, 1200L, 1200L);
    }

    public void stop() {
        if (this.checkTask != null) {
            this.checkTask.cancel();
            this.checkTask = null;
        }
        this.pendingSaver.flushIfPending();
        this.stateSaver.flushIfPending();
    }

    private void checkTransition() {
        // Bewusst das Kalenderfenster statt isSeasonActive(): ein manuelles /spooky off ist
        // kein Season-Ende und darf keine Rewards verteilen.
        boolean nowActive = SpookySeason.get().isInSeasonWindow();
        boolean seasonEnded = this.wasActive && !nowActive;
        boolean dirty = false;
        if (nowActive != this.wasActive) {
            this.wasActive = nowActive;
            synchronized (this.stateCfg) {
                this.stateCfg.set("wasActive", nowActive);
            }
            dirty = true;
        }
        if (seasonEnded && this.plugin.getConfig().getBoolean("seasonEndRewards.enabled", true)) {
            String today = LocalDate.now().toString();
            String lastDistributed;
            synchronized (this.stateCfg) {
                lastDistributed = this.stateCfg.getString("lastDistributed", "");
            }
            if (!today.equals(lastDistributed)) {
                this.distribute();
                synchronized (this.stateCfg) {
                    this.stateCfg.set("lastDistributed", today);
                }
                dirty = true;
            }
        }
        if (dirty) {
            this.stateSaver.save();
        }
    }

    public void distributeManually() {
        this.distribute();
        synchronized (this.stateCfg) {
            this.stateCfg.set("lastDistributed", LocalDate.now().toString());
        }
        this.stateSaver.save();
    }

    private void distribute() {
        List<Map.Entry<UUID, Integer>> topList = SpookySeason.get().stats().getTopTreats(10);
        if (topList.isEmpty()) {
            this.plugin.getLogger().info("No treats collected – skipping reward distribution.");
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
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                // Die Reward-Commands fassen das Inventar des Spielers an — auf Folia geht das nur
                // aus dessen Region-Thread, nicht aus dem Global-Tick, in dem diese Prüfung läuft.
                Scheduler.runOnEntity(this.plugin, online, () -> {
                    if (!online.isOnline()) {
                        this.queuePending(uuid, resolved);
                        return;
                    }
                    this.executeCommands(resolved);
                    online.sendMessage(Lang.get("rewards.received", "rank", String.valueOf(currentRank)));
                });
            } else {
                this.queuePending(uuid, resolved);
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
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        List<String> commands = this.takePending(uuid);
        if (commands.isEmpty()) {
            return;
        }
        // Zurücklegen, falls der Spieler die Wartezeit nicht übersteht — auf Folia auch dann,
        // wenn Folia den Task wegen des Logouts gar nicht erst ausführt (retired).
        Runnable requeue = () -> this.queuePending(uuid, commands);
        Scheduler.runEntityLater(this.plugin, player, () -> {
            if (player.isOnline()) {
                this.executeCommands(commands);
                player.sendMessage(Lang.get("rewards.received-late", new String[0]));
            } else {
                requeue.run();
            }
        }, requeue, 60L);
    }

    /** Holt die offenen Commands und bucht sie sofort aus — sonst liest ein Relog sie erneut (Dupe). */
    private List<String> takePending(UUID uuid) {
        String path = "pending." + String.valueOf(uuid);
        List<String> commands;
        synchronized (this.pendingCfg) {
            commands = List.copyOf(this.pendingCfg.getStringList(path));
            if (commands.isEmpty()) {
                return commands;
            }
            this.pendingCfg.set(path, null);
        }
        this.pendingSaver.save();
        return commands;
    }

    private void queuePending(UUID uuid, List<String> commands) {
        String path = "pending." + String.valueOf(uuid);
        synchronized (this.pendingCfg) {
            ArrayList<String> merged = new ArrayList<String>(this.pendingCfg.getStringList(path));
            merged.addAll(commands);
            this.pendingCfg.set(path, merged);
        }
        this.pendingSaver.save();
    }

    private void executeCommands(List<String> commands) {
        for (String cmd : commands) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), cmd);
        }
    }

    private String snapshotPending() {
        synchronized (this.pendingCfg) {
            return this.pendingCfg.saveToString();
        }
    }

    private String snapshotState() {
        synchronized (this.stateCfg) {
            return this.stateCfg.saveToString();
        }
    }
}
