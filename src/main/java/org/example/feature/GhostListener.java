/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.NamespacedKey
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityTargetEvent
 *  org.bukkit.persistence.PersistentDataType
 */
package org.example.feature;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.persistence.PersistentDataType;
import org.example.SpookySeason;

public class GhostListener
implements Listener {
    private final NamespacedKey KEY = SpookySeason.get().ghostMarker();

    private boolean isSpookyGhost(Entity e) {
        return e.getType() == EntityType.VEX && e.getPersistentDataContainer().has(this.KEY, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled=true)
    public void onGhostDamage(EntityDamageByEntityEvent e) {
        if (this.isSpookyGhost(e.getDamager())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onGhostTarget(EntityTargetEvent e) {
        if (this.isSpookyGhost(e.getEntity()) && e.getTarget() instanceof Player) {
            e.setCancelled(true);
        }
    }
}

