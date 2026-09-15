/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.plugin.Plugin
 */
package org.example.integration;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

public class CustomJukeboxHook {
    private final Plugin plugin;
    private Object api;
    private boolean available;
    private Class<?> discClass;
    private Method getDiscMethod;
    private Method startPlaybackMethod;
    private Method stopPlaybackMethod;
    private Method isPlayingMethod;

    public CustomJukeboxHook(Plugin plugin) {
        this.plugin = plugin;
        this.detect();
    }

    private void detect() {
        Plugin cjb = Bukkit.getPluginManager().getPlugin("CustomJukebox");
        if (cjb == null || !cjb.isEnabled()) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("de.boondocksulfur.customjukebox.api.CustomJukeboxAPI");
            this.api = apiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            if (this.api == null) {
                return;
            }
            this.discClass = Class.forName("de.boondocksulfur.customjukebox.model.CustomDisc");
            this.getDiscMethod = apiClass.getMethod("getDisc", String.class);
            this.startPlaybackMethod = apiClass.getMethod("startPlayback", Location.class, this.discClass, Boolean.TYPE);
            this.stopPlaybackMethod = apiClass.getMethod("stopPlayback", Location.class);
            this.isPlayingMethod = apiClass.getMethod("isPlaying", Location.class);
            this.available = true;
            this.plugin.getLogger().info("CustomJukebox integration enabled.");
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("CustomJukebox detected but integration failed: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return this.available;
    }

    public boolean playDisc(Location location, String discId, boolean loop) {
        if (!this.available || discId == null || discId.isEmpty()) {
            return false;
        }
        try {
            Object disc = this.getDiscMethod.invoke(this.api, discId);
            if (disc == null) {
                this.plugin.getLogger().warning("CustomJukebox disc not found: " + discId);
                return false;
            }
            this.startPlaybackMethod.invoke(this.api, location, disc, loop);
            return true;
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("CustomJukebox playback failed: " + e.getMessage());
            return false;
        }
    }

    public void stopPlayback(Location location) {
        if (!this.available) {
            return;
        }
        try {
            this.stopPlaybackMethod.invoke(this.api, location);
        }
        catch (Exception e) {
            // Do not swallow this: if playback gets stuck, the ambient track keeps running past
            // the end of the season or the night and nobody can see why.
            this.plugin.getLogger().warning("CustomJukebox stopPlayback failed: " + e.getMessage());
        }
    }

    public boolean isPlaying(Location location) {
        if (!this.available) {
            return false;
        }
        try {
            return (Boolean)this.isPlayingMethod.invoke(this.api, location);
        }
        catch (Exception e) {
            return false;
        }
    }
}

