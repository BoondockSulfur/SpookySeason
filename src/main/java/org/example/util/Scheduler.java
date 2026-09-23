package org.example.util;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class Scheduler {
    private static final boolean FOLIA;
    private static final ConcurrentHashMap<String, Object> SCHEDULER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    private Scheduler() {
    }

    private static Object schedulerInstance(String getterName) {
        return SCHEDULER_CACHE.computeIfAbsent(getterName, k -> {
            try {
                return Bukkit.class.getMethod(k).invoke(null);
            }
            catch (Exception e) {
                throw new RuntimeException("Scheduler lookup failed: " + k, e);
            }
        });
    }

    private static Method cachedMethod(Class<?> clazz, String name, int paramCount) {
        return METHOD_CACHE.computeIfAbsent(clazz.getName() + "#" + name + "/" + paramCount,
                k -> Scheduler.findMethod(clazz, name, paramCount));
    }

    /**
     * The declared return type of the Bukkit getter, i.e. the public scheduler interface. Some
     * of Folia's implementation classes are package-private, so a Method found through
     * {@code instance.getClass()} cannot be invoked; lookups go through the public interface.
     */
    private static Class<?> schedulerType(String getterName) {
        try {
            return Bukkit.class.getMethod(getterName).getReturnType();
        }
        catch (Exception e) {
            throw new RuntimeException("Scheduler type lookup failed: " + getterName, e);
        }
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static TaskHandle runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            try {
                Object scheduler = Scheduler.schedulerInstance("getGlobalRegionScheduler");
                Consumer<Object> consumer = t -> task.run();
                Method method = Scheduler.cachedMethod(Scheduler.schedulerType("getGlobalRegionScheduler"), "runAtFixedRate", 4);
                Object handle = method.invoke(scheduler, plugin, consumer, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                Class<?> taskType = method.getReturnType();
                return () -> Scheduler.cancelTask(taskType, handle);
            }
            catch (Exception e) {
                throw new RuntimeException("Folia global timer failed", e);
            }
        }
        BukkitTask t2 = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return () -> ((BukkitTask)t2).cancel();
    }

    public static void runLater(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            try {
                Object scheduler = Scheduler.schedulerInstance("getGlobalRegionScheduler");
                Consumer<Object> consumer = t -> task.run();
                Method method = Scheduler.cachedMethod(Scheduler.schedulerType("getGlobalRegionScheduler"), "runDelayed", 3);
                method.invoke(scheduler, plugin, consumer, Math.max(1L, delayTicks));
            }
            catch (Exception e) {
                throw new RuntimeException("Folia global delayed task failed", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = Scheduler.schedulerInstance("getRegionScheduler");
                Method method = Scheduler.schedulerType("getRegionScheduler").getMethod("execute", Plugin.class, Location.class, Runnable.class);
                method.invoke(scheduler, plugin, location, task);
            }
            catch (Exception e) {
                if (!plugin.isEnabled()) {
                    // Folia refuses tasks for disabled plugins (during onDisable, for instance).
                    // Marked entities left behind are cleared by EntityCleanupListener on the next chunk load.
                    return;
                }
                throw new RuntimeException("Folia region scheduler failed", e);
            }
        } else {
            task.run();
        }
    }

    /**
     * Runs the task on the entity's region thread, wherever that entity currently is. Unlike
     * {@link #runAtLocation} this needs no location known up front and stays correct if the entity
     * has moved into a different region since the call.
     */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        // getScheduler is resolved on the Entity interface, not on entity.getClass(): the declared
        // return type is the public EntityScheduler interface and one cache entry serves every
        // entity type.
        if (FOLIA) {
            try {
                Method getScheduler = Scheduler.cachedMethod(Entity.class, "getScheduler", 0);
                Object entityScheduler = getScheduler.invoke((Object)entity);
                Class<?> schedulerType = getScheduler.getReturnType();
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = () -> {};
                Method method = Scheduler.cachedMethod(schedulerType, "run", 3);
                method.invoke(entityScheduler, plugin, consumer, retired);
            }
            catch (Exception e) {
                if (!plugin.isEnabled()) {
                    // Folia refuses tasks for disabled plugins (during onDisable, for instance).
                    return;
                }
                throw new RuntimeException("Folia entity scheduler failed", e);
            }
        } else {
            task.run();
        }
    }

    public static void runEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        Scheduler.runEntityLater(plugin, entity, task, () -> {}, delayTicks);
    }

    /**
     * Like {@link #runEntityLater(Plugin, Entity, Runnable, long)}, but with a callback for the
     * case where the entity disappears before the delay elapses (for example a logout on Folia).
     */
    public static void runEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable onRetired, long delayTicks) {
        if (FOLIA) {
            try {
                Method getScheduler = Scheduler.cachedMethod(Entity.class, "getScheduler", 0);
                Object entityScheduler = getScheduler.invoke((Object)entity);
                Class<?> schedulerType = getScheduler.getReturnType();
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = onRetired;
                Method method = Scheduler.cachedMethod(schedulerType, "runDelayed", 4);
                Object handle = method.invoke(entityScheduler, plugin, consumer, retired, Math.max(1L, delayTicks));
                if (handle == null) {
                    onRetired.run();
                }
            }
            catch (Exception e) {
                throw new RuntimeException("Folia entity scheduler failed", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static TaskHandle runEntityTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        return Scheduler.runEntityTimer(plugin, entity, task, () -> {}, delayTicks, periodTicks);
    }

    /**
     * Like {@link #runEntityTimer(Plugin, Entity, Runnable, long, long)}, but with a callback for
     * the case where Folia drops the task because the entity is gone (death, chunk unload). On
     * Paper the timer keeps running and the caller handles that itself.
     */
    public static TaskHandle runEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable onRetired, long delayTicks, long periodTicks) {
        if (FOLIA) {
            try {
                Method getScheduler = Scheduler.cachedMethod(Entity.class, "getScheduler", 0);
                Object entityScheduler = getScheduler.invoke((Object)entity);
                Class<?> schedulerType = getScheduler.getReturnType();
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = onRetired;
                Method method = Scheduler.cachedMethod(schedulerType, "runAtFixedRate", 5);
                Object handle = method.invoke(entityScheduler, plugin, consumer, retired, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                if (handle == null) {
                    // The entity was already gone before the task ever ran — Folia then does not
                    // invoke the retired callback either.
                    onRetired.run();
                    return () -> {};
                }
                return () -> Scheduler.cancelTask(method.getReturnType(), handle);
            }
            catch (Exception e) {
                throw new RuntimeException("Folia entity timer failed", e);
            }
        }
        BukkitTask t2 = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return () -> ((BukkitTask)t2).cancel();
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = Scheduler.schedulerInstance("getAsyncScheduler");
                Consumer<Object> consumer = t -> task.run();
                Method method = Scheduler.cachedMethod(Scheduler.schedulerType("getAsyncScheduler"), "runNow", 2);
                method.invoke(scheduler, plugin, consumer);
            }
            catch (Exception e) {
                throw new RuntimeException("Folia async scheduler failed", e);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    private static void cancelTask(Class<?> taskType, Object handle) {
        try {
            Scheduler.cachedMethod(taskType, "cancel", 0).invoke(handle);
        }
        catch (Exception e) {
            // Logged, not swallowed: a silent failure here leaves timers running after a reload.
            Bukkit.getLogger().warning("[SpookySeason] Could not cancel scheduled task: " + e);
        }
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != paramCount) continue;
            return m;
        }
        throw new RuntimeException("Method " + name + "(" + paramCount + " params) not found on " + clazz.getName());
    }

    static {
        boolean f;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            f = true;
        }
        catch (ClassNotFoundException e) {
            f = false;
        }
        FOLIA = f;
    }

    public static interface TaskHandle {
        public void cancel();
    }
}

