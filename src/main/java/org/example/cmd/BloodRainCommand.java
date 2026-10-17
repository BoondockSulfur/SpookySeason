package org.example.cmd;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.example.feature.BloodRainManager;
import org.example.lang.Lang;

public class BloodRainCommand
implements CommandExecutor,
TabCompleter {
    private static final List<String> SUBS = List.of("start", "stop");
    private final BloodRainManager mgr;

    public BloodRainCommand(BloodRainManager mgr) {
        this.mgr = mgr;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("command.bloodrain.usage", "label", label));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start": {
                this.mgr.start(true);
                sender.sendMessage(Lang.get("command.bloodrain.started", new String[0]));
                break;
            }
            case "stop": {
                this.mgr.stop();
                sender.sendMessage(Lang.get("command.bloodrain.stopped", new String[0]));
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
            return SUBS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}

