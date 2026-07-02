/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.plugin.Plugin
 */
package org.example.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.example.lang.Lang;
import org.example.util.Scheduler;

public class UpdateChecker
implements Listener {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/spookyseason/version?limit=1";
    private final Plugin plugin;
    // Im Async-Thread geschrieben, im Join-Event gelesen — volatile für die Sichtbarkeit.
    private volatile String latestVersion;
    private volatile String downloadUrl;
    private volatile boolean updateAvailable;

    public UpdateChecker(Plugin plugin) {
        this.plugin = plugin;
    }

    public void checkAsync() {
        Scheduler.runAsync(this.plugin, this::check);
    }

    private void check() {
        try {
            JsonArray versions;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(MODRINTH_API)).timeout(Duration.ofSeconds(15L)).header("User-Agent", "SpookySeason/" + this.plugin.getDescription().getVersion()).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && !(versions = JsonParser.parseString((String)response.body()).getAsJsonArray()).isEmpty()) {
                JsonObject latest = versions.get(0).getAsJsonObject();
                this.latestVersion = latest.get("version_number").getAsString();
                String currentVersion = this.plugin.getDescription().getVersion();
                if (!this.latestVersion.equals(currentVersion)) {
                    this.downloadUrl = "https://modrinth.com/project/spookyseason/version/" + this.latestVersion;
                    this.updateAvailable = true;
                    this.plugin.getLogger().info("New version available: " + this.latestVersion + " (current: " + currentVersion + ")");
                }
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!this.updateAvailable) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.hasPermission("spooky.admin")) {
            return;
        }
        Scheduler.runLater(this.plugin, () -> {
            if (p.isOnline()) {
                p.sendMessage(Lang.get("update.available", "version", this.latestVersion));
                p.sendMessage(Lang.get("update.download", "url", this.downloadUrl));
            }
        }, 40L);
    }
}

