package org.example.prefs;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.example.util.YamlSaver;

public class PlayerStats {
    private final YamlConfiguration cfg;
    private final YamlSaver saver;

    public PlayerStats(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "player-stats.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        this.saver = new YamlSaver(plugin, file, this::snapshot);
    }

    public synchronized int getTreats(UUID id) {
        return this.cfg.getInt("players." + String.valueOf(id) + ".treats", 0);
    }

    public synchronized void addTreat(UUID id) {
        this.cfg.set("players." + String.valueOf(id) + ".treats", this.getTreats(id) + 1);
        this.saver.save();
    }

    public synchronized void resetAll() {
        this.cfg.set("players", null);
        this.saver.save();
    }

    public synchronized List<Map.Entry<UUID, Integer>> getTopTreats(int limit) {
        ConfigurationSection section = this.cfg.getConfigurationSection("players");
        if (section == null) {
            return List.of();
        }
        return section.getKeys(false).stream().map(key -> {
            try {
                UUID uuid = UUID.fromString(key);
                int treats = this.cfg.getInt("players." + key + ".treats", 0);
                return Map.entry(uuid, treats);
            }
            catch (Exception e) {
                return null;
            }
        }).filter(Objects::nonNull).sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(limit).collect(Collectors.toList());
    }

    public static String resolveName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    /** Writes immediately and in full — for onDisable. */
    public void flush() {
        this.saver.flush();
    }

    /**
     * Called by the YamlSaver, possibly from an async thread. The serialisation therefore has to
     * run under the same monitor as the mutations above.
     */
    private synchronized String snapshot() {
        return this.cfg.saveToString();
    }
}
