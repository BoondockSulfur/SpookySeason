package org.example.prefs;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.example.util.Scheduler;

public class PlayerPrefs {
    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration cfg;
    private final Object ioLock = new Object();
    private final AtomicBoolean savePending = new AtomicBoolean();

    public PlayerPrefs(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player-prefs.yml");
        this.cfg = YamlConfiguration.loadConfiguration(this.file);
    }

    public synchronized double getAmbientVol(UUID id, double def) {
        return this.cfg.getDouble(this.path(id, "ambient"), def);
    }

    public synchronized double getGhostVol(UUID id, double def) {
        return this.cfg.getDouble(this.path(id, "ghost"), def);
    }

    public synchronized double getRainVol(UUID id, double def) {
        return this.cfg.getDouble(this.path(id, "rain"), def);
    }

    public synchronized void setAmbientVol(UUID id, double v) {
        this.cfg.set(this.path(id, "ambient"), this.clamp(v));
        this.save();
    }

    public synchronized void setGhostVol(UUID id, double v) {
        this.cfg.set(this.path(id, "ghost"), this.clamp(v));
        this.save();
    }

    public synchronized void setRainVol(UUID id, double v) {
        this.cfg.set(this.path(id, "rain"), this.clamp(v));
        this.save();
    }

    public synchronized boolean isOptedOut(UUID id) {
        return this.cfg.getBoolean(this.path(id, "optout"), false);
    }

    public synchronized boolean toggleOptOut(UUID id) {
        boolean newState = !this.isOptedOut(id);
        this.cfg.set(this.path(id, "optout"), newState);
        this.save();
        return newState;
    }

    public synchronized void reset(UUID id) {
        this.cfg.set(this.base(id), null);
        this.save();
    }

    private String base(UUID id) {
        return "players." + String.valueOf(id);
    }

    private String path(UUID id, String k) {
        return this.base(id) + "." + k;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
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
                this.plugin.getLogger().log(Level.WARNING, "Could not save player-prefs.yml", e);
            }
        }
    }
}
