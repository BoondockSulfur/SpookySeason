/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.Plugin
 */
package org.example.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
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
}

