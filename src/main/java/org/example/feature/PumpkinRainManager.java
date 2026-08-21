/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.SoundCategory
 *  org.bukkit.World
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.util.Vector
 */
package org.example.feature;

import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.example.SpookySeason;
import org.example.util.Scheduler;

public class PumpkinRainManager {
    private final Plugin plugin;
    private Scheduler.TaskHandle task;
    private boolean running;

    public PumpkinRainManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.start(false);
    }

    public void start(boolean force) {
        if (!force && !SpookySeason.get().isSeasonActive()) {
            return;
        }
        if (!force && !this.plugin.getConfig().getBoolean("pumpkinRain.enabled", true)) {
            return;
        }
        this.stop();
        final int period = this.plugin.getConfig().getInt("pumpkinRain.tickPeriod", 10);
        int durationS = this.plugin.getConfig().getInt("pumpkinRain.maxDurationSeconds", 60);
        final int radius = this.plugin.getConfig().getInt("pumpkinRain.spawnRadius", 10);
        final int intensity = this.plugin.getConfig().getInt("pumpkinRain.intensity", 5);
        final double cfgVol = this.plugin.getConfig().getDouble("pumpkinRain.volume", 0.6);
        final int maxTicks = durationS * 20;
        this.running = true;
        this.task = Scheduler.runTimer(this.plugin, new Runnable(){
            int ticks = 0;

            @Override
            public void run() {
                try {
                    this.ticks += period;
                    if (this.ticks >= maxTicks) {
                        PumpkinRainManager.this.stop();
                        return;
                    }
                    for (World w : Bukkit.getWorlds()) {
                        if (!SpookySeason.get().isWorldEnabled(w)) continue;
                        for (Player p : w.getPlayers()) {
                            if (SpookySeason.get().prefs().isOptedOut(p.getUniqueId())) continue;
                            // Position einmal erfassen und im Lambda weiterverwenden — auf Folia muss
                            // der Spawn in der Region der geplanten Location bleiben, auch wenn der
                            // Spieler sich inzwischen bewegt hat.
                            Location anchor = p.getLocation();
                            Scheduler.runAtLocation(PumpkinRainManager.this.plugin, anchor, () -> {
                                if (!p.isOnline()) {
                                    return;
                                }
                                float vol = (float)SpookySeason.get().prefs().getRainVol(p.getUniqueId(), cfgVol);
                                World pw = anchor.getWorld();
                                for (int i = 0; i < intensity; ++i) {
                                    Location base = anchor.clone().add(PumpkinRainManager.rand(radius), 8.0 + Math.random() * 4.0, PumpkinRainManager.rand(radius));
                                    Item drop = pw.dropItem(base, new ItemStack(Material.CARVED_PUMPKIN));
                                    drop.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
                                    drop.setPickupDelay(Short.MAX_VALUE);
                                    drop.setVelocity(new Vector(0.0, -0.4, 0.0));
                                    pw.spawnParticle(Particle.FALLING_DRIPSTONE_LAVA, base, 8, 0.3, 0.3, 0.3, 0.01);
                                    p.playSound(base, Sound.BLOCK_PUMPKIN_CARVE, SoundCategory.BLOCKS, vol, 0.9f);
                                    Scheduler.runEntityLater(PumpkinRainManager.this.plugin, (Entity)drop, () -> {
                                        if (!drop.isDead() && drop.isValid()) {
                                            drop.remove();
                                        }
                                    }, 300L);
                                }
                            });
                        }
                    }
                }
                catch (Exception e) {
                    PumpkinRainManager.this.plugin.getLogger().log(Level.WARNING, "PumpkinRain tick error", e);
                }
            }
        }, 1L, period);
    }

    public boolean isRunning() {
        return this.running;
    }

    public void stop() {
        this.running = false;
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private static double rand(int radius) {
        return Math.random() * (double)radius * 2.0 - (double)radius;
    }
}

