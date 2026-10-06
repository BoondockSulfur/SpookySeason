package org.example.api;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired once when a raid starts, at the beginning of its countdown. Synchronous: on Paper on the
 * main thread, on Folia on the global region thread. Every start is followed by exactly one
 * {@link RaidEndEvent} with the same {@link #raidId()}, unless the server shuts down mid-raid.
 */
public class RaidStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID raidId;
    private final long startedAt;
    private final String world;
    private final String targetMode;
    private final int wavesTotal;

    public RaidStartEvent(UUID raidId, long startedAt, String world, String targetMode, int wavesTotal) {
        super(false);
        this.raidId = raidId;
        this.startedAt = startedAt;
        this.world = world;
        this.targetMode = targetMode;
        this.wavesTotal = wavesTotal;
    }

    public UUID raidId() {
        return this.raidId;
    }

    /** Epoch milliseconds. */
    public long startedAt() {
        return this.startedAt;
    }

    public String world() {
        return this.world;
    }

    /** {@code objective}, {@code zone} or {@code region}. */
    public String targetMode() {
        return this.targetMode;
    }

    public int wavesTotal() {
        return this.wavesTotal;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
