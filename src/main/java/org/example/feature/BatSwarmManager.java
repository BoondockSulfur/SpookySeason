package org.example.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
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
    // On Folia, region-thread lambdas mutate this list while the global tick prunes it.
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
            // Entity scheduler rather than runAtLocation: the bat flies and is no longer
            // necessarily in the region it spawned in.
            Scheduler.runOnEntity(this.plugin, tb.entity, () -> {
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
            // NOT Bukkit.getCurrentTick(): on Folia that is only valid inside a region tick and
            // throws "No currently ticking region" on the global scheduler — it broke bat swarms
            // on Folia completely.
            long now = System.currentTimeMillis();
            List<TrackedBat> expired = new ArrayList<TrackedBat>();
            for (TrackedBat tb : this.activeBats) {
                if (tb.entity.isValid() && !tb.entity.isDead() && now < tb.expiryMillis) continue;
                if (tb.entity.isValid() && !tb.entity.isDead()) {
                    Scheduler.runOnEntity(this.plugin, tb.entity, () -> {
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
            long lifetimeMillis = (long)this.plugin.getConfig().getInt("batSwarm.lifetimeSeconds", 5) * 1000L;
            for (World w : Bukkit.getWorlds()) {
                long time;
                if (!SpookySeason.get().isWorldEnabled(w) || (time = w.getTime()) < 13000L || time > 23000L) continue;
                for (Player p : w.getPlayers()) {
                    if (SpookySeason.get().prefs().isOptedOut(p.getUniqueId()) || ThreadLocalRandom.current().nextDouble() >= chance) continue;
                    long expiry = now + lifetimeMillis;
                    int count = batsPerSwarm;
                    // Capture the position once — on Folia the spawn has to stay in the region
                    // of the planned location, even if the player keeps walking.
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
            this.plugin.getLogger().log(Level.WARNING, "BatSwarm tick error", e);
        }
    }

    private record TrackedBat(Entity entity, long expiryMillis) {
    }
}

