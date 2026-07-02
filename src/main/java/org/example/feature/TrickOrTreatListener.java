/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  org.bukkit.Location
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.SoundCategory
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.data.Bisected$Half
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.type.Door
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Villager
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 */
package org.example.feature;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Items;
import org.example.util.Scheduler;

public class TrickOrTreatListener
implements Listener {
    private final SpookySeason plugin;
    // Events kommen auf Folia von unterschiedlichen Region-Threads.
    private final Map<String, Long> cooldown = new ConcurrentHashMap<String, Long>();

    public TrickOrTreatListener(SpookySeason plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDoorClick(PlayerInteractEvent e) {
        BlockData blockData;
        if (!this.plugin.isSeasonActive()) {
            return;
        }
        if (!this.plugin.isWorldEnabled(e.getPlayer().getWorld())) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("trickOrTreat.enabled", true)) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Interact-Events feuern pro Hand; ohne Filter läuft der Off-Hand-Klick
        // direkt in den frisch gesetzten Cooldown und spammt die Cooldown-Meldung.
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null || !((blockData = b.getBlockData()) instanceof Door)) {
            return;
        }
        Door door = (Door)blockData;
        Player p = e.getPlayer();
        if (this.plugin.prefs().isOptedOut(p.getUniqueId())) {
            return;
        }
        if (!this.plugin.regions().canPlayerInteract(p, b.getLocation())) {
            return;
        }
        String key = this.doorCooldownKey(p.getUniqueId(), b, door);
        if (!this.checkCooldown(p, key)) {
            return;
        }
        this.rollTrickOrTreat(p);
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent e) {
        if (!this.plugin.isSeasonActive()) {
            return;
        }
        if (!this.plugin.isWorldEnabled(e.getPlayer().getWorld())) {
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("trickOrTreat.enabled", true)) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("trickOrTreatVillagers.enabled", true)) {
            return;
        }
        Entity entity = e.getRightClicked();
        if (!(entity instanceof Villager)) {
            return;
        }
        Villager villager = (Villager)entity;
        Player p = e.getPlayer();
        if (this.plugin.prefs().isOptedOut(p.getUniqueId())) {
            return;
        }
        if (!this.plugin.regions().canPlayerInteract(p, villager.getLocation())) {
            return;
        }
        e.setCancelled(true);
        String key = this.villagerCooldownKey(p.getUniqueId(), villager);
        if (!this.checkCooldown(p, key)) {
            return;
        }
        this.rollTrickOrTreat(p);
    }

    private boolean checkCooldown(Player p, String key) {
        long now = System.currentTimeMillis();
        this.cooldown.entrySet().removeIf(entry -> (Long)entry.getValue() < now);
        // Zusätzlich zum Pro-Tür/Villager-Cooldown ein globaler Pro-Spieler-Cooldown,
        // sonst lässt sich mit vielen Türen unbegrenzt für die Season-Rewards farmen.
        String globalKey = String.valueOf(p.getUniqueId()) + ":global";
        if (now < this.cooldown.getOrDefault(key, 0L) || now < this.cooldown.getOrDefault(globalKey, 0L)) {
            p.sendMessage(Lang.get("trickortreat.cooldown", new String[0]));
            return false;
        }
        this.cooldown.put(key, now + (long)this.plugin.getConfig().getInt("trickOrTreat.cooldownSeconds", 90) * 1000L);
        int globalSeconds = this.plugin.getConfig().getInt("trickOrTreat.globalCooldownSeconds", 30);
        if (globalSeconds > 0) {
            this.cooldown.put(globalKey, now + (long)globalSeconds * 1000L);
        }
        return true;
    }

    private void rollTrickOrTreat(Player p) {
        double chance = this.plugin.getConfig().getDouble("trickOrTreat.chanceTreat", 0.7);
        if (Math.random() < chance) {
            Items.giveRandomTreat(p, this.plugin.getConfig());
            this.plugin.stats().addTreat(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, SoundCategory.PLAYERS, 1.0f, 1.1f);
            p.spawnParticle(Particle.HEART, p.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.5, 0.3, 0.01);
            p.sendMessage(Lang.get("trickortreat.treat", new String[0]));
        } else {
            this.meanTrick(p);
        }
    }

    private void meanTrick(Player p) {
        World w = p.getWorld();
        w.strikeLightningEffect(p.getLocation());
        double cfgGhost = this.plugin.getConfig().getDouble("hauntedNight.ghostVolume", 0.5);
        float vol = (float)this.plugin.prefs().getGhostVol(p.getUniqueId(), cfgGhost);
        p.playSound(p.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, SoundCategory.AMBIENT, vol, 0.7f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        List<String> mobs = this.plugin.getConfig().getStringList("trickOrTreat.meanMobs");
        EntityType type = EntityType.WITCH;
        if (!mobs.isEmpty()) {
            String name = mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())).trim().toUpperCase(Locale.ROOT);
            try {
                type = EntityType.valueOf(name);
            }
            catch (IllegalArgumentException ex) {
                this.plugin.getLogger().warning("Invalid trickOrTreat.meanMobs entry: " + name);
            }
        }
        Location spawnLoc = p.getLocation().add(0.0, 0.0, 1.0);
        if (!this.plugin.regions().canSpawnAt(spawnLoc)) {
            return;
        }
        Entity mob = w.spawnEntity(spawnLoc, type);
        mob.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        mob.customName(((TextComponent)Component.text((String)Lang.get("trickortreat.surprise", new String[0])).color((TextColor)NamedTextColor.LIGHT_PURPLE)).decorate(TextDecoration.BOLD));
        mob.setCustomNameVisible(true);
        mob.setGlowing(true);
        int buff = this.plugin.getConfig().getInt("trickOrTreat.meanMobBuffSeconds", 20);
        if (mob instanceof LivingEntity) {
            LivingEntity le = (LivingEntity)mob;
            le.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * buff, 0));
        }
        w.spawnParticle(Particle.INSTANT_EFFECT, mob.getLocation().add(0.0, 1.0, 0.0), 16, 0.4, 0.6, 0.4, 0.01);
        Scheduler.runEntityLater((Plugin)this.plugin, mob, () -> {
            if (mob.isValid() && !mob.isDead()) {
                mob.remove();
            }
        }, 20L * (long)buff);
    }

    private String doorCooldownKey(UUID player, Block block, Door door) {
        int y = door.getHalf() == Bisected.Half.TOP ? block.getY() - 1 : block.getY();
        return String.valueOf(player) + ":door:" + block.getWorld().getName() + ":" + block.getX() + ":" + y + ":" + block.getZ();
    }

    private String villagerCooldownKey(UUID player, Villager villager) {
        return String.valueOf(player) + ":villager:" + String.valueOf(villager.getUniqueId());
    }
}

