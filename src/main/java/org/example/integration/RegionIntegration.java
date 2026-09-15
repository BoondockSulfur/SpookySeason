package org.example.integration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
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
    // All reflection signatures are resolved once, here. If that fails the integration stays off
    // and warns, rather than silently allowing everything at runtime.
    //
    // They are resolved consistently against the DECLARED type (interface, public base class or
    // field type), never against instance.getClass(): implementation classes are often
    // package-private, and a Method object found on one of those cannot be invoked
    // (IllegalAccessException). That exact trap has already bitten once on Folia.
    private Method wgAdaptWorld;
    private Method wgGetRegionManager;
    private Method wgGetApplicableRegions;
    private Method wgRegionSetSize;
    private Method wgBlockVector3At;
    // Extra signatures for looking up a named region (the raid target in region mode). They are
    // independent of the spawn protection above: if only this part fails, canSpawnAt() stays
    // usable and merely region mode drops out.
    private Method wgGetRegion;
    private Method wgRegionMin;
    private Method wgRegionMax;
    private Method wgRegionContains;
    private Method bvGetX;
    private Method bvGetY;
    private Method bvGetZ;
    private boolean worldGuardRegionLookup;
    private Method gpGetClaimAt;
    private Method gpIsAdminClaim;
    private Method gpAllowBuild;
    // Set from arbitrary region threads; they only serve the warn-once behaviour.
    private volatile boolean warnedWorldGuard;
    private volatile boolean warnedGriefPrevention;

    public RegionIntegration(Plugin plugin) {
        this.plugin = plugin;
        this.detectPlugins();
    }

    private void detectPlugins() {
        Plugin wg = this.plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg != null && wg.isEnabled()) {
            try {
                Class<?> wgClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object wgInstance = wgClass.getMethod("getInstance").invoke(null);
                Method getPlatform = wgClass.getMethod("getPlatform");
                Object platform = getPlatform.invoke(wgInstance);
                Method getRegionContainer = getPlatform.getReturnType().getMethod("getRegionContainer");
                this.regionContainer = getRegionContainer.invoke(platform);
                // RegionContainer.get expects the WorldEdit world, not the Bukkit one.
                Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                this.wgAdaptWorld = adapterClass.getMethod("adapt", World.class);
                Class<?> weWorldClass = Class.forName("com.sk89q.worldedit.world.World");
                this.wgGetRegionManager = getRegionContainer.getReturnType().getMethod("get", weWorldClass);
                Class<?> blockVector3 = Class.forName("com.sk89q.worldedit.math.BlockVector3");
                this.wgBlockVector3At = blockVector3.getMethod("at", Double.TYPE, Double.TYPE, Double.TYPE);
                this.wgGetApplicableRegions = this.wgGetRegionManager.getReturnType().getMethod("getApplicableRegions", blockVector3);
                this.wgRegionSetSize = this.wgGetApplicableRegions.getReturnType().getMethod("size");
                try {
                    Class<?> regionManagerClass = this.wgGetRegionManager.getReturnType();
                    this.wgGetRegion = regionManagerClass.getMethod("getRegion", String.class);
                    Class<?> protectedRegionClass = this.wgGetRegion.getReturnType();
                    this.wgRegionMin = protectedRegionClass.getMethod("getMinimumPoint");
                    this.wgRegionMax = protectedRegionClass.getMethod("getMaximumPoint");
                    this.wgRegionContains = protectedRegionClass.getMethod("contains", blockVector3);
                    // WorldEdit 7.3 hat getX()/getY()/getZ() durch x()/y()/z() ersetzt.
                    this.bvGetX = RegionIntegration.coordMethod(blockVector3, "getX", "x");
                    this.bvGetY = RegionIntegration.coordMethod(blockVector3, "getY", "y");
                    this.bvGetZ = RegionIntegration.coordMethod(blockVector3, "getZ", "z");
                    this.worldGuardRegionLookup = true;
                }
                catch (Exception e) {
                    this.plugin.getLogger().warning("WorldGuard region lookup unavailable (named regions cannot be used as a target): " + e.getMessage());
                }
                this.worldGuardEnabled = true;
                this.plugin.getLogger().info("WorldGuard integration enabled.");
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("WorldGuard detected but integration failed: " + e.getMessage());
            }
        }
        Plugin gp = this.plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gp != null && gp.isEnabled()) {
            try {
                Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                Object gpInstance = gpClass.getField("instance").get(null);
                // Resolve the signature on the declared field type (DataStore), not on the
                // concrete instance: that is FlatFileDataStore or DatabaseDataStore depending on
                // the configuration.
                Field dataStoreField = gpClass.getField("dataStore");
                this.gpDataStore = dataStoreField.get(gpInstance);
                Class<?> claimClass = Class.forName("me.ryanhamshire.GriefPrevention.Claim");
                this.gpGetClaimAt = dataStoreField.getType().getMethod("getClaimAt", Location.class, Boolean.TYPE, claimClass);
                this.gpIsAdminClaim = claimClass.getMethod("isAdminClaim");
                this.gpAllowBuild = claimClass.getMethod("allowBuild", Player.class, Material.class);
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
            Object vector = this.wgBlockVector3At.invoke(null, location.getX(), location.getY(), location.getZ());
            Object regions = this.wgGetApplicableRegions.invoke(regionManager, vector);
            return ((Integer)this.wgRegionSetSize.invoke(regions)).intValue() == 0;
        }
        catch (Exception e) {
            this.warnWorldGuard(e);
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
            this.warnWorldGuard(e);
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
                    && ((Boolean)this.gpIsAdminClaim.invoke(claim)).booleanValue();
        }
        catch (Exception e) {
            this.warnGriefPrevention(e);
            return true;
        }
    }

    private boolean checkGriefPreventionPlayer(Player player, Location location) {
        try {
            Object claim = this.gpGetClaimAt.invoke(this.gpDataStore, location, false, null);
            if (claim == null) {
                return true;
            }
            // allowBuild() returns the reason as text; null means "allowed".
            return this.gpAllowBuild.invoke(claim, player, Material.GRASS_BLOCK) == null;
        }
        catch (Exception e) {
            this.warnGriefPrevention(e);
            return true;
        }
    }

    private void warnWorldGuard(Exception e) {
        if (this.warnedWorldGuard) {
            return;
        }
        this.warnedWorldGuard = true;
        this.plugin.getLogger().warning("WorldGuard region check failed (allowing by default): " + e);
    }

    private void warnGriefPrevention(Exception e) {
        if (this.warnedGriefPrevention) {
            return;
        }
        this.warnedGriefPrevention = true;
        this.plugin.getLogger().warning("GriefPrevention claim check failed (allowing by default): " + e);
    }

    /** True when named WorldGuard regions can be queried. */
    public boolean hasRegionLookup() {
        return this.worldGuardRegionLookup;
    }

    public boolean regionExists(World world, String regionName) {
        return this.findRegion(world, regionName) != null;
    }

    /**
     * Centre of a named region. Y is the midpoint between the lower and upper edge — anyone who
     * needs a ground-level point has to search for it themselves, which only works on the region
     * thread of that location.
     */
    public Location regionCenter(World world, String regionName) {
        Object region = this.findRegion(world, regionName);
        if (region == null) {
            return null;
        }
        try {
            Object min = this.wgRegionMin.invoke(region);
            Object max = this.wgRegionMax.invoke(region);
            double x = (RegionIntegration.coord(this.bvGetX, min) + RegionIntegration.coord(this.bvGetX, max)) / 2.0 + 0.5;
            double y = (RegionIntegration.coord(this.bvGetY, min) + RegionIntegration.coord(this.bvGetY, max)) / 2.0;
            double z = (RegionIntegration.coord(this.bvGetZ, min) + RegionIntegration.coord(this.bvGetZ, max)) / 2.0 + 0.5;
            return new Location(world, x, y, z);
        }
        catch (Exception e) {
            this.warnWorldGuard(e);
            return null;
        }
    }

    public boolean isInRegion(Location location, String regionName) {
        if (location == null) {
            return false;
        }
        Object region = this.findRegion(location.getWorld(), regionName);
        if (region == null) {
            return false;
        }
        try {
            Object vector = this.wgBlockVector3At.invoke(null, location.getX(), location.getY(), location.getZ());
            return ((Boolean)this.wgRegionContains.invoke(region, vector)).booleanValue();
        }
        catch (Exception e) {
            this.warnWorldGuard(e);
            return false;
        }
    }

    private Object findRegion(World world, String regionName) {
        if (!this.worldGuardRegionLookup || world == null || regionName == null || regionName.isEmpty()) {
            return null;
        }
        try {
            Object weWorld = this.wgAdaptWorld.invoke(null, world);
            Object regionManager = this.wgGetRegionManager.invoke(this.regionContainer, weWorld);
            if (regionManager == null) {
                return null;
            }
            // WorldGuard fuehrt Region-IDs intern in Kleinschreibung.
            return this.wgGetRegion.invoke(regionManager, regionName.toLowerCase(Locale.ROOT));
        }
        catch (Exception e) {
            this.warnWorldGuard(e);
            return null;
        }
    }

    private static Method coordMethod(Class<?> vectorClass, String primary, String fallback) throws NoSuchMethodException {
        try {
            return vectorClass.getMethod(primary);
        }
        catch (NoSuchMethodException e) {
            return vectorClass.getMethod(fallback);
        }
    }

    private static double coord(Method accessor, Object vector) throws Exception {
        return ((Number)accessor.invoke(vector)).doubleValue();
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
