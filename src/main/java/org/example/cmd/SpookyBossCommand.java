/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package org.example.cmd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.example.SpookySeason;
import org.example.lang.Lang;

public class SpookyBossCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> SUBS = List.of("spawn", "remove", "info", "set", "armor", "ability", "loot");
    private static final List<String> SET_KEYS = List.of("health", "damage", "speed", "spawnChance", "enabled");
    private static final List<String> ARMOR_SLOTS = List.of("helmet", "chestplate", "leggings", "boots", "weapon");
    private static final List<String> ABILITY_KEYS = List.of("witherOnHit", "witherDurationTicks", "fireResistance", "charge", "chargeIntervalSeconds");
    private static final List<String> LOOT_SUBS = List.of("add", "remove", "list");

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("command.boss.usage", "label", label));
            return true;
        }
        SpookySeason plugin = SpookySeason.get();
        FileConfiguration cfg = plugin.getConfig();
        block9 : switch (args[0].toLowerCase()) {
            case "spawn": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    return true;
                }
                Player p = (Player)sender;
                plugin.boss().forceSpawn(p);
                p.sendMessage(Lang.get("command.spooky.spawn-boss", new String[0]));
                break;
            }
            case "remove": {
                plugin.boss().stop();
                plugin.boss().start();
                sender.sendMessage(Lang.get("command.boss.removed", new String[0]));
                break;
            }
            case "info": {
                sender.sendMessage(Lang.get("command.boss.info-header", new String[0]));
                sender.sendMessage("\u00a77enabled: \u00a7e" + cfg.getBoolean("halloweenBoss.enabled", true));
                sender.sendMessage("\u00a77health: \u00a7e" + cfg.getDouble("halloweenBoss.health", 100.0));
                sender.sendMessage("\u00a77damage: \u00a7e" + cfg.getDouble("halloweenBoss.damage", 8.0));
                sender.sendMessage("\u00a77speed: \u00a7e" + cfg.getDouble("halloweenBoss.speed", 0.35));
                sender.sendMessage("\u00a77spawnChance: \u00a7e" + cfg.getDouble("halloweenBoss.spawnChance", 0.005));
                sender.sendMessage("\u00a76\u00a7l\u2500\u2500 Armor \u2500\u2500");
                for (String slot : ARMOR_SLOTS) {
                    sender.sendMessage("\u00a77" + slot + ": \u00a7e" + cfg.getString("halloweenBoss.armor." + slot, "(none)"));
                }
                sender.sendMessage("\u00a76\u00a7l\u2500\u2500 Abilities \u2500\u2500");
                sender.sendMessage("\u00a77witherOnHit: \u00a7e" + cfg.getBoolean("halloweenBoss.abilities.witherOnHit", true));
                sender.sendMessage("\u00a77witherDurationTicks: \u00a7e" + cfg.getInt("halloweenBoss.abilities.witherDurationTicks", 100));
                sender.sendMessage("\u00a77fireResistance: \u00a7e" + cfg.getBoolean("halloweenBoss.abilities.fireResistance", true));
                sender.sendMessage("\u00a77charge: \u00a7e" + cfg.getBoolean("halloweenBoss.abilities.charge", true));
                sender.sendMessage("\u00a77chargeIntervalSeconds: \u00a7e" + cfg.getInt("halloweenBoss.abilities.chargeIntervalSeconds", 15));
                sender.sendMessage("\u00a76\u00a7l\u2500\u2500 Loot \u2500\u2500");
                List<String> loot = cfg.getStringList("halloweenBoss.lootTable");
                if (loot.isEmpty()) {
                    sender.sendMessage("\u00a77(empty)");
                    break;
                }
                for (int i = 0; i < loot.size(); ++i) {
                    sender.sendMessage("\u00a7e#" + (i + 1) + " \u00a77" + loot.get(i));
                }
                break;
            }
            case "set": {
                if (args.length < 3) {
                    sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " set <" + String.join((CharSequence)"|", SET_KEYS) + "> <value>");
                    return true;
                }
                String key = args[1].toLowerCase();
                String value = args[2];
                switch (key) {
                    case "health": {
                        double v = SpookyBossCommand.parseDouble(value, -1.0);
                        if (v <= 0.0) {
                            sender.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                            return true;
                        }
                        cfg.set("halloweenBoss.health", (Object)v);
                        break;
                    }
                    case "damage": {
                        double v = SpookyBossCommand.parseDouble(value, -1.0);
                        if (v < 0.0) {
                            sender.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                            return true;
                        }
                        cfg.set("halloweenBoss.damage", (Object)v);
                        break;
                    }
                    case "speed": {
                        double v = SpookyBossCommand.parseDouble(value, -1.0);
                        if (v <= 0.0) {
                            sender.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                            return true;
                        }
                        cfg.set("halloweenBoss.speed", (Object)v);
                        break;
                    }
                    case "spawnchance": {
                        double v = SpookyBossCommand.parseDouble(value, -1.0);
                        if (v < 0.0 || v > 1.0) {
                            sender.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                            return true;
                        }
                        cfg.set("halloweenBoss.spawnChance", (Object)v);
                        break;
                    }
                    case "enabled": {
                        cfg.set("halloweenBoss.enabled", (Object)Boolean.parseBoolean(value));
                        break;
                    }
                    default: {
                        sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " set <" + String.join((CharSequence)"|", SET_KEYS) + "> <value>");
                        return true;
                    }
                }
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.boss.set-success", "key", key, "value", value));
                break;
            }
            case "armor": {
                if (args.length < 3) {
                    sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " armor <" + String.join((CharSequence)"|", ARMOR_SLOTS) + "> <MATERIAL|none>");
                    return true;
                }
                String slot = args[1].toLowerCase();
                if (!ARMOR_SLOTS.contains(slot)) {
                    sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " armor <" + String.join((CharSequence)"|", ARMOR_SLOTS) + "> <MATERIAL|none>");
                    return true;
                }
                String matName = args[2].equalsIgnoreCase("none") ? "" : args[2].toUpperCase();
                if (!matName.isEmpty() && Material.matchMaterial((String)matName) == null) {
                    sender.sendMessage("\u00a7cUnknown material: " + matName);
                    return true;
                }
                cfg.set("halloweenBoss.armor." + slot, (Object)matName);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.boss.set-success", "key", "armor." + slot, "value", matName.isEmpty() ? "none" : matName));
                break;
            }
            case "ability": {
                if (args.length < 3) {
                    sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " ability <" + String.join((CharSequence)"|", ABILITY_KEYS) + "> <value>");
                    return true;
                }
                // Auf die kanonische Schreibweise normalisieren — sonst legt z.B.
                // "witheronhit" einen toten Config-Key an, den der Code nie liest.
                String aKey = ABILITY_KEYS.stream().filter(k -> k.equalsIgnoreCase(args[1])).findFirst().orElse(args[1]);
                String aVal = args[2];
                String configPath = "halloweenBoss.abilities." + aKey;
                switch (aKey.toLowerCase()) {
                    case "witheronhit": 
                    case "fireresistance": 
                    case "charge": {
                        cfg.set(configPath, (Object)Boolean.parseBoolean(aVal));
                        break;
                    }
                    case "witherdurationticks": 
                    case "chargeintervalseconds": {
                        int v = (int)SpookyBossCommand.parseDouble(aVal, -1.0);
                        if (v <= 0) {
                            sender.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                            return true;
                        }
                        cfg.set(configPath, (Object)v);
                        break;
                    }
                    default: {
                        sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " ability <" + String.join((CharSequence)"|", ABILITY_KEYS) + "> <value>");
                        return true;
                    }
                }
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.boss.set-success", "key", aKey, "value", aVal));
                break;
            }
            case "loot": {
                if (args.length < 2) {
                    sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " loot <add|remove|list>");
                    return true;
                }
                ArrayList<String> loot = new ArrayList<String>(cfg.getStringList("halloweenBoss.lootTable"));
                switch (args[1].toLowerCase()) {
                    case "list": {
                        if (loot.isEmpty()) {
                            sender.sendMessage("\u00a77Loot table is empty.");
                            break;
                        }
                        sender.sendMessage(Lang.get("command.boss.loot-header", new String[0]));
                        for (int i = 0; i < loot.size(); ++i) {
                            sender.sendMessage("\u00a7e#" + (i + 1) + " \u00a77" + (String)loot.get(i));
                        }
                        break block9;
                    }
                    case "add": {
                        if (args.length < 3) {
                            sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " loot add <MATERIAL:amount>");
                            return true;
                        }
                        String entry = args[2].toUpperCase();
                        String[] entryParts = entry.split(":");
                        if (Material.matchMaterial((String)entryParts[0]) == null) {
                            sender.sendMessage("\u00a7cUnknown material: " + entryParts[0]);
                            return true;
                        }
                        if (entryParts.length > 1 && SpookyBossCommand.parseDouble(entryParts[1], -1.0) < 1.0) {
                            sender.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                            return true;
                        }
                        loot.add(entry);
                        cfg.set("halloweenBoss.lootTable", loot);
                        plugin.saveConfig();
                        sender.sendMessage(Lang.get("command.boss.loot-added", "entry", entry));
                        break;
                    }
                    case "remove": {
                        if (args.length < 3) {
                            sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " loot remove <index>");
                            return true;
                        }
                        int idx = (int)SpookyBossCommand.parseDouble(args[2], 0.0) - 1;
                        if (idx < 0 || idx >= loot.size()) {
                            sender.sendMessage("\u00a7cInvalid index.");
                            return true;
                        }
                        String removed = (String)loot.remove(idx);
                        cfg.set("halloweenBoss.lootTable", loot);
                        plugin.saveConfig();
                        sender.sendMessage(Lang.get("command.boss.loot-removed", "entry", removed));
                        break;
                    }
                    default: {
                        sender.sendMessage("\u00a77Usage: \u00a7e/" + label + " loot <add|remove|list>");
                        break;
                    }
                }
                break;
            }
            default: {
                sender.sendMessage(Lang.get("command.boss.usage", "label", label));
            }
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "set" -> SET_KEYS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                case "armor" -> ARMOR_SLOTS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                case "ability" -> ABILITY_KEYS.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
                case "loot" -> LOOT_SUBS.stream().filter(s -> s.startsWith(args[1].toLowerCase())).toList();
                default -> List.of();
            };
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                return switch (args[1].toLowerCase()) {
                    case "enabled" -> List.of("true", "false");
                    case "health" -> List.of("50", "100", "200");
                    case "damage" -> List.of("4", "8", "12");
                    case "speed" -> List.of("0.25", "0.35", "0.5");
                    case "spawnchance" -> List.of("0.001", "0.005", "0.01", "0.05");
                    default -> List.of();
                };
            }
            if (args[0].equalsIgnoreCase("armor")) {
                List<String> mats = Arrays.stream(Material.values()).filter(Material::isItem).map(Enum::name).filter(s -> s.startsWith(args[2].toUpperCase())).limit(20L).toList();
                ArrayList<String> result = new ArrayList<String>(List.of("none"));
                result.addAll(mats);
                return result.stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
            if (args[0].equalsIgnoreCase("ability")) {
                return switch (args[1].toLowerCase()) {
                    case "witheronhit", "fireresistance", "charge" -> List.of("true", "false");
                    case "witherdurationticks" -> List.of("60", "100", "200");
                    case "chargeintervalseconds" -> List.of("10", "15", "20", "30");
                    default -> List.of();
                };
            }
            if (args[0].equalsIgnoreCase("loot") && args[1].equalsIgnoreCase("add")) {
                return Arrays.stream(Material.values()).filter(Material::isItem).map(m -> m.name() + ":1").filter(s -> s.startsWith(args[2].toUpperCase())).limit(20L).toList();
            }
        }
        return List.of();
    }

    private static double parseDouble(String s, double def) {
        try {
            double v = Double.parseDouble(s);
            // NaN besteht jeden Bereichsvergleich, Infinity ist als Attributwert ungültig.
            return Double.isFinite(v) ? v : def;
        }
        catch (Exception e) {
            return def;
        }
    }
}

