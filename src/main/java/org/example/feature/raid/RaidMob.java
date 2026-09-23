package org.example.feature.raid;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * One kind of attacker: an entity type plus the stats, gear and wave window that make it
 * distinct. Several archetypes may share the same entity type — a plain zombie and a bigger,
 * armoured one are two archetypes over {@code ZOMBIE}.
 *
 * <p>Every stat defaults to "unset" ({@code -1} / {@code null}) and then falls back to the
 * wave-wide value from {@link RaidSettings}. That keeps a minimal entry like
 * {@code {type: ZOMBIE, weight: 5}} working exactly like the old flat composition list.
 */
public final class RaidMob {

    private static final double UNSET = -1.0;

    private final String id;
    private final EntityType type;
    private final int weight;
    private final int fromWave;
    private final int untilWave;
    private final int maxPerWave;
    private final int minPerWave;
    private final double health;
    private final double damage;
    private final double targetDamage;
    private final double speed;
    private final double scale;
    private final double spawnRadiusMin;
    private final double spawnRadiusMax;
    private final String name;
    private final Boolean ranged;
    private final Map<String, Material> equipment = new LinkedHashMap<String, Material>();

    private RaidMob(String id, EntityType type, int weight, int fromWave, int untilWave, int maxPerWave, int minPerWave,
                    double health, double damage, double targetDamage, double speed, double scale, String name,
                    Boolean ranged, double spawnRadiusMin, double spawnRadiusMax) {
        this.id = id;
        this.type = type;
        this.weight = weight;
        this.fromWave = fromWave;
        this.untilWave = untilWave;
        this.maxPerWave = maxPerWave;
        this.minPerWave = minPerWave;
        this.health = health;
        this.damage = damage;
        this.targetDamage = targetDamage;
        this.speed = speed;
        this.scale = scale;
        this.name = name;
        this.ranged = ranged;
        this.spawnRadiusMin = spawnRadiusMin;
        this.spawnRadiusMax = spawnRadiusMax;
    }

    /**
     * Reads one entry of the {@code raid.waves.mobs} list.
     *
     * @return the archetype, or {@code null} if the entry names no usable entity type
     */
    public static RaidMob from(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(section.getString("type", "").trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException e) {
            return null;
        }
        RaidMob mob = new RaidMob(
                section.getString("id", type.name()),
                type,
                Math.max(1, section.getInt("weight", 1)),
                Math.max(1, section.getInt("fromWave", 1)),
                Math.max(0, section.getInt("untilWave", 0)),
                Math.max(0, section.getInt("maxPerWave", 0)),
                Math.max(0, section.getInt("minPerWave", 0)),
                section.getDouble("health", UNSET),
                section.getDouble("damage", UNSET),
                section.getDouble("targetDamage", UNSET),
                section.getDouble("speed", UNSET),
                section.getDouble("scale", UNSET),
                section.getString("name", null),
                section.contains("ranged") ? Boolean.valueOf(section.getBoolean("ranged")) : null,
                section.getDouble("spawnRadiusMin", UNSET),
                section.getDouble("spawnRadiusMax", UNSET));
        ConfigurationSection gear = section.getConfigurationSection("equipment");
        if (gear != null) {
            for (String slot : gear.getKeys(false)) {
                Material material = Material.matchMaterial(gear.getString(slot, ""));
                if (material == null) continue;
                mob.equipment.put(slot.toLowerCase(Locale.ROOT), material);
            }
        }
        return mob;
    }

    /** Minimal archetype for the legacy {@code composition} format ({@code TYPE:weight}). */
    public static RaidMob legacy(EntityType type, int weight) {
        return new RaidMob(type.name(), type, weight, 1, 0, 0, 0, UNSET, UNSET, UNSET, UNSET, UNSET, null, null, UNSET, UNSET);
    }

    public String id() {
        return this.id;
    }

    public EntityType type() {
        return this.type;
    }

    public int weight() {
        return this.weight;
    }

    /** Whether this archetype may appear in the given wave at all. */
    public boolean availableIn(int wave) {
        if (wave < this.fromWave) {
            return false;
        }
        return this.untilWave <= 0 || wave <= this.untilWave;
    }

    /** Upper bound per wave, or {@code 0} for "no limit". */
    public int maxPerWave() {
        return this.maxPerWave;
    }

    /**
     * Guaranteed minimum per wave, or {@code 0} for "leave it to chance". Useful for rare
     * archetypes that a low weight would otherwise leave out of a raid entirely.
     */
    public int minPerWave() {
        return this.minPerWave;
    }

    public double healthOr(double fallback) {
        return this.health < 0.0 ? fallback : this.health;
    }

    public double damageOr(double fallback) {
        return this.damage < 0.0 ? fallback : this.damage;
    }

    public double targetDamageOr(double fallback) {
        return this.targetDamage < 0.0 ? fallback : this.targetDamage;
    }

    public double speedOr(double fallback) {
        return this.speed < 0.0 ? fallback : this.speed;
    }

    /** Size multiplier, or {@code -1} when the archetype does not change the size. */
    public double scale() {
        return this.scale;
    }

    public String nameOr(String fallback) {
        return this.name == null ? fallback : this.name;
    }

    /**
     * Whether this attacker shoots at the target rather than closing to melee range.
     *
     * <p>Unless the archetype says otherwise this follows the weapon: anything carrying a bow or
     * crossbow is treated as a shooter. Explicitly setting {@code ranged} wins, which is how a
     * bow-carrying mob can still be made to charge in.
     */
    public boolean isRanged() {
        if (this.ranged != null) {
            return this.ranged.booleanValue();
        }
        Material weapon = this.equipment.get("weapon");
        if (weapon == null) {
            weapon = this.equipment.get("mainhand");
        }
        return weapon == Material.BOW || weapon == Material.CROSSBOW;
    }

    /**
     * Distance band this archetype appears in, or the wave-wide value when it sets none. Useful
     * for mobs that travel badly, such as withers, which can be spawned next to the objective.
     */
    public double spawnRadiusMinOr(double fallback) {
        return this.spawnRadiusMin < 0.0 ? fallback : this.spawnRadiusMin;
    }

    public double spawnRadiusMaxOr(double fallback) {
        return this.spawnRadiusMax < 0.0 ? fallback : this.spawnRadiusMax;
    }

    public boolean hasEquipment() {
        return !this.equipment.isEmpty();
    }

    /** Applies the configured gear. Drop chances are forced to zero throughout. */
    public void equip(EntityEquipment eq) {
        for (Map.Entry<String, Material> entry : this.equipment.entrySet()) {
            ItemStack item = new ItemStack(entry.getValue());
            switch (entry.getKey()) {
                case "weapon":
                case "mainhand": {
                    eq.setItemInMainHand(item);
                    eq.setItemInMainHandDropChance(0.0f);
                    break;
                }
                case "offhand": {
                    eq.setItemInOffHand(item);
                    eq.setItemInOffHandDropChance(0.0f);
                    break;
                }
                case "helmet": {
                    eq.setHelmet(item);
                    eq.setHelmetDropChance(0.0f);
                    break;
                }
                case "chestplate": {
                    eq.setChestplate(item);
                    eq.setChestplateDropChance(0.0f);
                    break;
                }
                case "leggings": {
                    eq.setLeggings(item);
                    eq.setLeggingsDropChance(0.0f);
                    break;
                }
                case "boots": {
                    eq.setBoots(item);
                    eq.setBootsDropChance(0.0f);
                    break;
                }
            }
        }
    }
}
