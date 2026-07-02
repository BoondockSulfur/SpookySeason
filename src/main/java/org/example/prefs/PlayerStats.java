package org.example.prefs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.example.util.Scheduler;

public class PlayerStats {
    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration cfg;
    private final Object ioLock = new Object();
    private final AtomicBoolean savePending = new AtomicBoolean();

    public PlayerStats(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-stats.yml");
        this.cfg = YamlConfiguration.loadConfiguration(this.file);
    }

    public synchronized int getTreats(UUID id) {
        return this.cfg.getInt("players." + String.valueOf(id) + ".treats", 0);
    }

    public synchronized void addTreat(UUID id) {
        this.cfg.set("players." + String.valueOf(id) + ".treats", this.getTreats(id) + 1);
        this.save();
    }

    public synchronized void resetAll() {
        this.cfg.set("players", null);
        this.save();
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

    public void flush() {
        String data;
        synchronized (this) {
            data = this.cfg.saveToString();
        }
        this.write(data);
    }

    // Mutationen kommen auf Folia von beliebigen Region-Threads; die Datei-I/O darf dort
    // nicht inline laufen. Debounced: es ist höchstens ein Async-Save gleichzeitig geplant.
    private void save() {
        if (!this.plugin.isEnabled()) {
            this.flush();
            return;
        }
        if (this.savePending.compareAndSet(false, true)) {
            Scheduler.runAsync(this.plugin, () -> {
                this.savePending.set(false);
                this.flush();
            });
        }
    }

    private void write(String data) {
        synchronized (this.ioLock) {
            try {
                File parent = this.file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                Files.writeString(this.file.toPath(), data, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save player-stats.yml", e);
            }
        }
    }
}
