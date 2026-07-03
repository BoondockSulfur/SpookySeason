package org.example.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

/**
 * Versionsübergreifende Attribute-Auflösung: Mit MC 1.21.2/3 wurden die Attribute-Keys
 * umbenannt (minecraft:generic.max_health → minecraft:max_health) und die Enum-Konstanten
 * entsprechend (GENERIC_MAX_HEALTH → MAX_HEALTH). Enum-Konstanten im Bytecode binden an
 * genau eine Version — der Registry-Lookup mit Fallback läuft auf 1.21.x UND 26.x.
 */
public final class Attributes {
    public static final Attribute MAX_HEALTH = resolve("max_health", "generic.max_health");
    public static final Attribute MOVEMENT_SPEED = resolve("movement_speed", "generic.movement_speed");
    public static final Attribute ATTACK_DAMAGE = resolve("attack_damage", "generic.attack_damage");

    private Attributes() {
    }

    /** Erzwingt die Klasseninitialisierung, damit Auflösungsfehler sofort beim Enable auffallen. */
    public static void init() {
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
