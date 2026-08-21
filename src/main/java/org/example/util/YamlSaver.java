package org.example.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

/**
 * Entprelltes, asynchrones Schreiben einer YAML-Datei.
 *
 * Auf Folia laufen Events und Timer auf beliebigen Region-Threads — Datei-I/O darf dort nicht
 * inline passieren. Es ist immer höchstens ein Async-Save geplant; ist das Plugin bereits
 * deaktiviert (onDisable), wird synchron geschrieben, weil dann nichts mehr geplant werden kann.
 *
 * Der Snapshot-Supplier muss den serialisierten Inhalt konsistent liefern (also selbst
 * synchronisieren) — geschrieben wird ausschließlich dieser Snapshot.
 */
public final class YamlSaver {
    private final Plugin plugin;
    private final File file;
    private final Supplier<String> snapshot;
    private final Object ioLock = new Object();
    private final AtomicBoolean savePending = new AtomicBoolean();

    public YamlSaver(Plugin plugin, File file, Supplier<String> snapshot) {
        this.plugin = plugin;
        this.file = file;
        this.snapshot = snapshot;
    }

    public void save() {
        if (!this.plugin.isEnabled()) {
            this.flush();
            return;
        }
        if (this.savePending.compareAndSet(false, true)) {
            Scheduler.runAsync(this.plugin, () -> {
                this.savePending.set(false);
                this.flush();
            });
        }
    }

    /**
     * Schreibt nur, wenn eine Änderung ansteht — für das Herunterfahren, damit ein noch nicht
     * ausgeführter Async-Save nicht verloren geht, ohne unbenutzte Dateien anzulegen.
     */
    public void flushIfPending() {
        if (this.savePending.compareAndSet(true, false)) {
            this.flush();
        }
    }

    public void flush() {
        String data = this.snapshot.get();
        synchronized (this.ioLock) {
            try {
                File parent = this.file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                Files.writeString(this.file.toPath(), data, StandardCharsets.UTF_8);
            }
            catch (IOException e) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save " + this.file.getName(), e);
            }
        }
    }
}
