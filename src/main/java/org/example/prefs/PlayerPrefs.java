package org.example.prefs;

import java.io.File;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.example.util.YamlSaver;

public class PlayerPrefs {
    private final YamlConfiguration cfg;
    private final YamlSaver saver;

    public PlayerPrefs(Plugin plugin) {
        File file = new File(plugin.getDataFolder(), "player-prefs.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        this.saver = new YamlSaver(plugin, file, this::snapshot);
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
        this.saver.save();
    }

    public synchronized void setGhostVol(UUID id, double v) {
        this.cfg.set(this.path(id, "ghost"), this.clamp(v));
        this.saver.save();
    }

    public synchronized void setRainVol(UUID id, double v) {
        this.cfg.set(this.path(id, "rain"), this.clamp(v));
        this.saver.save();
    }

    public synchronized boolean isOptedOut(UUID id) {
        return this.cfg.getBoolean(this.path(id, "optout"), false);
    }

    public synchronized boolean toggleOptOut(UUID id) {
        boolean newState = !this.isOptedOut(id);
        this.cfg.set(this.path(id, "optout"), newState);
        this.saver.save();
        return newState;
    }

    public synchronized void reset(UUID id) {
        this.cfg.set(this.base(id), null);
        this.saver.save();
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

    private String base(UUID id) {
        return "players." + String.valueOf(id);
    }

    private String path(UUID id, String k) {
        return this.base(id) + "." + k;
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
