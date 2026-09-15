package org.example.cmd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.SpookySeason;
import org.bukkit.configuration.ConfigurationSection;
import org.example.feature.raid.RaidManager;
import org.example.feature.raid.ZoneTarget;
import org.example.lang.Lang;

/**
 * Raid control. Purely administrative — players experience the assault through announcements,
 * bossbars and titles, not through commands.
 */
public class SpookyRaidCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> SUBS = List.of("start", "stop", "status", "target", "zone");
    private static final List<String> TARGET_SUBS = List.of("info", "mode", "set", "add", "list", "remove", "clear", "region");
    private static final List<String> MODES = List.of("objective", "zone", "region");
    private static final List<String> ZONE_SUBS = List.of("pos1", "pos2", "save", "list", "remove");
    /** Corner selections per player, in memory only - a restart simply forgets them. */
    private static final Map<UUID, Location[]> SELECTION = new HashMap<UUID, Location[]>();

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spooky.admin")) {
            sender.sendMessage(Lang.get("command.spooky.no-permission", new String[0]));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Lang.get("command.raid.usage", "label", label));
            return true;
        }
        SpookySeason plugin = SpookySeason.get();
        RaidManager raid = plugin.raid();
        if (raid == null) {
            sender.sendMessage(Lang.get("raid.error.disabled", new String[0]));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start": {
                String error = raid.begin();
                sender.sendMessage(error == null ? Lang.get("command.raid.started", new String[0]) : Lang.get(error, new String[0]));
                break;
            }
            case "stop": {
                if (!raid.isRunning()) {
                    sender.sendMessage(Lang.get("command.raid.not-running", new String[0]));
                    break;
                }
                raid.abort(true);
                sender.sendMessage(Lang.get("command.raid.stopped", new String[0]));
                break;
            }
            case "status": {
                for (String line : raid.statusLines()) {
                    sender.sendMessage(line);
                }
                break;
            }
            case "target": {
                this.handleTarget(sender, label, args, plugin);
                break;
            }
            case "zone": {
                this.handleZone(sender, label, args, plugin);
                break;
            }
            default: {
                sender.sendMessage(Lang.get("command.unknown-arg", new String[0]));
            }
        }
        return true;
    }

    /** Zone setup: two corners, then save. Deliberately the same shape SiteZero uses for arenas. */
    private void handleZone(CommandSender sender, String label, String[] args, SpookySeason plugin) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
            return;
        }
        Player player = (Player)sender;
        if (args.length < 2) {
            sender.sendMessage(Lang.get("command.raid.zone-usage", "label", label));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "pos1":
            case "pos2": {
                boolean first = args[1].equalsIgnoreCase("pos1");
                Location[] corners = SELECTION.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
                corners[first ? 0 : 1] = player.getLocation();
                sender.sendMessage(Lang.get(first ? "command.raid.zone-pos1" : "command.raid.zone-pos2",
                        "x", String.valueOf(player.getLocation().getBlockX()),
                        "y", String.valueOf(player.getLocation().getBlockY()),
                        "z", String.valueOf(player.getLocation().getBlockZ())));
                break;
            }
            case "save": {
                if (args.length < 3) {
                    sender.sendMessage(Lang.get("command.raid.zone-save-usage", "label", label));
                    break;
                }
                Location[] corners = SELECTION.get(player.getUniqueId());
                if (corners == null || corners[0] == null || corners[1] == null) {
                    sender.sendMessage(Lang.get("command.raid.zone-need-corners", new String[0]));
                    break;
                }
                if (!corners[0].getWorld().equals(corners[1].getWorld())) {
                    sender.sendMessage(Lang.get("command.raid.zone-two-worlds", new String[0]));
                    break;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                plugin.getConfig().set("raid.target.zones." + name, ZoneTarget.format(corners[0], corners[1]));
                plugin.getConfig().set("raid.target.zone.name", name);
                plugin.getConfig().set("raid.target.mode", "zone");
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.zone-saved", "zone", name));
                break;
            }
            case "list": {
                ConfigurationSection zones = plugin.getConfig().getConfigurationSection("raid.target.zones");
                if (zones == null || zones.getKeys(false).isEmpty()) {
                    sender.sendMessage(Lang.get("command.raid.zone-empty", new String[0]));
                    break;
                }
                sender.sendMessage(Lang.get("command.raid.zone-list-header", new String[0]));
                for (String key : zones.getKeys(false)) {
                    sender.sendMessage(Lang.get("command.raid.zone-list-entry",
                            "zone", key, "entry", zones.getString(key, "-")));
                }
                break;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage(Lang.get("command.raid.zone-remove-usage", "label", label));
                    break;
                }
                String name = args[2].toLowerCase(Locale.ROOT);
                if (plugin.getConfig().getString("raid.target.zones." + name) == null) {
                    sender.sendMessage(Lang.get("command.raid.zone-unknown", "zone", name));
                    break;
                }
                plugin.getConfig().set("raid.target.zones." + name, null);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.zone-removed", "zone", name));
                break;
            }
            default: {
                sender.sendMessage(Lang.get("command.raid.zone-usage", "label", label));
            }
        }
    }

    private void handleTarget(CommandSender sender, String label, String[] args, SpookySeason plugin) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("command.raid.target-usage", "label", label));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "info": {
                String mode = plugin.getConfig().getString("raid.target.mode", "objective");
                sender.sendMessage(Lang.get("command.raid.target-mode", "mode", mode));
                sender.sendMessage(Lang.get("command.raid.target-objective",
                        "world", SpookyRaidCommand.orDash(plugin.getConfig().getString("raid.target.objective.world", "")),
                        "x", String.valueOf((long)Math.floor(plugin.getConfig().getDouble("raid.target.objective.x", 0.0))),
                        "y", String.valueOf((long)Math.floor(plugin.getConfig().getDouble("raid.target.objective.y", 0.0))),
                        "z", String.valueOf((long)Math.floor(plugin.getConfig().getDouble("raid.target.objective.z", 0.0))),
                        "health", String.valueOf(Math.round(plugin.getConfig().getDouble("raid.target.objective.health", 0.0)))));
                List<String> configured = plugin.getConfig().getStringList("raid.target.objective.points");
                if (configured.isEmpty()) {
                    sender.sendMessage(Lang.get("command.raid.target-list-empty", new String[0]));
                } else {
                    sender.sendMessage(Lang.get("command.raid.target-list-header", new String[0]));
                    int i = 1;
                    for (String entry : configured) {
                        sender.sendMessage(Lang.get("command.raid.target-list-entry",
                                "index", String.valueOf(i++), "entry", entry));
                    }
                }
                ConfigurationSection allZones = plugin.getConfig().getConfigurationSection("raid.target.zones");
                String activeZone = plugin.getConfig().getString("raid.target.zone.name", "");
                sender.sendMessage(Lang.get("command.raid.target-zone",
                        "zone", SpookyRaidCommand.orDash(activeZone),
                        "entry", allZones == null || activeZone.isEmpty()
                                ? "-" : allZones.getString(activeZone, "-"),
                        "limit", String.valueOf(plugin.getConfig().getInt("raid.target.zone.breachLimit", 0))));
                sender.sendMessage(Lang.get("command.raid.target-region",
                        "world", SpookyRaidCommand.orDash(plugin.getConfig().getString("raid.target.region.world", "")),
                        "region", SpookyRaidCommand.orDash(plugin.getConfig().getString("raid.target.region.name", "")),
                        "limit", String.valueOf(plugin.getConfig().getInt("raid.target.region.breachLimit", 0))));
                break;
            }
            case "mode": {
                if (args.length < 3 || !MODES.contains(args[2].toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(Lang.get("command.raid.mode-usage", "label", label));
                    break;
                }
                String mode = args[2].toLowerCase(Locale.ROOT);
                plugin.getConfig().set("raid.target.mode", mode);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.mode-set", "mode", mode));
                break;
            }
            case "set": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    break;
                }
                Location loc = ((Player)sender).getLocation();
                plugin.getConfig().set("raid.target.objective.world", loc.getWorld().getName());
                plugin.getConfig().set("raid.target.objective.x", Math.floor(loc.getX()) + 0.5);
                plugin.getConfig().set("raid.target.objective.y", (double)loc.getBlockY());
                plugin.getConfig().set("raid.target.objective.z", Math.floor(loc.getZ()) + 0.5);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.target-set",
                        "world", loc.getWorld().getName(),
                        "x", String.valueOf(loc.getBlockX()),
                        "y", String.valueOf(loc.getBlockY()),
                        "z", String.valueOf(loc.getBlockZ())));
                break;
            }
            case "add": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    break;
                }
                Location loc = ((Player)sender).getLocation();
                List<String> points = new ArrayList<String>(plugin.getConfig().getStringList("raid.target.objective.points"));
                points.add(SpookyRaidCommand.formatPoint(loc));
                plugin.getConfig().set("raid.target.objective.points", points);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.target-added",
                        "index", String.valueOf(points.size()),
                        "world", loc.getWorld().getName(),
                        "x", String.valueOf(loc.getBlockX()),
                        "y", String.valueOf(loc.getBlockY()),
                        "z", String.valueOf(loc.getBlockZ())));
                break;
            }
            case "list": {
                List<String> points = plugin.getConfig().getStringList("raid.target.objective.points");
                if (points.isEmpty()) {
                    sender.sendMessage(Lang.get("command.raid.target-list-empty", new String[0]));
                    break;
                }
                sender.sendMessage(Lang.get("command.raid.target-list-header", new String[0]));
                int index = 1;
                for (String entry : points) {
                    sender.sendMessage(Lang.get("command.raid.target-list-entry",
                            "index", String.valueOf(index++), "entry", entry));
                }
                break;
            }
            case "remove": {
                List<String> points = new ArrayList<String>(plugin.getConfig().getStringList("raid.target.objective.points"));
                int index = args.length < 3 ? -1 : SpookyRaidCommand.parseIndex(args[2]);
                if (args.length < 3) {
                    sender.sendMessage(Lang.get("command.raid.remove-usage", "label", label));
                    break;
                }
                if (index < 1 || index > points.size()) {
                    sender.sendMessage(Lang.get("command.raid.invalid-index", new String[0]));
                    break;
                }
                points.remove(index - 1);
                plugin.getConfig().set("raid.target.objective.points", points);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.target-removed", "index", String.valueOf(index)));
                break;
            }
            case "clear": {
                plugin.getConfig().set("raid.target.objective.points", new ArrayList<String>());
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.target-cleared", new String[0]));
                break;
            }
            case "region": {
                if (args.length < 3) {
                    sender.sendMessage(Lang.get("command.raid.region-usage", "label", label));
                    break;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
                    break;
                }
                Player player = (Player)sender;
                String region = args[2];
                if (!plugin.regions().hasRegionLookup()) {
                    sender.sendMessage(Lang.get("raid.error.no-worldguard", new String[0]));
                    break;
                }
                if (!plugin.regions().regionExists(player.getWorld(), region)) {
                    sender.sendMessage(Lang.get("raid.error.no-region", new String[0]));
                    break;
                }
                plugin.getConfig().set("raid.target.region.world", player.getWorld().getName());
                plugin.getConfig().set("raid.target.region.name", region);
                plugin.saveConfig();
                sender.sendMessage(Lang.get("command.raid.region-set",
                        "region", region, "world", player.getWorld().getName()));
                break;
            }
            default: {
                sender.sendMessage(Lang.get("command.raid.target-usage", "label", label));
            }
        }
    }

    /** Points-list format: world,x,y,z — deliberately the same thing the config accepts. */
    private static String formatPoint(Location loc) {
        return loc.getWorld().getName() + ","
                + (Math.floor(loc.getX()) + 0.5) + ","
                + loc.getBlockY() + ","
                + (Math.floor(loc.getZ()) + 0.5);
    }

    private static int parseIndex(String value) {
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Render empty config values as "-" rather than as a hole in the line. */
    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("spooky.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("target")) {
            return TARGET_SUBS.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("target") && args[1].equalsIgnoreCase("mode")) {
            return MODES.stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("zone")) {
            return ZONE_SUBS.stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("zone") && args[1].equalsIgnoreCase("remove")) {
            ConfigurationSection zones = SpookySeason.get().getConfig().getConfigurationSection("raid.target.zones");
            if (zones != null) {
                return zones.getKeys(false).stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            }
        }
        return List.of();
    }
}
