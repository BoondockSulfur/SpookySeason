package org.example.feature.raid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.example.lang.Lang;

/**
 * Snapshot of the {@code raid} configuration, taken when a raid starts.
 *
 * <p>Deliberately frozen: a {@code /spooky reload} mid-assault must not pull wave count, strength
 * or target values out from under the defenders. The next raid reads fresh values.
 */
public final class RaidSettings {
    public final int countdownSeconds;
    public final int waveCount;
    public final int breakSeconds;
    public final int baseMobs;
    public final int mobsPerWave;
    public final int maxAlive;
    public final int spawnPerSecond;
    public final double spawnRadiusMin;
    public final double spawnRadiusMax;
    public final int waveTimeoutSeconds;
    public final int retargetSeconds;
    public final boolean respectRegionProtection;
    public final boolean pumpkinHeads;
    public final boolean nameVisible;
    // Namen je Angreifer-Typ, Schluessel in Grossbuchstaben; "DEFAULT" gilt fuer den Rest.
    private final Map<String, String> names = new HashMap<String, String>();
    private final boolean hasNameSection;
    public final boolean dropLoot;
    public final boolean babies;
    public final boolean noGriefing;
    public final double rangedReach;
    public final boolean friendlyFire;
    public final boolean hideBossBars;
    public final boolean debug;

    public final double baseHealth;
    public final double healthPerWave;
    public final double baseDamage;
    public final double damagePerWave;
    public final double targetDamage;
    public final double targetDamagePerWave;
    public final boolean focusTarget;
    public final double playerAggroRange;
    public final double speed;

    public final boolean leaderEnabled;
    public final int leaderWave;

    public final boolean fogEnabled;
    public final int fogIntervalSeconds;
    public final int fogDurationTicks;

    public final double announceRadius;
    public final double effectRadius;
    public final double participationRadius;

    public final String discId;
    public final boolean discLoop;

    public final List<String> victoryCommands;
    public final List<String> consolationCommands;
    public final boolean defeatEffectEnabled;
    public final int defeatEffectSeconds;

    private final List<RaidMob> mobs = new ArrayList<RaidMob>();

    private RaidSettings(FileConfiguration cfg) {
        this.countdownSeconds = Math.max(0, cfg.getInt("raid.announce.countdownSeconds", 60));
        this.waveCount = cfg.getInt("raid.waves.count", 5);
        this.breakSeconds = Math.max(0, cfg.getInt("raid.waves.breakSeconds", 20));
        this.baseMobs = Math.max(1, cfg.getInt("raid.waves.baseMobs", 8));
        this.mobsPerWave = Math.max(0, cfg.getInt("raid.waves.mobsPerWave", 4));
        this.maxAlive = Math.max(1, cfg.getInt("raid.waves.maxAlive", 40));
        this.spawnPerSecond = Math.max(1, cfg.getInt("raid.waves.spawnPerSecond", 4));
        double min = Math.max(4.0, cfg.getDouble("raid.waves.spawnRadiusMin", 22.0));
        double max = Math.max(min + 1.0, cfg.getDouble("raid.waves.spawnRadiusMax", 40.0));
        this.spawnRadiusMin = min;
        this.spawnRadiusMax = max;
        this.waveTimeoutSeconds = Math.max(0, cfg.getInt("raid.waves.timeoutSeconds", 420));
        this.retargetSeconds = Math.max(1, cfg.getInt("raid.waves.retargetSeconds", 3));
        this.respectRegionProtection = cfg.getBoolean("raid.waves.respectRegionProtection", true);
        this.pumpkinHeads = cfg.getBoolean("raid.waves.pumpkinHeads", true);
        this.nameVisible = cfg.getBoolean("raid.waves.nameVisible", false);
        ConfigurationSection nameSection = cfg.getConfigurationSection("raid.waves.names");
        this.hasNameSection = nameSection != null;
        if (nameSection != null) {
            for (String key : nameSection.getKeys(false)) {
                this.names.put(key.toUpperCase(Locale.ROOT), nameSection.getString(key, ""));
            }
        }
        this.dropLoot = cfg.getBoolean("raid.waves.dropLoot", false);
        this.babies = cfg.getBoolean("raid.waves.babies", true);
        this.noGriefing = cfg.getBoolean("raid.waves.noGriefing", true);
        this.rangedReach = Math.max(4.0, cfg.getDouble("raid.waves.rangedReach", 16.0));
        this.friendlyFire = cfg.getBoolean("raid.waves.friendlyFire", false);
        this.hideBossBars = cfg.getBoolean("raid.waves.hideBossBars", true);
        this.debug = cfg.getBoolean("raid.debug", false);

        this.baseHealth = Math.max(1.0, cfg.getDouble("raid.waves.health", 20.0));
        this.healthPerWave = Math.max(0.0, cfg.getDouble("raid.waves.healthPerWave", 0.15));
        this.baseDamage = Math.max(0.0, cfg.getDouble("raid.waves.damage", 3.0));
        this.damagePerWave = Math.max(0.0, cfg.getDouble("raid.waves.damagePerWave", 0.1));
        // Negative means "same as against players", so nothing changes for existing configs.
        double configuredTargetDamage = cfg.getDouble("raid.waves.targetDamage", -1.0);
        this.targetDamage = configuredTargetDamage < 0.0 ? this.baseDamage : configuredTargetDamage;
        double configuredTargetPerWave = cfg.getDouble("raid.waves.targetDamagePerWave", -1.0);
        this.targetDamagePerWave = configuredTargetPerWave < 0.0 ? this.damagePerWave : configuredTargetPerWave;
        this.focusTarget = !"players".equalsIgnoreCase(cfg.getString("raid.waves.focus", "target"));
        this.playerAggroRange = Math.max(0.0, cfg.getDouble("raid.waves.playerAggroRange", 6.0));
        this.speed = Math.max(0.05, cfg.getDouble("raid.waves.speed", 0.26));

        this.leaderEnabled = cfg.getBoolean("raid.waves.leaderEnabled", true);
        this.leaderWave = cfg.getInt("raid.waves.leaderWave", 0);

        this.fogEnabled = cfg.getBoolean("raid.fog.enabled", true);
        this.fogIntervalSeconds = Math.max(5, cfg.getInt("raid.fog.intervalSeconds", 45));
        this.fogDurationTicks = Math.max(20, cfg.getInt("raid.fog.durationTicks", 80));

        this.announceRadius = Math.max(16.0, cfg.getDouble("raid.announce.radius", 128.0));
        this.effectRadius = Math.max(16.0, cfg.getDouble("raid.fog.radius", 96.0));
        this.participationRadius = Math.max(16.0, cfg.getDouble("raid.rewards.participationRadius", 96.0));

        this.discId = cfg.getString("raid.music.discId", "");
        this.discLoop = cfg.getBoolean("raid.music.loop", true);

        this.victoryCommands = List.copyOf(cfg.getStringList("raid.rewards.victory"));
        this.consolationCommands = List.copyOf(cfg.getStringList("raid.rewards.consolation"));
        this.defeatEffectEnabled = cfg.getBoolean("raid.defeat.effect.enabled", true);
        this.defeatEffectSeconds = Math.max(0, cfg.getInt("raid.defeat.effect.durationSeconds", 120));

        this.parseMobs(cfg);
    }

    public static RaidSettings from(FileConfiguration cfg) {
        return new RaidSettings(cfg);
    }

    /**
     * Reads the attacker roster. The rich {@code mobs} list wins; if it is missing or yields
     * nothing usable, the old flat {@code composition} list is used instead, so a config from
     * an earlier version keeps working unchanged.
     */
    private void parseMobs(FileConfiguration cfg) {
        for (Map<?, ?> raw : cfg.getMapList("raid.waves.mobs")) {
            RaidMob mob = RaidMob.from(RaidSettings.asSection(raw));
            if (mob == null) continue;
            this.mobs.add(mob);
        }
        if (!this.mobs.isEmpty()) {
            return;
        }
        for (String entry : cfg.getStringList("raid.waves.composition")) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.split(":");
            EntityType type;
            try {
                type = EntityType.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException e) {
                continue;
            }
            int weight = 1;
            if (parts.length > 1) {
                try {
                    weight = Math.max(1, Integer.parseInt(parts[1].trim()));
                }
                catch (NumberFormatException e) {
                    weight = 1;
                }
            }
            this.mobs.add(RaidMob.legacy(type, weight));
        }
    }

    /** A YAML list entry arrives as a plain Map; RaidMob wants a ConfigurationSection. */
    private static ConfigurationSection asSection(Map<?, ?> raw) {
        MemoryConfiguration section = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            if (entry.getValue() instanceof Map) {
                section.createSection(key, (Map)entry.getValue());
                continue;
            }
            section.set(key, entry.getValue());
        }
        return section;
    }

    public boolean hasComposition() {
        return !this.mobs.isEmpty();
    }

    /**
     * Total of all guaranteed minimums for a wave. If this exceeds {@link #quotaForWave(int)} the
     * wave cannot honour every minimum and some archetypes will be short — worth warning about,
     * because the symptom (a mob that never turns up) looks nothing like the cause.
     */
    public int minimumsForWave(int wave) {
        int total = 0;
        for (RaidMob mob : this.mobs) {
            if (!mob.availableIn(wave)) continue;
            total += mob.minPerWave();
        }
        return total;
    }

    /** All archetypes that may appear in this wave at all — for the startup sanity check. */
    public boolean hasMobsForWave(int wave) {
        for (RaidMob mob : this.mobs) {
            if (mob.availableIn(wave)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws one archetype at random, weighted, from those allowed in this wave.
     *
     * @param spawnedThisWave how many of each archetype already spawned this wave, so a
     *                        {@code maxPerWave} cap can be honoured
     * @return the archetype, or {@code null} if nothing is eligible any more
     */
    public RaidMob pick(int wave, Map<String, Integer> spawnedThisWave) {
        // Guaranteed minimums come first, so a rare archetype cannot be skipped by bad luck.
        //
        // Picked at random among everything still short of its minimum, NOT in config order. In
        // order, a roster whose minimums add up to more than the wave holds starves whatever sits
        // at the bottom of the list: measured over ten waves, the last five archetypes never
        // spawned once while the earlier ones hit their minimum exactly.
        ArrayList<RaidMob> below = new ArrayList<RaidMob>();
        for (RaidMob mob : this.mobs) {
            if (!mob.availableIn(wave) || mob.minPerWave() <= 0) continue;
            int already = spawnedThisWave.getOrDefault(mob.id(), 0);
            if (already >= mob.minPerWave()) continue;
            if (mob.maxPerWave() > 0 && already >= mob.maxPerWave()) continue;
            below.add(mob);
        }
        if (!below.isEmpty()) {
            return below.get(ThreadLocalRandom.current().nextInt(below.size()));
        }
        ArrayList<RaidMob> eligible = new ArrayList<RaidMob>();
        int weightSum = 0;
        for (RaidMob mob : this.mobs) {
            if (!mob.availableIn(wave)) continue;
            if (mob.maxPerWave() > 0 && spawnedThisWave.getOrDefault(mob.id(), 0) >= mob.maxPerWave()) continue;
            eligible.add(mob);
            weightSum += mob.weight();
        }
        if (eligible.isEmpty()) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(weightSum);
        for (RaidMob mob : eligible) {
            roll -= mob.weight();
            if (roll < 0) {
                return mob;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    /**
     * Name for an attacker of this type, or {@code null} for "no custom name".
     *
     * <p>Lookup order: entry for the exact type, then {@code default}. If the whole section is
     * missing (config from an earlier version) the text from the language file is used, so an
     * update does not silently change how an existing server looks.
     */
    public String nameFor(EntityType type, int wave) {
        String name;
        if (!this.hasNameSection) {
            name = Lang.get("raid.attacker-name", new String[0]);
        } else {
            name = this.names.get(type.name());
            if (name == null) {
                name = this.names.getOrDefault("DEFAULT", "");
            }
        }
        return RaidSettings.resolveName(name, wave);
    }

    /** Same, but for an archetype that brings its own name. */
    public String resolveName(String archetypeName, EntityType type, int wave) {
        return archetypeName == null ? this.nameFor(type, wave) : RaidSettings.resolveName(archetypeName, wave);
    }

    private static String resolveName(String name, int wave) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.replace("{wave}", String.valueOf(wave));
    }

    /** Total number of attackers in wave {@code wave} (1-based). */
    public int quotaForWave(int wave) {
        return this.baseMobs + Math.max(0, wave - 1) * this.mobsPerWave;
    }

    public double healthForWave(int wave) {
        return this.baseHealth * (1.0 + this.healthPerWave * Math.max(0, wave - 1));
    }

    /** Damage against players — set as the attack attribute, so armour reduces it. */
    public double damageForWave(int wave) {
        return this.baseDamage * (1.0 + this.damagePerWave * Math.max(0, wave - 1));
    }

    /** Damage against a target per blow — raw, no armour involved. */
    public double targetDamageForWave(int wave) {
        return this.targetDamage * (1.0 + this.targetDamagePerWave * Math.max(0, wave - 1));
    }

    /** 0 in the config means "the last wave". */
    public int resolvedLeaderWave() {
        return this.leaderWave <= 0 ? this.waveCount : this.leaderWave;
    }
}
