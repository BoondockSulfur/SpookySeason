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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private static final String MODRINTH_VERSION_URL = "https://modrinth.com/project/spookyseason/version/";
    private static final String CURSEFORGE_URL = "https://www.curseforge.com/minecraft/bukkit-plugins/spookyseason/files";
    private final Plugin plugin;
    // Written on the async thread, read in the join event — volatile for visibility.
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
                String remoteVersion = latest.get("version_number").getAsString();
                String currentVersion = this.plugin.getDescription().getVersion();
                if (UpdateChecker.isNewer(remoteVersion, currentVersion)) {
                    this.latestVersion = remoteVersion;
                    this.downloadUrl = MODRINTH_VERSION_URL + remoteVersion;
                    this.updateAvailable = true;
                    this.plugin.getLogger().info("New version available: " + remoteVersion + " (current: " + currentVersion + ")");
                }
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
        }
    }

    /**
     * Compares the numeric blocks in order. A plain inequality test would report an "update" even
     * when the newest thing on Modrinth is actually an older version.
     */
    static boolean isNewer(String remote, String local) {
        int[] r = UpdateChecker.parseVersion(remote);
        int[] l = UpdateChecker.parseVersion(local);
        int len = Math.max(r.length, l.length);
        for (int i = 0; i < len; ++i) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv != lv) {
                return rv > lv;
            }
        }
        return false;
    }

    /** "v1.2.3-beta.2" becomes [1, 2, 3]; everything from the first suffix on is discarded. */
    private static int[] parseVersion(String version) {
        if (version == null) {
            return new int[0];
        }
        String cleaned = version.trim();
        if (cleaned.startsWith("v") || cleaned.startsWith("V")) {
            cleaned = cleaned.substring(1);
        }
        int cut = cleaned.length();
        for (int i = 0; i < cleaned.length(); ++i) {
            char c = cleaned.charAt(i);
            if (Character.isDigit(c) || c == '.') continue;
            cut = i;
            break;
        }
        String[] parts = cleaned.substring(0, cut).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; ++i) {
            try {
                out[i] = parts[i].isEmpty() ? 0 : Integer.parseInt(parts[i]);
            }
            catch (NumberFormatException ex) {
                out[i] = 0;
            }
        }
        return out;
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
                p.sendMessage(UpdateChecker.legacy(Lang.get("update.available", "version", this.latestVersion)));
                p.sendMessage(UpdateChecker.downloadLine(this.downloadUrl));
            }
        }, 40L);
    }

    /**
     * The download line as clickable components rather than a typed-out URL, and both stores, so
     * the notice does not tie anyone to a single source.
     */
    private static Component downloadLine(String modrinthUrl) {
        return UpdateChecker.legacy(Lang.get("update.download-prefix", new String[0]))
                .append(UpdateChecker.link(Lang.get("update.modrinth", new String[0]), modrinthUrl))
                .append(Component.text(" "))
                .append(UpdateChecker.link(Lang.get("update.curseforge", new String[0]), CURSEFORGE_URL));
    }

    private static Component link(String label, String url) {
        return UpdateChecker.legacy(label)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url)));
    }

    /** The language files use section-sign codes; those have to be translated for components. */
    private static Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}

