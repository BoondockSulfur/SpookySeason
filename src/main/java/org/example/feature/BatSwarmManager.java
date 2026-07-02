/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Sound
 *  org.bukkit.SoundCategory
 *  org.bukkit.World
 *  org.bukkit.entity.Bat
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package org.example.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.example.SpookySeason;
import org.example.util.Scheduler;

public class BatSwarmManager {
    private final Plugin plugin;
    private Scheduler.TaskHandle tickTask;
    // Auf Folia mutieren Region-Thread-Lambdas die Liste, während der Global-Tick sie bereinigt.
    private final List<TrackedBat> activeBats = new CopyOnWriteArrayList<TrackedBat>();

    public BatSwarmManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        this.stop();
        this.tickTask = Scheduler.runTimer(this.plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        for (TrackedBat tb : this.activeBats) {
            if (!tb.entity.isValid() || tb.entity.isDead()) continue;
            Scheduler.runAtLocation(this.plugin, tb.entity.getLocation(), () -> {
                if (tb.entity.isValid() && !tb.entity.isDead()) {
                    tb.entity.remove();
                }
            });
        }
        this.activeBats.clear();
    }

    private void tick() {
        try {
            if (!SpookySeason.get().isSeasonActive()) {
                return;
            }
            if (!this.plugin.getConfig().getBoolean("batSwarm.enabled", true)) {
                return;
            }
            long currentTick = Bukkit.getCurrentTick();
            List<TrackedBat> expired = new ArrayList<TrackedBat>();
            for (TrackedBat tb : this.activeBats) {
                if (tb.entity.isValid() && !tb.entity.isDead() && currentTick < tb.expiryTick) continue;
                if (tb.entity.isValid() && !tb.entity.isDead()) {
                    Scheduler.runAtLocation(this.plugin, tb.entity.getLocation(), () -> {
                        if (tb.entity.isValid() && !tb.entity.isDead()) {
                            tb.entity.remove();
                        }
                    });
                }
                expired.add(tb);
            }
            this.activeBats.removeAll(expired);
            double chance = this.plugin.getConfig().getDouble("batSwarm.spawnChance", 0.03);
            int batsPerSwarm = this.plugin.getConfig().getInt("batSwarm.batsPerSwarm", 8);
            int lifetimeTicks = this.plugin.getConfig().getInt("batSwarm.lifetimeSeconds", 5) * 20;
            for (World w : Bukkit.getWorlds()) {
                long time;
                if (!SpookySeason.get().isWorldEnabled(w) || (time = w.getTime()) < 13000L || time > 23000L) continue;
                for (Player p : w.getPlayers()) {
                    if (SpookySeason.get().prefs().isOptedOut(p.getUniqueId()) || ThreadLocalRandom.current().nextDouble() >= chance) continue;
                    long expiry = currentTick + (long)lifetimeTicks;
                    int count = batsPerSwarm;
                    // Position einmal erfassen — der Spawn muss auf Folia in der Region der
                    // geplanten Location bleiben, auch wenn der Spieler weiterläuft.
                    Location anchor = p.getLocation();
                    Scheduler.runAtLocation(this.plugin, anchor, () -> {
                        if (!p.isOnline()) {
                            return;
                        }
                        float vol = (float)SpookySeason.get().prefs().getAmbientVol(p.getUniqueId(), this.plugin.getConfig().getDouble("hauntedNight.ambientVolume", 0.7));
                        p.playSound(anchor, Sound.ENTITY_BAT_LOOP, SoundCategory.AMBIENT, vol * 0.4f, 1.2f);
                        for (int i = 0; i < count; ++i) {
                            Location loc = anchor.clone().add(ThreadLocalRandom.current().nextDouble(-4.0, 4.0), ThreadLocalRandom.current().nextDouble(0.5, 3.0), ThreadLocalRandom.current().nextDouble(-4.0, 4.0));
                            Bat bat = (Bat)anchor.getWorld().spawnEntity(loc, EntityType.BAT);
                            bat.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
                            bat.setAwake(true);
                            bat.setGlowing(true);
                            bat.setSilent(true);
                            bat.setRemoveWhenFarAway(true);
                            this.activeBats.add(new TrackedBat((Entity)bat, expiry));
                        }
                    });
                }
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("BatSwarm tick error: " + e.getMessage());
        }
    }

    private record TrackedBat(Entity entity, long expiryTick) {
    }
}

