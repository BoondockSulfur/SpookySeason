/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package org.example.integration;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class RegionIntegration {
    private final Plugin plugin;
    private boolean worldGuardEnabled;
    private boolean griefPreventionEnabled;
    private Object regionContainer;
    private Object gpDataStore;
    // Alle Reflection-Signaturen werden hier einmalig aufgelöst — schlägt das fehl,
    // bleibt die Integration aus und es wird gewarnt, statt zur Laufzeit still alles zu erlauben.
    private Method wgAdaptWorld;
    private Method wgGetRegionManager;
    private Method gpGetClaimAt;
    private boolean warnedWorldGuard;
    private boolean warnedGriefPrevention;

    public RegionIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.detectPlugins();
    }

    private void detectPlugins() {
        Plugin gp;
        Plugin wg = this.plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg != null && wg.isEnabled()) {
            try {
                Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object wgInstance = wgClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
                Object platform = wgClass.getMethod("getPlatform", new Class[0]).invoke(wgInstance, new Object[0]);
                this.regionContainer = platform.getClass().getMethod("getRegionContainer", new Class[0]).invoke(platform, new Object[0]);
                // RegionContainer.get erwartet die WorldEdit-World, nicht die Bukkit-World.
                Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                this.wgAdaptWorld = adapterClass.getMethod("adapt", World.class);
                Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
                this.wgGetRegionManager = this.regionContainer.getClass().getMethod("get", weWorldClass);
                this.worldGuardEnabled = true;
                this.plugin.getLogger().info("WorldGuard integration enabled.");
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("WorldGuard detected but integration failed: " + e.getMessage());
            }
        }
        if ((gp = this.plugin.getServer().getPluginManager().getPlugin("GriefPrevention")) != null && gp.isEnabled()) {
            try {
                Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                Object gpInstance = gpClass.getField("instance").get(null);
                this.gpDataStore = gpInstance.getClass().getField("dataStore").get(gpInstance);
                Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
                this.gpGetClaimAt = this.gpDataStore.getClass().getMethod("getClaimAt", Location.class, Boolean.TYPE, claimClass);
                this.griefPreventionEnabled = true;
                this.plugin.getLogger().info("GriefPrevention integration enabled.");
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("GriefPrevention detected but integration failed: " + e.getMessage());
            }
        }
    }

    public boolean canSpawnAt(Location location) {
        if (!this.plugin.getConfig().getBoolean("regionIntegration.enabled", true)) {
            return true;
        }
        if (this.worldGuardEnabled && !this.checkWorldGuardSpawn(location)) {
            return false;
        }
        return !this.griefPreventionEnabled || this.checkGriefPreventionSpawn(location);
    }

    public boolean canPlayerInteract(Player player, Location location) {
        if (!this.plugin.getConfig().getBoolean("regionIntegration.enabled", true)) {
            return true;
        }
        if (this.worldGuardEnabled && !this.checkWorldGuardPlayer(player, location)) {
            return false;
        }
        return !this.griefPreventionEnabled || this.checkGriefPreventionPlayer(player, location);
    }

    private boolean checkWorldGuardSpawn(Location location) {
        try {
            if (this.plugin.getConfig().getBoolean("regionIntegration.worldGuard.allowInProtected", false)) {
                return true;
            }
            Object weWorld = this.wgAdaptWorld.invoke(null, location.getWorld());
            Object regionManager = this.wgGetRegionManager.invoke(this.regionContainer, weWorld);
            if (regionManager == null) {
                return true;
            }
            Class<?> bv3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object vector = bv3.getMethod("at", Double.TYPE, Double.TYPE, Double.TYPE).invoke(null, location.getX(), location.getY(), location.getZ());
            Object regions = regionManager.getClass().getMethod("getApplicableRegions", bv3).invoke(regionManager, vector);
            int count = (Integer)regions.getClass().getMethod("size", new Class[0]).invoke(regions, new Object[0]);
            return count == 0;
        }
        catch (Exception e) {
            if (!this.warnedWorldGuard) {
                this.warnedWorldGuard = true;
                this.plugin.getLogger().warning("WorldGuard region check failed (allowing by default): " + e);
            }
            return true;
        }
    }

    private boolean checkWorldGuardPlayer(Player player, Location location) {
        try {
            if (player.hasPermission("worldguard.region.bypass." + location.getWorld().getName()) || player.isOp()) {
                return true;
            }
            return this.checkWorldGuardSpawn(location);
        }
        catch (Exception e) {
            return true;
        }
    }

    private boolean checkGriefPreventionSpawn(Location location) {
        try {
            if (this.plugin.getConfig().getBoolean("regionIntegration.griefPrevention.allowInClaims", false)) {
                return true;
            }
            Object claim = this.gpGetClaimAt.invoke(this.gpDataStore, location, false, null);
            if (claim == null) {
                return true;
            }
            return this.plugin.getConfig().getBoolean("regionIntegration.griefPrevention.allowInAdminClaims", false)
                    && ((Boolean)claim.getClass().getMethod("isAdminClaim", new Class[0]).invoke(claim, new Object[0])).booleanValue();
        }
        catch (Exception e) {
            if (!this.warnedGriefPrevention) {
                this.warnedGriefPrevention = true;
                this.plugin.getLogger().warning("GriefPrevention claim check failed (allowing by default): " + e);
            }
            return true;
        }
    }

    private boolean checkGriefPreventionPlayer(Player player, Location location) {
        try {
            Object claim = this.gpGetClaimAt.invoke(this.gpDataStore, location, false, null);
            if (claim == null) {
                return true;
            }
            Object result = claim.getClass().getMethod("allowBuild", Player.class, Material.class).invoke(claim, player, Material.GRASS_BLOCK);
            return result == null;
        }
        catch (Exception e) {
            if (!this.warnedGriefPrevention) {
                this.warnedGriefPrevention = true;
                this.plugin.getLogger().warning("GriefPrevention claim check failed (allowing by default): " + e);
            }
            return true;
        }
    }

    public boolean isEnabled() {
        return this.worldGuardEnabled || this.griefPreventionEnabled;
    }

    public String getStatus() {
        if (!this.isEnabled()) {
            return "No region plugins detected";
        }
        StringBuilder sb = new StringBuilder();
        if (this.worldGuardEnabled) {
            sb.append("WorldGuard: Enabled");
        }
        if (this.griefPreventionEnabled) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append("GriefPrevention: Enabled");
        }
        return sb.toString();
    }
}

