package org.example.feature;

import java.util.List;
import java.util.logging.Level;
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
import org.bukkit.EntityEffect;
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
    // Written by the spawning region thread and read from events and the entity tick on other
    // threads — volatile for visibility.
    private volatile UUID bossUUID;
    private volatile UUID horseUUID;
    // Direct references rather than Bukkit.getEntity(UUID): a held entity can always be touched
    // on the correct region thread through its own scheduler, a UUID cannot.
    private volatile Entity bossEntity;
    private volatile Entity horseEntity;
    private BossBar bossBar;
    // chargeCooldown runs on the entity tick, respawnCooldownUntil is set in the death event and
    // read on the global tick — on Folia those are different threads.
    private volatile int chargeCooldown;
    private volatile long respawnCooldownUntil;
    private volatile boolean forceSpawned;
    // The boss health the plugin tracks itself. The entity attribute behind it is capped at 1024
    // by vanilla, which is nowhere near enough for a wave leader facing a full server, so the
    // entity only ever holds one chunk of this pool and is topped back up as the pool drains.
    private volatile double healthPool;
    private volatile double healthPoolMax;
    private volatile double entityMaxHealth;

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
                // Capture the position once — on Folia the spawn has to stay in the region of
                // the planned location, even if the player keeps walking.
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
            this.plugin.getLogger().log(Level.WARNING, "Boss tick error", e);
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
                // Straight off the pool, not off the entity: the entity is refilled repeatedly,
                // so its own health says nothing about how far along the fight is.
                if (this.healthPoolMax > 0.0) {
                    this.bossBar.setProgress(Math.max(0.0, Math.min(1.0, this.healthPool / this.healthPoolMax)));
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
                // Players who left the world drop out of the loop above and would otherwise keep
                // the bar until the boss dies.
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
            // Admin spawns (/spookyboss spawn) are exempt from the auto-despawn — otherwise a
            // boss forced outside the season or during the day vanishes after one second.
            if (!this.forceSpawned && (!SpookySeason.get().isSeasonActive() || (time = boss.getWorld().getTime()) < 13000L || time > 23000L)) {
                this.removeBoss();
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Boss entity tick error", e);
        }
    }

    /** True while a tracked boss is standing in the world. */
    public boolean isActive() {
        return this.bossUUID != null;
    }

    /**
     * Spawns the boss at a fixed location instead of near a player, for events that dictate the
     * place. Like {@link #forceSpawn(Player)} this counts as a forced spawn: no auto-despawn
     * outside the season or during the day.
     */
    public void forceSpawnAt(Location location) {
        this.removeBoss();
        Location anchor = location.clone();
        Scheduler.runAtLocation(this.plugin, anchor, () -> {
            Location loc = anchor.clone();
            loc.setY((double)(anchor.getWorld().getHighestBlockYAt(loc) + 1));
            this.spawnBoss(loc, true);
        });
    }

    /** Removes a running boss but leaves the auto-spawn tick alone. */
    public void despawnBoss() {
        this.removeBoss();
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
            // Two simultaneous spawn attempts (auto tick and /spookyboss spawn) would otherwise
            // leave the first boss behind as an untracked orphan.
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
        this.setBaseValue(horse, Attributes.MOVEMENT_SPEED, speed, "movement_speed");
        horse.customName(((TextComponent)Component.text((String)bossName).color((TextColor)NamedTextColor.DARK_RED)).decorate(TextDecoration.BOLD));
        WitherSkeleton rider;
        try {
            rider = (WitherSkeleton)w.spawnEntity(loc, EntityType.WITHER_SKELETON);
        }
        catch (RuntimeException e) {
            // The rider cannot always be placed — on Peaceful difficulty the server refuses every
            // monster spawn. Without this cleanup the horse that already spawned would be left
            // standing in the world as a marked, untracked orphan.
            horse.remove();
            throw e;
        }
        rider.getPersistentDataContainer().set(SpookySeason.get().entityMarker(), PersistentDataType.BYTE, (byte)1);
        rider.customName(((TextComponent)Component.text((String)bossName).color((TextColor)NamedTextColor.DARK_RED)).decorate(TextDecoration.BOLD));
        rider.setCustomNameVisible(true);
        rider.setGlowing(true);
        this.healthPoolMax = Math.max(1.0, health);
        this.healthPool = this.healthPoolMax;
        // The entity itself carries at most one chunk of the pool, because vanilla refuses a
        // max_health above 1024. Everything beyond that lives in healthPool.
        this.setBaseValue(rider, Attributes.MAX_HEALTH, Math.min(health, 1024.0), "max_health");
        // Never set above the actual maximum: if max_health could not be adjusted, setHealth()
        // throws and the spawn breaks off halfway through.
        AttributeInstance riderMax = rider.getAttribute(Attributes.MAX_HEALTH);
        this.entityMaxHealth = riderMax == null ? rider.getHealth() : riderMax.getValue();
        rider.setHealth(this.entityMaxHealth);
        this.setBaseValue(rider, Attributes.ATTACK_DAMAGE, damage, "attack_damage");
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
        // Retired callback: on Folia the boss tick hangs off the entity and disappears with it
        // (chunk unload, death). Without cleanup bossUUID would stay set and no boss would ever
        // spawn again until a restart.
        this.bossTickHandle = Scheduler.runEntityTimer(this.plugin, (Entity)rider, this::bossTick, this::onBossRetired, 20L, 20L);
        SpookySeason.get().haunted().refreshBar();
    }

    /**
     * Runs the boss health off the plugin's own pool instead of the entity attribute.
     *
     * <p>Vanilla caps {@code max_health} at 1024. Rather than fight that, the entity holds one
     * chunk of the pool: a blow that would kill it is cancelled and its health topped back up
     * while reserves remain, and once the pool is empty the next blow is allowed to finish it.
     * Hit feedback, knockback and the shared horse/rider pool all keep working as before.
     */
    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onBossDamaged(EntityDamageEvent e) {
        UUID id = this.bossUUID;
        if (id == null || !e.getEntity().getUniqueId().equals(id)) {
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity)) {
            return;
        }
        LivingEntity le = (LivingEntity)e.getEntity();
        double dealt = e.getFinalDamage();
        double left = this.healthPool - dealt;
        if (this.plugin.getConfig().getBoolean("raid.debug", false)) {
            this.plugin.getLogger().info("[boss-debug] cause=" + e.getCause()
                    + " raw=" + e.getDamage() + " final=" + dealt
                    + " entityHealth=" + le.getHealth() + "/" + this.entityMaxHealth
                    + " pool=" + this.healthPool + " -> " + Math.max(0.0, left));
        }
        this.healthPool = Math.max(0.0, left);
        if (left > 0.0 && le.getHealth() - dealt <= 0.0) {
            e.setCancelled(true);
            Scheduler.runOnEntity(this.plugin, le, () -> {
                if (le.isValid() && !le.isDead()) {
                    le.setHealth(this.entityMaxHealth);
                    le.playEffect(EntityEffect.HURT);
                }
            });
            return;
        }
        if (left <= 0.0 && le.getHealth() - dealt > 0.0) {
            // Pool spent but the entity would shrug this one off - finish it on the next tick.
            Scheduler.runEntityLater(this.plugin, le, () -> {
                if (le.isValid() && !le.isDead()) {
                    le.setHealth(0.0);
                }
            }, 1L);
        }
    }

    /** Remaining boss health from the plugin pool, for status output. */
    public double healthPool() {
        return this.healthPool;
    }

    public double healthPoolMax() {
        return this.healthPoolMax;
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

    @EventHandler
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

    /** Folia dropped the boss tick because the entity is gone. */
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
        this.healthPool = 0.0;
        this.healthPoolMax = 0.0;
        this.despawn(rider);
        this.despawn(mount);
        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar.setVisible(false);
            this.bossBar = null;
        }
    }

    // Through the entity scheduler rather than the spawn position: the rider moves and may long
    // since be standing in a different Folia region than where it spawned.
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

    /**
     * Sets an attribute base value, provided the entity has that attribute at all. Without the
     * null check a missing attribute tears the spawn apart halfway through, leaving the horse that
     * already spawned behind as a marked, untracked orphan.
     */
    private void setBaseValue(LivingEntity entity, Attribute attribute, double value, String label) {
        AttributeInstance attr = entity.getAttribute(attribute);
        if (attr == null) {
            this.plugin.getLogger().warning("Boss entity is missing the " + label + " attribute - keeping the vanilla value.");
            return;
        }
        attr.setBaseValue(value);
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

