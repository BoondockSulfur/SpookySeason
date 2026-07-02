/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 */
package org.example.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.example.SpookySeason;

public class SpookyPlaceholders
extends PlaceholderExpansion {
    private final SpookySeason plugin;

    public SpookyPlaceholders(SpookySeason plugin) {
        this.plugin = plugin;
    }

    public String getIdentifier() {
        return "spooky";
    }

    public String getAuthor() {
        return "BoondockSulfur";
    }

    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase()) {
            case "active" -> {
                if (this.plugin.isSeasonActive()) {
                    yield "Yes";
                }
                yield "No";
            }
            case "treats" -> {
                if (player != null) {
                    yield String.valueOf(this.plugin.stats().getTreats(player.getUniqueId()));
                }
                yield "0";
            }
            case "optout" -> {
                if (player != null) {
                    if (this.plugin.prefs().isOptedOut(player.getUniqueId())) {
                        yield "Yes";
                    }
                    yield "No";
                }
                yield "No";
            }
            case "language" -> this.plugin.getConfig().getString("language", "en");
            default -> null;
        };
    }
}

