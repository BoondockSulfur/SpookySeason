package org.example.feature.raid;

import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.example.api.RaidResult;

/**
 * The thing a raid is fought over.
 *
 * <p>Three flavours: one or more objects with their own health pool ({@link ObjectiveTarget}),
 * a configured box ({@link ZoneTarget}), or a WorldGuard region ({@link RegionTarget}); the latter
 * two count attackers that get inside.
 *
 * <p><b>Folia:</b> {@link #prepare()} and {@link #tick()} run on the global scheduler and must
 * therefore never touch blocks or entities directly — the implementations go through the region
 * or entity scheduler for that. {@link #inspectAttacker}, {@link #navTarget} and
 * {@link #attackTarget} are only ever called from the region thread of the attacker in question
 * and may read its location.
 */
public interface RaidTarget {

    /**
     * Prepares the target. {@code false} means the raid cannot start at all (missing world,
     * missing region, WorldGuard unavailable).
     *
     * <p>The actual work may happen asynchronously to this call — {@link #isReady()} says whether
     * it finished, {@link #failure()} names the reason if it did not.
     */
    boolean prepare();

    /** True once the target is in place and the first wave may begin. */
    boolean isReady();

    /** Language key of whatever made preparation fail, or null. */
    String failure();

    /** World of the target — known before {@link #prepare()}, for the enabledWorlds check. */
    World world();

    /** Centre point for fog, announcements and the bossbars. */
    Location center();

    /**
     * Point the next group of attackers should appear around. With several targets this rotates,
     * so the waves do not all arrive from the same direction.
     */
    Location spawnAnchor();

    /** Remaining integrity, from 1.0 (untouched) down to 0.0 (lost). */
    double integrity();

    /** True once the defeat condition is met. */
    boolean isLost();

    /** Once per second, from the global scheduler. */
    void tick();

    /**
     * From the attacker's region thread, once per second: whatever this kind of target cares
     * about — damage to the object when close enough, or a region breach.
     *
     * @param waveDamage  damage this attacker deals per blow
     * @param ranged      true for an attacker that shoots instead of closing in
     * @param rangedReach how far a ranged attacker may stand off and still hit the target
     */
    void inspectAttacker(Entity attacker, double waveDamage, boolean ranged, double rangedReach);

    /**
     * From the wave leader's region thread, once per second. Objectives take damage as from any
     * attacker. Zones and regions count a breach once the leader gets inside and return
     * {@code true}: the leader is through and the caller takes it off the field.
     *
     * @param leaderReach how far from an objective the leader may stand and still strike it
     */
    boolean inspectLeader(Entity leader, double damage, double leaderReach);

    /** Where an attacker at {@code from} should head, or null. */
    Location navTarget(Location from);

    /**
     * Living entity an attacker at {@code from} should target — entity style only. Defaults to
     * none, in which case everything goes through {@link #navTarget}.
     */
    default LivingEntity attackTarget(Location from) {
        return null;
    }

    /**
     * Whether an attacker standing at {@code from} is already within reach and should hold that
     * position instead of being pathed or nudged closer. Only the objective flavour returns
     * {@code true}; a zone or region has to be entered.
     *
     * @param ranged      true for an attacker that shoots instead of closing in
     * @param rangedReach how far a ranged attacker may stand off and still hit the target
     */
    default boolean holdsPosition(Location from, boolean ranged, double rangedReach) {
        return false;
    }

    /** Is this entity part of the target itself? Shields it from the general entity cleanup. */
    boolean isTracked(UUID id);

    /**
     * A tracked entity of the target was loaded again with its chunk. On Paper that is a new
     * object; the old reference is invalid from then on and has to be swapped for this one.
     */
    default void rebind(Entity entity) {
    }

    /** Removes everything the target itself put into the world. */
    void cleanup();

    /** Lines for {@code /spookyraid status}. */
    List<String> describe();

    /** Mode name as used in {@code raid.target.mode} and in the raid result. */
    String mode();

    /** Current state of every target, for the raid result. Reads counters only. */
    List<RaidResult.TargetResult> results();

    /**
     * Whether {@code loc} lies close to something still worth defending: within {@code radius}
     * of a standing objective, or inside the zone/region or within {@code radius} of its centre.
     * Called from the region thread that owns {@code loc}.
     */
    boolean defends(Location loc, double radius);

    /** Squared distance check that tolerates a different world or a missing centre. */
    static boolean within(Location a, Location b, double radius) {
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())
                && a.distanceSquared(b) <= radius * radius;
    }
}
