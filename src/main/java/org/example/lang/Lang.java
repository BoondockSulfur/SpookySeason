/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.plugin.Plugin
 */
package org.example.lang;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.example.util.ConfigMerger;

public final class Lang {
    private static YamlConfiguration messages;
    private static YamlConfiguration fallback;

    private Lang() {
    }

    public static void load(Plugin plugin) {
        String langCode = plugin.getConfig().getString("language", "en");
        File langDir = new File(plugin.getDataFolder(), "lang");
        // saveResource(false) logs a warning every time the file already exists — on
        // /spooky reload that would be pure log spam.
        if (!new File(langDir, "en.yml").exists()) {
            plugin.saveResource("lang/en.yml", false);
        }
        if (!new File(langDir, "de.yml").exists()) {
            plugin.saveResource("lang/de.yml", false);
        }
        Lang.mergeIfBundled(plugin, "lang/en.yml", new File(langDir, "en.yml"));
        Lang.mergeIfBundled(plugin, "lang/de.yml", new File(langDir, "de.yml"));
        File langFile = new File(langDir, langCode + ".yml");
        if (langFile.exists()) {
            messages = YamlConfiguration.loadConfiguration((File)langFile);
        } else {
            plugin.getLogger().warning("Language file not found: " + langCode + ".yml \u2013 falling back to English.");
            messages = YamlConfiguration.loadConfiguration((File)new File(langDir, "en.yml"));
        }
        InputStream stream = plugin.getResource("lang/en.yml");
        if (stream != null) {
            fallback = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    public static String get(String key, String ... replacements) {
        if (messages == null) {
            return key;
        }
        String msg = messages.getString(key);
        if (msg == null && fallback != null) {
            msg = fallback.getString(key);
        }
        if (msg == null) {
            return key;
        }
        if (replacements != null && replacements.length >= 2) {
            for (int i = 0; i < replacements.length - 1; i += 2) {
                msg = msg.replace("{" + replacements[i] + "}", replacements[i + 1]);
            }
        }
        return msg;
    }

    private static void mergeIfBundled(Plugin plugin, String resourcePath, File diskFile) {
        if (diskFile.exists() && plugin.getResource(resourcePath) != null) {
            ConfigMerger.merge(plugin, resourcePath, diskFile);
        }
    }
}

