package org.example.feature.raid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;
import org.example.api.RaidResult;

/**
 * Raid results on disk: one JSON file per raid in {@code raid-results/}, named
 * {@code <endedAtMillis>-<raidId>.json}. Every method does blocking file I/O and must be called
 * off the server threads.
 */
final class RaidResultStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private final Plugin plugin;
    private final File directory;

    RaidResultStore(Plugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "raid-results");
    }

    /** Writes the result; through a temporary file so a reader never sees half a file. */
    void write(RaidResult result) {
        try {
            Files.createDirectories(this.directory.toPath());
            String name = result.endedAt() + "-" + result.raidId() + ".json";
            Path target = this.directory.toPath().resolve(name);
            Path temp = this.directory.toPath().resolve(name + ".tmp");
            Files.writeString(temp, GSON.toJson(result.toMap()), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException | RuntimeException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not write raid result " + result.raidId(), e);
        }
    }

    /** The most recent stored result, or null if there is none or it cannot be read. */
    RaidResult loadLatest() {
        File[] files = this.directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return null;
        }
        File latest = null;
        long latestEnded = Long.MIN_VALUE;
        for (File file : files) {
            long ended = RaidResultStore.endedAt(file.getName());
            if (ended <= latestEnded) continue;
            latestEnded = ended;
            latest = file;
        }
        if (latest == null) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(latest.toPath(), StandardCharsets.UTF_8)) {
            Map<?, ?> map = GSON.fromJson(reader, Map.class);
            return map == null ? null : RaidResult.fromMap(map);
        }
        catch (IOException | RuntimeException e) {
            this.plugin.getLogger().log(Level.WARNING, "Could not read raid result " + latest.getName(), e);
            return null;
        }
    }

    private static long endedAt(String fileName) {
        int dash = fileName.indexOf('-');
        if (dash <= 0) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(fileName.substring(0, dash));
        }
        catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }
}
