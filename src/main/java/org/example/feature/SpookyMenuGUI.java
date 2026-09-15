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
 *  org.bukkit.Material
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package org.example.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.prefs.PlayerStats;
import org.example.util.Scheduler;

public class SpookyMenuGUI
implements Listener {
    private static final String GUI_TITLE = "\u00a76\u00a7lSpookySeason";
    private static final int SIZE = 27;
    private static final int SLOT_STATUS = 4;
    private static final int SLOT_OPTOUT = 10;
    private static final int SLOT_AMBIENT = 12;
    private static final int SLOT_GHOST = 14;
    private static final int SLOT_RAIN = 16;
    private static final int SLOT_LEADER = 22;

    // A dedicated holder rather than comparing titles: collision-proof against foreign
    // inventories that happen to share a title.
    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return this.inventory;
        }
    }

    public void open(Player p) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, (int)27, (String)GUI_TITLE);
        holder.inventory = inv;
        ItemStack filler = SpookyMenuGUI.item(Material.BLACK_STAINED_GLASS_PANE, " ", new String[0]);
        for (int i = 0; i < 27; ++i) {
            inv.setItem(i, filler);
        }
        SpookySeason plugin = SpookySeason.get();
        UUID id = p.getUniqueId();
        boolean active = plugin.isSeasonActive();
        inv.setItem(4, SpookyMenuGUI.item(active ? Material.JACK_O_LANTERN : Material.BARRIER, Lang.get("gui.status", new String[0]), active ? Lang.get("gui.status-active", new String[0]) : Lang.get("gui.status-inactive", new String[0])));
        boolean optedOut = plugin.prefs().isOptedOut(id);
        inv.setItem(10, SpookyMenuGUI.item(optedOut ? Material.RED_WOOL : Material.LIME_WOOL, Lang.get("gui.optout", new String[0]), optedOut ? Lang.get("gui.optout-enabled", new String[0]) : Lang.get("gui.optout-disabled", new String[0]), Lang.get("gui.click-toggle", new String[0])));
        double cfgA = plugin.getConfig().getDouble("hauntedNight.ambientVolume", 0.7);
        double cfgG = plugin.getConfig().getDouble("hauntedNight.ghostVolume", 0.5);
        double cfgR = plugin.getConfig().getDouble("pumpkinRain.volume", 0.6);
        double volA = plugin.prefs().getAmbientVol(id, cfgA);
        double volG = plugin.prefs().getGhostVol(id, cfgG);
        double volR = plugin.prefs().getRainVol(id, cfgR);
        inv.setItem(12, this.volumeItem(Lang.get("gui.vol-ambient", new String[0]), volA));
        inv.setItem(14, this.volumeItem(Lang.get("gui.vol-ghost", new String[0]), volG));
        inv.setItem(16, this.volumeItem(Lang.get("gui.vol-rain", new String[0]), volR));
        inv.setItem(22, this.leaderboardItem());
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player p = (Player)humanEntity;
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }
        SpookySeason plugin = SpookySeason.get();
        UUID id = p.getUniqueId();
        switch (slot) {
            case 10: {
                plugin.prefs().toggleOptOut(id);
                this.reopen(p);
                break;
            }
            case 12: {
                double cur = plugin.prefs().getAmbientVol(id, plugin.getConfig().getDouble("hauntedNight.ambientVolume", 0.7));
                double next = e.isShiftClick() ? cur - 0.1 : cur + 0.1;
                plugin.prefs().setAmbientVol(id, Math.max(0.0, Math.min(1.0, next)));
                this.reopen(p);
                break;
            }
            case 14: {
                double cur = plugin.prefs().getGhostVol(id, plugin.getConfig().getDouble("hauntedNight.ghostVolume", 0.5));
                double next = e.isShiftClick() ? cur - 0.1 : cur + 0.1;
                plugin.prefs().setGhostVol(id, Math.max(0.0, Math.min(1.0, next)));
                this.reopen(p);
                break;
            }
            case 16: {
                double cur = plugin.prefs().getRainVol(id, plugin.getConfig().getDouble("pumpkinRain.volume", 0.6));
                double next = e.isShiftClick() ? cur - 0.1 : cur + 0.1;
                plugin.prefs().setRainVol(id, Math.max(0.0, Math.min(1.0, next)));
                this.reopen(p);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        // Without drag protection players can drag items into the GUI and lose them on close,
        // because the inventory is discarded.
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        e.setCancelled(true);
    }

    // Do not reopen the inventory in the middle of the click event — the client tends to be left
    // with ghost items from the old view. One tick later the event is done.
    private void reopen(Player p) {
        Scheduler.runEntityLater(SpookySeason.get(), p, () -> {
            if (p.isOnline()) {
                this.open(p);
            }
        }, 1L);
    }

    private ItemStack volumeItem(String name, double vol) {
        int percent = (int)Math.round(vol * 100.0);
        return SpookyMenuGUI.item(Material.NOTE_BLOCK, name, "\u00a7b" + percent + "%", Lang.get("gui.click-increase", new String[0]), Lang.get("gui.shift-decrease", new String[0]));
    }

    private ItemStack leaderboardItem() {
        ArrayList<String> lore = new ArrayList<String>();
        List<Map.Entry<UUID, Integer>> top = SpookySeason.get().stats().getTopTreats(5);
        if (top.isEmpty()) {
            lore.add(Lang.get("command.spooky.top-empty", new String[0]));
        } else {
            int rank = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                lore.add(Lang.get("command.spooky.top-entry", "rank", String.valueOf(rank++), "player", PlayerStats.resolveName(entry.getKey()), "treats", String.valueOf(entry.getValue())));
            }
        }
        return SpookyMenuGUI.item(Material.GOLDEN_APPLE, Lang.get("gui.leaderboard", new String[0]), lore.toArray(new String[0]));
    }

    private static ItemStack item(Material mat, String name, String ... lore) {
        ItemStack is = new ItemStack(mat);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text((String)name).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                ArrayList<Component> loreList = new ArrayList<Component>();
                for (String line : lore) {
                    loreList.add(((TextComponent)Component.text((String)line).color((TextColor)NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(loreList);
            }
            is.setItemMeta(meta);
        }
        return is;
    }
}

