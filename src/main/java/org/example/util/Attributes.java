package org.example.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

/**
 * Attribute resolution across versions: MC 1.21.2/3 renamed the attribute keys
 * (minecraft:generic.max_health became minecraft:max_health) and the enum constants along with
 * them (GENERIC_MAX_HEALTH became MAX_HEALTH). Enum constants in bytecode bind to exactly one
 * version — a registry lookup with a fallback runs on 1.21.x AND 26.x.
 */
public final class Attributes {
    public static final Attribute MAX_HEALTH = resolve("max_health", "generic.max_health");
    public static final Attribute MOVEMENT_SPEED = resolve("movement_speed", "generic.movement_speed");
    public static final Attribute ATTACK_DAMAGE = resolve("attack_damage", "generic.attack_damage");
    public static final Attribute FOLLOW_RANGE = resolve("follow_range", "generic.follow_range");
    public static final Attribute KNOCKBACK_RESISTANCE = resolve("knockback_resistance", "generic.knockback_resistance");
    /**
     * Entity size multiplier. Only exists from MC 1.20.5 onwards, so this one is resolved
     * leniently: on an older server it stays null and callers simply skip it, instead of
     * taking the whole plugin down at enable time.
     */
    public static final Attribute SCALE = resolveOptional("scale", "generic.scale");

    private Attributes() {
    }

    /** Forces class initialisation, so resolution failures surface at enable time. */
    public static void init() {
    }

    private static Attribute resolveOptional(String modernKey, String legacyKey) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modernKey));
        return a != null ? a : Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacyKey));
    }

    private static Attribute resolve(String modernKey, String legacyKey) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(modernKey));
        if (a == null) {
            a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(legacyKey));
        }
        if (a == null) {
            throw new IllegalStateException("Attribute not found in registry: " + modernKey + " / " + legacyKey);
        }
        return a;
    }
}
