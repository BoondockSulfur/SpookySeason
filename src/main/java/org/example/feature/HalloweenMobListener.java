package org.example.feature;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.example.SpookySeason;

public class HalloweenMobListener
implements Listener {
    private final SpookySeason plugin;

    public HalloweenMobListener(SpookySeason plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled=true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!this.plugin.isSeasonActive()) {
            return;
        }
        if (!this.plugin.isWorldEnabled(e.getLocation().getWorld())) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("halloweenMobs.enabled", true)) {
            return;
        }
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        EntityType type = e.getEntityType();
        if (type != EntityType.ZOMBIE && type != EntityType.SKELETON) {
            return;
        }
        double chance = this.plugin.getConfig().getDouble("halloweenMobs.pumpkinHeadChance", 0.3);
        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        LivingEntity le = e.getEntity();
        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            eq.setHelmetDropChance(0.0f);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (!this.plugin.isSeasonActive()) {
            return;
        }
        if (!this.plugin.isWorldEnabled(e.getEntity().getWorld())) {
            return;
        }
        if (!this.plugin.getConfig().getBoolean("halloweenMobs.enabled", true)) {
            return;
        }
        if (!(e.getEntity() instanceof Creeper)) {
            return;
        }
        double chance = this.plugin.getConfig().getDouble("halloweenMobs.creeperPumpkinDropChance", 0.1);
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            e.getDrops().add(new ItemStack(Material.CARVED_PUMPKIN));
        }
    }
}

