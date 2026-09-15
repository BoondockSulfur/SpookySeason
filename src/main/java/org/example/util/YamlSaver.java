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
 * Debounced, asynchronous writing of a YAML file.
 *
 * <p>On Folia, events and timers run on arbitrary region threads, where file I/O must not happen
 * inline. At most one async save is ever scheduled; if the plugin is already disabled (onDisable)
 * the write happens synchronously, because nothing can be scheduled by then.
 *
 * <p>The snapshot supplier has to return the serialised content consistently, synchronising for
 * itself — that snapshot is the only thing ever written.
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
     * Writes only if a change is pending — for shutdown, so an async save that has not run yet is
     * not lost, without creating unused files along the way.
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
