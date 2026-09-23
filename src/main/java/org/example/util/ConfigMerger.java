package org.example.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ConfigMerger {
    private ConfigMerger() {
    }

    public static YamlConfiguration merge(Plugin plugin, String resourcePath, File diskFile) {
        YamlConfiguration userCfg = YamlConfiguration.loadConfiguration((File)diskFile);
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream == null) {
            return userCfg;
        }
        YamlConfiguration defaultCfg = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : defaultCfg.getKeys(true)) {
            if (defaultCfg.isConfigurationSection(key) || userCfg.contains(key, true)) continue;
            userCfg.set(key, defaultCfg.get(key));
            changed = true;
        }
        if (changed) {
            try {
                userCfg.save(diskFile);
                plugin.getLogger().info("Updated " + diskFile.getName() + " with new default keys.");
            }
            catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save merged " + diskFile.getName(), e);
            }
        }
        return userCfg;
    }

    /**
     * Removes configuration paths the plugin no longer reads. Runs on every start and is
     * idempotent: merging only ever adds keys, so a removed feature would otherwise leave its
     * block in the file indefinitely.
     */
    public static void removeObsolete(Plugin plugin, File diskFile, List<String> paths) {
        YamlConfiguration userCfg = YamlConfiguration.loadConfiguration((File)diskFile);
        boolean changed = false;
        for (String path : paths) {
            if (!userCfg.isSet(path)) continue;
            userCfg.set(path, null);
            changed = true;
            plugin.getLogger().info("Removed obsolete config key '" + path + "'.");
        }
        if (!changed) {
            return;
        }
        try {
            userCfg.save(diskFile);
        }
        catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save " + diskFile.getName(), e);
        }
    }
}

