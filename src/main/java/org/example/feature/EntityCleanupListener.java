package org.example.feature;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.example.SpookySeason;
import org.example.util.Scheduler;

public class EntityCleanupListener
implements Listener {
    private final SpookySeason plugin;

    public EntityCleanupListener(SpookySeason plugin) {
        this.plugin = plugin;
    }

    // Marked plugin entities are persistent: a boss from an unloaded chunk, or leftovers from a
    // shutdown or crash, would otherwise reappear as orphaned mobs with no bossbar and no loot
    // handling. Anything no longer tracked at chunk load time is removed.
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (!entity.getPersistentDataContainer().has(this.plugin.entityMarker(), PersistentDataType.BYTE)) continue;
            if (this.plugin.boss() != null && this.plugin.boss().isTracked(entity.getUniqueId())) continue;
            // A running raid's attackers and target are tracked and must not vanish mid-assault
            // just because their chunk was reloaded.
            if (this.plugin.raid() != null && this.plugin.raid().isTracked(entity.getUniqueId())) continue;
            // Remove one tick later: deleting entities in the middle of the load is delicate —
            // the server is busy with this very list at that moment.
            Scheduler.runEntityLater(this.plugin, entity, () -> {
                if (entity.isValid() && !entity.isDead()) {
                    entity.remove();
                }
            }, 1L);
        }
    }

    // Hoppers ignore the pickup delay on pumpkin rain items — without this cancel the decorative
    // drops can be farmed with a field of hoppers.
    @EventHandler(ignoreCancelled=true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (e.getItem().getPersistentDataContainer().has(this.plugin.entityMarker(), PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }
}
