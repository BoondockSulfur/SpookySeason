/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Material
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 */
package org.example.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.example.lang.Lang;

public final class Items {
    private static final Map<String, Material> ALIASES = Map.of("CANDY_APPLE", Material.GOLDEN_APPLE, "COOKIES", Material.COOKIE);
    private static final Map<String, String> DISPLAY_NAMES = Map.of("CANDY_APPLE", "item.candy-apple", "COOKIES", "item.cookies", "PUMPKIN_PIE", "item.pumpkin-pie");

    private Items() {
    }

    public static void giveRandomTreat(Player p, FileConfiguration cfg) {
        List<String> list = cfg.getStringList("trickOrTreat.treatItems");
        if (list.isEmpty()) {
            list = List.of("COOKIE:4", "PUMPKIN_PIE:1");
        }
        String pick = list.get(ThreadLocalRandom.current().nextInt(list.size()));
        String[] parts = pick.split(":");
        String key = parts[0];
        int amount = parts.length > 1 ? Items.parseIntSafe(parts[1], 1) : 1;
        Material mat = ALIASES.getOrDefault(key, Material.matchMaterial((String)key));
        if (mat == null) {
            mat = Material.COOKIE;
        }
        ItemStack stack = new ItemStack(mat, amount);
        String langKey = DISPLAY_NAMES.get(key);
        if (langKey != null) {
            Items.rename(stack, Lang.get(langKey, new String[0]));
        }
        // With a full inventory the treat would otherwise vanish without a word.
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    private static void rename(ItemStack is, String name) {
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.displayName((Component)Component.text((String)name));
            is.setItemMeta(meta);
        }
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        }
        catch (Exception ignored) {
            return def;
        }
    }
}

