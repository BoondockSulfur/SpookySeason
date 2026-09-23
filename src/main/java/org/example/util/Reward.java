package org.example.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * A reward handed to a player: items and experience, given through the inventory API.
 *
 * <p>Configured as a section with an {@code items} list ({@code MATERIAL:amount}) and an
 * {@code xp} value. The same entries serialise to plain lines for {@code pending-rewards.yml},
 * with experience stored as {@code xp:<amount>}.
 */
public final class Reward {

    private static final String XP_PREFIX = "xp:";

    private final List<ItemStack> items;
    private final int xp;

    private Reward(List<ItemStack> items, int xp) {
        this.items = List.copyOf(items);
        this.xp = Math.max(0, xp);
    }

    public static Reward empty() {
        return new Reward(List.of(), 0);
    }

    /** Reads {@code items} and {@code xp} from a configuration section; {@code null} yields an empty reward. */
    public static Reward from(ConfigurationSection section, Logger log) {
        if (section == null) {
            return Reward.empty();
        }
        return new Reward(Reward.parseItems(section.getStringList("items"), log), section.getInt("xp", 0));
    }

    /** Reads the serialised form written by {@link #toLines()}. */
    public static Reward fromLines(List<String> lines, Logger log) {
        ArrayList<String> itemLines = new ArrayList<String>();
        int xp = 0;
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith(XP_PREFIX)) {
                xp += Reward.parseInt(trimmed.substring(XP_PREFIX.length()), 0);
                continue;
            }
            itemLines.add(trimmed);
        }
        return new Reward(Reward.parseItems(itemLines, log), xp);
    }

    public List<String> toLines() {
        ArrayList<String> lines = new ArrayList<String>();
        for (ItemStack item : this.items) {
            lines.add(item.getType().name() + ":" + item.getAmount());
        }
        if (this.xp > 0) {
            lines.add(XP_PREFIX + this.xp);
        }
        return lines;
    }

    public boolean isEmpty() {
        return this.items.isEmpty() && this.xp <= 0;
    }

    /**
     * Gives the reward to the player on that player's region thread. Items that do not fit into
     * the inventory are dropped at the player's feet.
     */
    public void give(Plugin plugin, Player player) {
        if (this.isEmpty()) {
            return;
        }
        Scheduler.runOnEntity(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            for (ItemStack item : this.items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
                for (ItemStack rest : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
            if (this.xp > 0) {
                player.giveExp(this.xp);
            }
        });
    }

    private static List<ItemStack> parseItems(List<String> lines, Logger log) {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.trim().split(":");
            Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || !material.isItem()) {
                log.warning("Unknown reward item '" + line + "' - skipped.");
                continue;
            }
            int amount = parts.length > 1 ? Math.max(1, Reward.parseInt(parts[1], 1)) : 1;
            items.add(new ItemStack(material, amount));
        }
        return items;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }
}
