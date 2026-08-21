/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.TextComponent
 *  net.kyori.adventure.text.format.NamedTextColor
 *  net.kyori.adventure.text.format.TextColor
 *  net.kyori.adventure.text.format.TextDecoration
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeInstance
 *  org.bukkit.boss.BarColor
 *  org.bukkit.boss.BarFlag
 *  org.bukkit.boss.BarStyle
 *  org.bukkit.boss.BossBar
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.SkeletonHorse
 *  org.bukkit.entity.WitherSkeleton
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.inventory.EntityEquipment
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 */
package org.example.feature;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.util.Attributes;
import org.example.util.Scheduler;

public class HalloweenBossManager
implements Listener {
    private final Plugin plugin;
    private Scheduler.TaskHandle tickTask;
    private Scheduler.TaskHandle bossTickHandle;
    // Die Felder werden vom spawnenden Region-Thread geschrieben und aus Events sowie dem
    // Entity-Tick anderer Threads gelesen — volatile für die Sichtbarkeit.
    private volatile UUID bossUUID;
    private volatile UUID horseUUID;
    // Direkte Referenzen statt Bukkit.getEntity(UUID): eine gehaltene Entity lässt sich über
    // ihren eigenen Scheduler immer im richtigen Region-Thread anfassen, eine UUID nicht.
    private volatile Entity bossEntity;
    private volatile Entity horseEntity;
    private BossBar bossBar;
    private int chargeCooldown;
    private long respawnCooldownUntil;
    private volatile boolean forceSpawned;

    public HalloweenBossManager(Plugin plugin) {
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
        if (this.bossTickHandle != null) {
            this.bossTickHandle.cancel();
            this.bossTickHandle = null;
        }
        this.removeBoss();
    }

    private void tick() {
        try {
            if (this.bossUUID != null) {
                return;
            }
            if (System.currentTimeMillis() < this.respawnCooldownUntil) {
                return;
            }
            if (!SpookySeason.get().isSeasonActive()) {
                return;
            }
            if (!this.plugin.getConfig().getBoolean("halloweenBoss.enabled", true)) {
                return;
            }
            double chance = this.plugin.getConfig().getDouble("halloweenBoss.spawnChance", 0.005);
            if (ThreadLocalRandom.current().nextDouble() >= chance) {
                return;
            }
            for (World w : Bukkit.getWorlds()) {
                List<Player> players;
                long time;
                if (!SpookySeason.get().isWorldEnabled(w) || (time = w.getTime()) < 13000L || time > 23000L || (players = w.getPlayers()).isEmpty()) continue;
                Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                if (SpookySeason.get().prefs().isOptedOut(target.getUniqueId())) continue;
                // Position einmal erfassen — der Spawn muss auf Folia in der Region der
                // geplanten Location bleiben, auch wenn der Spieler weiterläuft.
                Location targetLoc = target.getLocation();
                Scheduler.runAtLocation(this.plugin, targetLoc, () -> {
                    Location loc = targetLoc.clone().add(ThreadLocalRandom.current().nextDouble(-15.0, 15.0), 0.0, ThreadLocalRandom.current().nextDouble(-15.0, 15.0));
                    loc.setY((double)(w.getHighestBlockYAt(loc) + 1));
                    if (!SpookySeason.get().regions().canSpawnAt(loc)) {
                        return;
                    }
                    this.spawnBoss(loc, false);
                });
                return;
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Boss tick error: " + e.getMessage());
        }
    }

    private void bossTick() {
        try {
            long time;
            LivingEntity le;
            Entity boss = this.bossEntity;
            if (boss == null || boss.isDead() || !boss.isValid()) {
                this.removeBoss();
                return;
            }
            if (this.bossBar != null && boss instanceof LivingEntity) {
                le = (LivingEntity)boss;
                AttributeInstance maxAttr = le.getAttribute(Attributes.MAX_HEALTH);
                if (maxAttr != null) {
                    this.bossBar.setProgress(Math.max(0.0, Math.min(1.0, le.getHealth() / maxAttr.getValue())));
                }
                Location bossLoc = boss.getLocation();
                for (Player p : boss.getWorld().getPlayers()) {
                    boolean inRange = p.getLocation().distanceSquared(bossLoc) < 4096.0;
                    if (inRange && !this.bossBar.getPlayers().contains(p)) {
                        this.bossBar.addPlayer(p);
                        continue;
                    }
                    if (inRange || !this.bossBar.getPlayers().contains(p)) continue;
                    this.bossBar.removePlayer(p);
                }
                // Spieler, die die Welt verlassen haben, fallen aus der obigen Schleife heraus
                // und würden die Bar sonst bis zum Boss-Tod behalten.
                for (Player p : List.copyOf(this.bossBar.getPlayers())) {
                    if (p.getWorld().equals(boss.getWorld())) continue;
                    this.bossBar.removePlayer(p);
                }
            }
            if (this.plugin.getConfig().getBoolean("halloweenBoss.abilities.charge", true)) {
                --this.chargeCooldown;
                if (this.chargeCooldown <= 0) {
                    this.chargeCooldown = this.plugin.getConfig().getInt("halloweenBoss.abilities.chargeIntervalSeconds", 15);
                    if (boss instanceof LivingEntity) {
                        le = (LivingEntity)boss;
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, true, true));
                        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.05);
                    }
                }
            }
            // Admin-Spawns (/spookyboss spawn) sind vom Auto-Despawn ausgenommen —
            // sonst verschwindet ein außerhalb der Season/tagsüber erzwungener Boss nach 1s.
            if (!this.forceSpawned && (!SpookySeason.get().isSeasonActive() || (time = boss.getWorld().getTime()) < 13000L || time > 23000L)) {
                this.removeBoss();
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Boss entity tick error: " + e.getMessage());
        }
    }

    public void forceSpawn(Player player) {
        this.removeBoss();
        Scheduler.runAtLocation(this.plugin, player.getLocation(), () -> {
            Location loc = player.getLocation().clone().add(5.0, 0.0, 5.0);
            loc.setY((double)(player.getWorld().getHighestBlockYAt(loc) + 1));
            this.spawnBoss(loc, true);
        });
    }

    private synchronized void spawnBoss(Location loc, boolean forced) {
        if (this.bossUUID != null) {
            // Zwei gleichzeitige Spawn-Anläufe (Auto-Tick und /spookyboss spawn) würden sonst
            // den ersten Boss als nicht mehr getrackte Waise zurücklassen.
            return;
        }
        FileConfiguration cfg = this.plugin.getConfig();
        String bossName = Lang.get("boss.name", new String[0]);
        double health = cfg.getDouble("halloweenBoss.health", 100.0);
        double damage = cfg.getDouble("halloweenBoss.damage", 8.0);
        double speed = cfg.getDouble("halloweenBoss.speed", 0.35);
        World w = loc.getWorld();
        SkeletonHorse horse = (SkeletonHorse)w.spawnEntity(loc, EntityType.SKELETON_HORSE);
        horse.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        horse.setTamed(true);
        horse.setAdult();
        horse.setInvulnerable(false);
        horse.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        horse.customName(((TextComponent)Component.text((String)bossName).color((TextColor)NamedTextColor.DARK_RED)).decorate(TextDecoration.BOLD));
        WitherSkeleton rider = (WitherSkeleton)w.spawnEntity(loc, EntityType.WITHER_SKELETON);
        rider.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        rider.customName(((TextComponent)Component.text((String)bossName).color((TextColor)NamedTextColor.DARK_RED)).decorate(TextDecoration.BOLD));
        rider.setCustomNameVisible(true);
        rider.setGlowing(true);
        rider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        rider.setHealth(health);
        rider.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
        EntityEquipment eq = rider.getEquipment();
        if (eq != null) {
            eq.setHelmet(HalloweenBossManager.safeItem(cfg.getString("halloweenBoss.armor.helmet", "CARVED_PUMPKIN")));
            eq.setChestplate(HalloweenBossManager.safeItem(cfg.getString("halloweenBoss.armor.chestplate", "")));
            eq.setLeggings(HalloweenBossManager.safeItem(cfg.getString("halloweenBoss.armor.leggings", "")));
            eq.setBoots(HalloweenBossManager.safeItem(cfg.getString("halloweenBoss.armor.boots", "")));
            eq.setItemInMainHand(HalloweenBossManager.safeItem(cfg.getString("halloweenBoss.armor.weapon", "NETHERITE_SWORD")));
            eq.setHelmetDropChance(0.0f);
            eq.setChestplateDropChance(0.0f);
            eq.setLeggingsDropChance(0.0f);
            eq.setBootsDropChance(0.0f);
            eq.setItemInMainHandDropChance(0.0f);
        }
        if (cfg.getBoolean("halloweenBoss.abilities.fireResistance", true)) {
            rider.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        }
        horse.addPassenger((Entity)rider);
        this.bossEntity = rider;
        this.horseEntity = horse;
        this.bossUUID = rider.getUniqueId();
        this.horseUUID = horse.getUniqueId();
        this.forceSpawned = forced;
        this.chargeCooldown = cfg.getInt("halloweenBoss.abilities.chargeIntervalSeconds", 15);
        this.bossBar = Bukkit.createBossBar((String)bossName, (BarColor)BarColor.RED, (BarStyle)BarStyle.SEGMENTED_10, (BarFlag[])new BarFlag[0]);
        this.bossBar.setVisible(true);
        for (Player p : w.getPlayers()) {
            if (!(p.getLocation().distanceSquared(loc) < 4096.0)) continue;
            this.bossBar.addPlayer(p);
            p.sendMessage(Lang.get("boss.spawn", new String[0]));
        }
        w.strikeLightningEffect(loc);
        // Retired-Callback: Auf Folia hängt der Boss-Tick an der Entity und verschwindet mit ihr
        // (Chunk-Entladung, Tod). Ohne Aufräumen bliebe bossUUID gesetzt und es spawnte bis zum
        // Neustart nie wieder ein Boss.
        this.bossTickHandle = Scheduler.runEntityTimer(this.plugin, (Entity)rider, this::bossTick, this::onBossRetired, 20L, 20L);
        SpookySeason.get().haunted().refreshBar();
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onHorseDamage(EntityDamageEvent e) {
        if (this.horseUUID == null) {
            return;
        }
        if (!e.getEntity().getUniqueId().equals(this.horseUUID)) {
            return;
        }
        e.setCancelled(true);
        Entity rider = this.bossEntity;
        if (rider instanceof LivingEntity) {
            LivingEntity le = (LivingEntity)rider;
            le.damage(e.getFinalDamage());
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onBossHitPlayer(EntityDamageByEntityEvent e) {
        if (this.bossUUID == null) {
            return;
        }
        if (!e.getDamager().getUniqueId().equals(this.bossUUID)) {
            return;
        }
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player victim = (Player)entity;
        if (this.plugin.getConfig().getBoolean("halloweenBoss.abilities.witherOnHit", true)) {
            int duration = this.plugin.getConfig().getInt("halloweenBoss.abilities.witherDurationTicks", 100);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 1, false, true, true));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onBossDeath(EntityDeathEvent e) {
        if (this.bossUUID == null) {
            return;
        }
        if (!e.getEntity().getUniqueId().equals(this.bossUUID)) {
            return;
        }
        Location loc = e.getEntity().getLocation();
        e.getDrops().clear();
        List<String> lootTable = this.plugin.getConfig().getStringList("halloweenBoss.lootTable");
        for (String entry : lootTable) {
            String[] parts = entry.split(":");
            Material mat = Material.matchMaterial((String)parts[0]);
            int amount = parts.length > 1 ? HalloweenBossManager.parseInt(parts[1], 1) : 1;
            if (mat == null) continue;
            loc.getWorld().dropItemNaturally(loc, new ItemStack(mat, amount));
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 1.0, 1.0, 1.0, 0.0);
        this.respawnCooldownUntil = System.currentTimeMillis() + this.plugin.getConfig().getLong("halloweenBoss.respawnCooldownSeconds", 300L) * 1000L;
        for (Player p : loc.getWorld().getPlayers()) {
            if (!(p.getLocation().distanceSquared(loc) < 4096.0)) continue;
            p.sendMessage(Lang.get("boss.defeated", new String[0]));
        }
        this.removeBoss();
    }

    public boolean isTracked(UUID id) {
        return id != null && (id.equals(this.bossUUID) || id.equals(this.horseUUID));
    }

    /** Folia hat den Boss-Tick fallen lassen, weil die Entity weg ist. */
    private void onBossRetired() {
        this.removeBoss();
    }

    private synchronized void removeBoss() {
        if (this.bossTickHandle != null) {
            this.bossTickHandle.cancel();
            this.bossTickHandle = null;
        }
        Entity rider = this.bossEntity;
        Entity mount = this.horseEntity;
        this.bossUUID = null;
        this.horseUUID = null;
        this.bossEntity = null;
        this.horseEntity = null;
        this.forceSpawned = false;
        this.despawn(rider);
        this.despawn(mount);
        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar.setVisible(false);
            this.bossBar = null;
        }
    }

    // Über den Entity-Scheduler statt über die Spawn-Position: Der Reiter bewegt sich und kann
    // längst in einer anderen Folia-Region stehen als dort, wo er gespawnt ist.
    private void despawn(Entity entity) {
        if (entity == null) {
            return;
        }
        Scheduler.runOnEntity(this.plugin, entity, () -> {
            if (entity.isValid() && !entity.isDead()) {
                entity.remove();
            }
        });
    }

    private static ItemStack safeItem(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        Material mat = Material.matchMaterial((String)materialName);
        return mat != null ? new ItemStack(mat) : new ItemStack(Material.AIR);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        }
        catch (Exception e) {
            return def;
        }
    }
}

