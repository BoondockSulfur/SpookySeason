/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.SoundCategory
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 */
package org.example.feature;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.SpookySeason;

public class JumpScareListener
implements Listener {
    private final SpookySeason plugin;
    // Events kommen auf Folia von unterschiedlichen Region-Threads.
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<UUID, Long>();

    public JumpScareListener(SpookySeason plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled=true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (!this.plugin.isSeasonActive()) {
            return;
        }
        if (!this.plugin.isWorldEnabled(e.getBlock().getWorld())) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("jumpScare.enabled", true)) {
            return;
        }
        Player p = e.getPlayer();
        if (this.plugin.prefs().isOptedOut(p.getUniqueId())) {
            return;
        }
        int maxLight = this.plugin.getConfig().getInt("jumpScare.darkLightLevel", 4);
        // Gesamtlicht (Block + Himmel) — nur Blocklicht würde tagsüber im Freien auslösen.
        if (e.getBlock().getLightLevel() > maxLight) {
            return;
        }
        long now = System.currentTimeMillis();
        int cooldownSec = this.plugin.getConfig().getInt("jumpScare.cooldownSeconds", 120);
        long expiry = this.cooldown.getOrDefault(p.getUniqueId(), 0L);
        if (now < expiry) {
            return;
        }
        double chance = this.plugin.getConfig().getDouble("jumpScare.chance", 0.02);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        this.cooldown.put(p.getUniqueId(), now + (long)cooldownSec * 1000L);
        float vol = (float)this.plugin.prefs().getGhostVol(p.getUniqueId(), this.plugin.getConfig().getDouble("hauntedNight.ghostVolume", 0.5));
        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.AMBIENT, vol, 1.5f);
        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, vol * 0.5f, 0.5f);
        p.spawnParticle(Particle.SOUL, p.getLocation().add(0.0, 1.0, 0.0), 30, 1.5, 1.0, 1.5, 0.02);
        p.spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0.0, 1.0, 0.0), 20, 1.0, 0.5, 1.0, 0.05);
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.cooldown.remove(e.getPlayer().getUniqueId());
    }
}

