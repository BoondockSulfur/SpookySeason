/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 */
package org.example.cmd;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.example.feature.PumpkinRainManager;
import org.example.lang.Lang;

public class PumpkinRainCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> SUBS = List.of("start", "stop");
    private final PumpkinRainManager mgr;

    public PumpkinRainCommand(PumpkinRainManager mgr) {
        this.mgr = mgr;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("command.pumpkinrain.usage", "label", label));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start": {
                this.mgr.start(true);
                sender.sendMessage(Lang.get("command.pumpkinrain.started", new String[0]));
                break;
            }
            case "stop": {
                this.mgr.stop();
                sender.sendMessage(Lang.get("command.pumpkinrain.stopped", new String[0]));
                break;
            }
            default: {
                sender.sendMessage(Lang.get("command.unknown-arg", new String[0]));
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

