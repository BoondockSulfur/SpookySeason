package org.example.feature;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.example.SpookySeason;

public class EntityCleanupListener
implements Listener {
    private final SpookySeason plugin;

    public EntityCleanupListener(SpookySeason plugin) {
        this.plugin = plugin;
    }

    // Markierte Plugin-Entities sind persistent: Ein Boss aus einem entladenen Chunk oder
    // Reste nach Shutdown/Crash tauchen sonst als verwaiste Mobs (ohne BossBar/Loot-Handling)
    // wieder auf. Alles, was beim Chunk-Load nicht mehr getrackt ist, wird entfernt.
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (!entity.getPersistentDataContainer().has(this.plugin.entityMarker(), PersistentDataType.BYTE)) continue;
            if (this.plugin.boss() != null && this.plugin.boss().isTracked(entity.getUniqueId())) continue;
            entity.remove();
        }
    }

    // Hopper ignorieren das Pickup-Delay der Kürbisregen-Items — ohne diesen Cancel
    // lassen sich die Deko-Drops mit einem Hopper-Feld farmen.
    @EventHandler(ignoreCancelled=true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (e.getItem().getPersistentDataContainer().has(this.plugin.entityMarker(), PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }
}
