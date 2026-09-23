package org.example.feature;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
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
import org.example.util.Reward;
import org.example.util.Scheduler;
import org.example.util.YamlSaver;

/**
 * Season-end rewards for the top treat collectors: items and experience per rank, given through
 * the inventory API. Players who are offline at the time receive theirs on their next join.
 */
public class SeasonRewardManager
implements Listener {
    private final Plugin plugin;
    private Scheduler.TaskHandle checkTask;
    private final YamlConfiguration pendingCfg;
    private final YamlConfiguration stateCfg;
    private final YamlSaver pendingSaver;
    private final YamlSaver stateSaver;
    // Kept across restarts in reward-state.yml, see checkTransition().
    private volatile boolean wasActive;

    public SeasonRewardManager(Plugin plugin) {
        this.plugin = plugin;
        File pendingFile = new File(plugin.getDataFolder(), "pending-rewards.yml");
        this.pendingCfg = YamlConfiguration.loadConfiguration(pendingFile);
        this.pendingSaver = new YamlSaver(plugin, pendingFile, this::snapshotPending);
        File stateFile = new File(plugin.getDataFolder(), "reward-state.yml");
        this.stateCfg = YamlConfiguration.loadConfiguration(stateFile);
        this.stateSaver = new YamlSaver(plugin, stateFile, this::snapshotState);
        // On the very first start (no saved state) adopt the current situation, so a distribution
        // is not triggered immediately.
        this.wasActive = this.stateCfg.getBoolean("wasActive", SpookySeason.get().isInSeasonWindow());
    }

    public void start() {
        this.stop();
        // wasActive is not refreshed here: if the server was down across the end of the season,
        // the transition would otherwise be lost and the rewards never handed out.
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
        // The calendar window rather than isSeasonActive(): a manual /spooky off is not the end
        // of the season and must not hand out rewards.
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
            this.plugin.getLogger().info("No treats collected - skipping reward distribution.");
            return;
        }
        Map<Integer, Reward> rewardsByRank = this.rewardsByRank();
        if (rewardsByRank.isEmpty()) {
            this.plugin.getLogger().warning("No valid reward entries found in seasonEndRewards.ranks.");
            return;
        }
        this.plugin.getLogger().info("Distributing season-end rewards...");
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : topList) {
            UUID uuid = entry.getKey();
            Reward reward = rewardsByRank.get(rank);
            if (reward == null || reward.isEmpty()) {
                ++rank;
                continue;
            }
            String playerName = PlayerStats.resolveName(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                reward.give(this.plugin, online);
                online.sendMessage(Lang.get("rewards.received", "rank", String.valueOf(rank)));
            } else {
                this.queuePending(uuid, reward.toLines());
            }
            this.plugin.getLogger().info("Rank #" + rank + ": " + playerName + " (" + entry.getValue() + " treats)");
            ++rank;
        }
        // Without the reset, every further distribution would reward the same cumulative top list again.
        SpookySeason.get().stats().resetAll();
        this.plugin.getLogger().info("Treat stats reset after reward distribution.");
    }

    /** Rewards per rank from {@code seasonEndRewards.ranks}, keyed by the numeric rank. */
    private Map<Integer, Reward> rewardsByRank() {
        HashMap<Integer, Reward> result = new HashMap<Integer, Reward>();
        ConfigurationSection ranks = this.plugin.getConfig().getConfigurationSection("seasonEndRewards.ranks");
        if (ranks == null) {
            return result;
        }
        for (String key : ranks.getKeys(false)) {
            int rank;
            try {
                rank = Integer.parseInt(key);
            }
            catch (NumberFormatException e) {
                continue;
            }
            Reward reward = Reward.from(ranks.getConfigurationSection(key), this.plugin.getLogger());
            if (reward.isEmpty()) continue;
            result.put(rank, reward);
        }
        return result;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();
        List<String> lines = this.takePending(uuid);
        if (lines.isEmpty()) {
            return;
        }
        Reward reward = Reward.fromLines(lines, this.plugin.getLogger());
        // Put them back if the player does not survive the wait; on Folia also for the case
        // where the task is never run because of the logout (retired).
        Runnable requeue = () -> this.queuePending(uuid, lines);
        Scheduler.runEntityLater(this.plugin, player, () -> {
            if (player.isOnline()) {
                reward.give(this.plugin, player);
                player.sendMessage(Lang.get("rewards.received-late", new String[0]));
            } else {
                requeue.run();
            }
        }, requeue, 60L);
    }

    /** Takes the pending reward lines and clears them at once, so a relog cannot read them twice. */
    private List<String> takePending(UUID uuid) {
        String path = "pending." + String.valueOf(uuid);
        List<String> lines;
        synchronized (this.pendingCfg) {
            lines = List.copyOf(this.pendingCfg.getStringList(path));
            if (lines.isEmpty()) {
                return lines;
            }
            this.pendingCfg.set(path, null);
        }
        this.pendingSaver.save();
        return lines;
    }

    private void queuePending(UUID uuid, List<String> lines) {
        String path = "pending." + String.valueOf(uuid);
        synchronized (this.pendingCfg) {
            ArrayList<String> merged = new ArrayList<String>(this.pendingCfg.getStringList(path));
            merged.addAll(lines);
            this.pendingCfg.set(path, merged);
        }
        this.pendingSaver.save();
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
