package org.example.feature.raid;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.example.api.RaidResult;
import org.example.lang.Lang;

/** Chat rendering of a raid result, for {@code /spookyraid stats} and the end-of-raid summary. */
public final class RaidSummary {

    private static final DateTimeFormatter STATS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private RaidSummary() {
    }

    /**
     * The result as chat lines: outcome, targets and the defenders ranked by kills, then damage.
     *
     * @param top how many defenders to list; 0 lists all of them
     */
    public static List<String> lines(RaidResult result, int top) {
        ArrayList<String> lines = new ArrayList<String>();
        if (result == null) {
            lines.add(Lang.get("command.raid.stats-none", new String[0]));
            return lines;
        }
        lines.add(Lang.get("command.raid.stats-header", new String[0]));
        lines.add(Lang.get("command.raid.stats-summary",
                "time", STATS_TIME.format(Instant.ofEpochMilli(result.endedAt()).atZone(ZoneId.systemDefault())),
                "outcome", Lang.get("command.raid.stats-outcome-" + result.outcome().key(), new String[0]),
                "wave", String.valueOf(result.waveReached()),
                "total", String.valueOf(result.wavesTotal()),
                "minutes", String.valueOf(Math.max(0L, (result.endedAt() - result.startedAt()) / 60000L))));
        for (RaidResult.TargetResult target : result.targets()) {
            String state = target.health() != null
                    ? Lang.get("command.raid.stats-target-health",
                            "health", String.valueOf((long)Math.ceil(target.health())),
                            "max", String.valueOf(Math.round(target.maxHealth() == null ? 0.0 : target.maxHealth())))
                    : Lang.get("command.raid.stats-target-breaches",
                            "breaches", String.valueOf(target.breaches()),
                            "limit", String.valueOf(target.breachLimit()));
            lines.add(Lang.get(target.fallen() ? "command.raid.stats-target-fallen" : "command.raid.stats-target",
                    "name", target.name(), "state", state));
        }
        if (result.players().isEmpty()) {
            lines.add(Lang.get("command.raid.stats-no-players", new String[0]));
            return lines;
        }
        int rank = 1;
        for (RaidResult.PlayerResult player : result.players()) {
            if (top > 0 && rank > top) break;
            lines.add(Lang.get("command.raid.stats-player",
                    "rank", String.valueOf(rank++),
                    "name", player.name(),
                    "kills", String.valueOf(player.kills()),
                    "leader", String.valueOf(player.leaderKills()),
                    "defense", String.valueOf(player.defenseKills()),
                    "damage", String.valueOf(Math.round(player.damage())),
                    "deaths", String.valueOf(player.deaths())));
        }
        return lines;
    }
}
