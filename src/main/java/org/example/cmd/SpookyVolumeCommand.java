/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package org.example.cmd;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.example.SpookySeason;
import org.example.lang.Lang;
import org.example.prefs.PlayerPrefs;

public class SpookyVolumeCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> SUBS = List.of("ambient", "ghost", "rain", "reset");

    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Lang.get("command.volume.only-ingame", new String[0]));
            return true;
        }
        Player p = (Player)sender;
        if (args.length == 0) {
            p.sendMessage(Lang.get("command.volume.usage", "label", lbl));
            return true;
        }
        PlayerPrefs prefs = SpookySeason.get().prefs();
        switch (args[0].toLowerCase()) {
            case "reset": {
                prefs.reset(p.getUniqueId());
                p.sendMessage(Lang.get("command.volume.reset", new String[0]));
                break;
            }
            case "ambient": 
            case "ghost": 
            case "rain": {
                double v;
                if (args.length < 2) {
                    p.sendMessage(Lang.get("command.volume.need-value", new String[0]));
                    return true;
                }
                try {
                    v = Double.parseDouble(args[1]);
                }
                catch (Exception e) {
                    p.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                    return true;
                }
                // "NaN" is valid input for parseDouble and survives any clamping (Math.max/min
                // pass NaN straight through) — without this check NaN ends up in player-prefs.yml
                // and in playSound().
                if (!Double.isFinite(v)) {
                    p.sendMessage(Lang.get("command.volume.invalid-number", new String[0]));
                    return true;
                }
                v = Math.max(0.0, Math.min(1.0, v));
                switch (args[0].toLowerCase()) {
                    case "ambient": {
                        prefs.setAmbientVol(p.getUniqueId(), v);
                        break;
                    }
                    case "ghost": {
                        prefs.setGhostVol(p.getUniqueId(), v);
                        break;
                    }
                    case "rain": {
                        prefs.setRainVol(p.getUniqueId(), v);
                    }
                }
                p.sendMessage(Lang.get("command.volume.set", "key", args[0].toLowerCase(), "value", String.valueOf(v)));
                break;
            }
            default: {
                p.sendMessage(Lang.get("command.volume.usage", "label", lbl));
            }
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}

