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
        return ConfigMerger.merge(plugin, resourcePath, diskFile, List.of());
    }

    /**
     * Adds missing default keys to the file on disk.
     *
     * @param freeForm sections the admin shapes freely (reward ranks, archetype names, ...). Their
     *                 defaults only serve as an example: they are added only together with their
     *                 parent section, never entry by entry, so deliberately deleted entries stay
     *                 deleted — all of them included.
     */
    public static YamlConfiguration merge(Plugin plugin, String resourcePath, File diskFile, List<String> freeForm) {
        YamlConfiguration userCfg = YamlConfiguration.loadConfiguration((File)diskFile);
        InputStream defaultStream = plugin.getResource(resourcePath);
        if (defaultStream == null) {
            return userCfg;
        }
        YamlConfiguration defaultCfg = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
        boolean changed = false;
        for (String key : defaultCfg.getKeys(true)) {
            if (defaultCfg.isConfigurationSection(key) || userCfg.contains(key, true)) continue;
            if (ConfigMerger.insideExistingFreeForm(userCfg, key, freeForm)) continue;
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
     * A free-form section counts as present once the section or its parent exists. The parent
     * matters because deleting every entry leaves a bare {@code ranks:} behind, which YAML reads
     * as no value at all — the section would otherwise come back in full.
     */
    private static boolean insideExistingFreeForm(YamlConfiguration userCfg, String key, List<String> freeForm) {
        for (String section : freeForm) {
            if (!key.startsWith(section + ".")) continue;
            int dot = section.lastIndexOf('.');
            String parent = dot < 0 ? null : section.substring(0, dot);
            if (userCfg.contains(section, true) || (parent != null && userCfg.contains(parent, true))) {
                return true;
            }
        }
        return false;
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

