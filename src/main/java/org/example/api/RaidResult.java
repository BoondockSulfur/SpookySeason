package org.example.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Outcome of one finished raid: how it ended, the state of every target and what each defender
 * did. Immutable.
 *
 * <p>{@link #toMap()} is the stable exchange format (schema {@value #SCHEMA}). It is written to
 * {@code raid-results/} as JSON and handed out through {@link RaidEndEvent}; it contains only
 * plain Java types so other plugins can read it without a compile-time dependency.
 */
public final class RaidResult {

    public static final int SCHEMA = 1;
    public static final String SOURCE = "spookyseason";

    public enum Outcome {
        VICTORY,
        DEFEAT,
        ABORTED;

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public static Outcome fromKey(String key) {
            for (Outcome outcome : Outcome.values()) {
                if (outcome.key().equalsIgnoreCase(key)) {
                    return outcome;
                }
            }
            return ABORTED;
        }
    }

    /**
     * One target. Objectives carry {@code health}/{@code maxHealth}, zones and regions carry
     * {@code breaches}/{@code breachLimit}; the other pair is null.
     */
    public record TargetResult(String name, boolean fallen, Double health, Double maxHealth,
                               Integer breaches, Integer breachLimit) {

        public static TargetResult objective(String name, boolean fallen, double health, double maxHealth) {
            return new TargetResult(name, fallen, RaidResult.round(health), RaidResult.round(maxHealth), null, null);
        }

        public static TargetResult breachable(String name, boolean fallen, int breaches, int breachLimit) {
            return new TargetResult(name, fallen, null, null, breaches, breachLimit);
        }

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("name", this.name);
            map.put("fallen", this.fallen);
            map.put("health", this.health);
            map.put("maxHealth", this.maxHealth);
            map.put("breaches", this.breaches);
            map.put("breachLimit", this.breachLimit);
            return map;
        }
    }

    /** What one defender did during the raid. */
    public record PlayerResult(UUID uuid, String name, int kills, int leaderKills, double damage,
                               int defenseKills, int deaths) {

        public Map<String, Object> toMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("uuid", this.uuid.toString());
            map.put("name", this.name);
            map.put("kills", this.kills);
            map.put("leaderKills", this.leaderKills);
            map.put("damage", RaidResult.round(this.damage));
            map.put("defenseKills", this.defenseKills);
            map.put("deaths", this.deaths);
            return map;
        }
    }

    private final UUID raidId;
    private final long startedAt;
    private final long endedAt;
    private final String world;
    private final Outcome outcome;
    private final int wavesTotal;
    private final int waveReached;
    private final String targetMode;
    private final List<TargetResult> targets;
    private final List<PlayerResult> players;

    public RaidResult(UUID raidId, long startedAt, long endedAt, String world, Outcome outcome,
                      int wavesTotal, int waveReached, String targetMode,
                      List<TargetResult> targets, List<PlayerResult> players) {
        this.raidId = raidId;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.world = world;
        this.outcome = outcome;
        this.wavesTotal = wavesTotal;
        this.waveReached = waveReached;
        this.targetMode = targetMode;
        this.targets = List.copyOf(targets);
        this.players = List.copyOf(players);
    }

    public UUID raidId() {
        return this.raidId;
    }

    public long startedAt() {
        return this.startedAt;
    }

    public long endedAt() {
        return this.endedAt;
    }

    public String world() {
        return this.world;
    }

    public Outcome outcome() {
        return this.outcome;
    }

    public int wavesTotal() {
        return this.wavesTotal;
    }

    public int waveReached() {
        return this.waveReached;
    }

    public String targetMode() {
        return this.targetMode;
    }

    public List<TargetResult> targets() {
        return this.targets;
    }

    public List<PlayerResult> players() {
        return this.players;
    }

    /** The exchange format as plain Java types (Map, List, String, Number, Boolean, null). */
    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("schema", SCHEMA);
        map.put("source", SOURCE);
        map.put("raidId", this.raidId.toString());
        map.put("startedAt", this.startedAt);
        map.put("endedAt", this.endedAt);
        map.put("world", this.world);
        map.put("outcome", this.outcome.key());
        map.put("wavesTotal", this.wavesTotal);
        map.put("waveReached", this.waveReached);
        map.put("targetMode", this.targetMode);
        ArrayList<Object> targetList = new ArrayList<Object>();
        for (TargetResult target : this.targets) {
            targetList.add(target.toMap());
        }
        map.put("targets", targetList);
        ArrayList<Object> playerList = new ArrayList<Object>();
        for (PlayerResult player : this.players) {
            playerList.add(player.toMap());
        }
        map.put("players", playerList);
        return Collections.unmodifiableMap(map);
    }

    /**
     * Reads the exchange format back, for instance from a stored JSON file. Numbers may arrive
     * as any {@link Number} subtype. Returns null if required fields are missing.
     */
    public static RaidResult fromMap(Map<?, ?> map) {
        try {
            UUID raidId = UUID.fromString(String.valueOf(map.get("raidId")));
            ArrayList<TargetResult> targets = new ArrayList<TargetResult>();
            if (map.get("targets") instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> t)) continue;
                    targets.add(new TargetResult(
                            String.valueOf(t.get("name")),
                            Boolean.TRUE.equals(t.get("fallen")),
                            RaidResult.optDouble(t.get("health")),
                            RaidResult.optDouble(t.get("maxHealth")),
                            RaidResult.optInt(t.get("breaches")),
                            RaidResult.optInt(t.get("breachLimit"))));
                }
            }
            ArrayList<PlayerResult> players = new ArrayList<PlayerResult>();
            if (map.get("players") instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof Map<?, ?> p)) continue;
                    players.add(new PlayerResult(
                            UUID.fromString(String.valueOf(p.get("uuid"))),
                            String.valueOf(p.get("name")),
                            RaidResult.intOf(p.get("kills")),
                            RaidResult.intOf(p.get("leaderKills")),
                            RaidResult.doubleOf(p.get("damage")),
                            RaidResult.intOf(p.get("defenseKills")),
                            RaidResult.intOf(p.get("deaths"))));
                }
            }
            return new RaidResult(raidId,
                    RaidResult.longOf(map.get("startedAt")),
                    RaidResult.longOf(map.get("endedAt")),
                    String.valueOf(map.get("world")),
                    Outcome.fromKey(String.valueOf(map.get("outcome"))),
                    RaidResult.intOf(map.get("wavesTotal")),
                    RaidResult.intOf(map.get("waveReached")),
                    String.valueOf(map.get("targetMode")),
                    targets, players);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }

    static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static Double optDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Integer optInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static int intOf(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static long longOf(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static double doubleOf(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }
}
