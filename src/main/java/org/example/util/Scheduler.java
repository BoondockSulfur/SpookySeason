/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
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

    public static boolean isFolia() {
        return FOLIA;
    }

    public static TaskHandle runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            try {
                Object scheduler = Scheduler.schedulerInstance("getGlobalRegionScheduler");
                Consumer<Object> consumer = t -> task.run();
                Method method = Scheduler.cachedMethod(scheduler.getClass(), "runAtFixedRate", 4);
                Object handle = method.invoke(scheduler, plugin, consumer, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                return () -> Scheduler.cancelTask(handle);
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
                Method method = Scheduler.cachedMethod(scheduler.getClass(), "runDelayed", 3);
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
                Method method = scheduler.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class);
                method.invoke(scheduler, plugin, location, task);
            }
            catch (Exception e) {
                if (!plugin.isEnabled()) {
                    // Folia lehnt Tasks für deaktivierte Plugins ab (z.B. während onDisable).
                    // Liegengebliebene markierte Entities räumt EntityCleanupListener beim nächsten Chunk-Load ab.
                    return;
                }
                throw new RuntimeException("Folia region scheduler failed", e);
            }
        } else {
            task.run();
        }
    }

    public static void runEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (FOLIA) {
            try {
                Object entityScheduler = Scheduler.cachedMethod(entity.getClass(), "getScheduler", 0).invoke((Object)entity);
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = () -> {};
                Method method = Scheduler.cachedMethod(entityScheduler.getClass(), "runDelayed", 4);
                method.invoke(entityScheduler, plugin, consumer, retired, Math.max(1L, delayTicks));
            }
            catch (Exception e) {
                throw new RuntimeException("Folia entity scheduler failed", e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static TaskHandle runEntityTimer(Plugin plugin, Entity entity, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            try {
                Object entityScheduler = Scheduler.cachedMethod(entity.getClass(), "getScheduler", 0).invoke((Object)entity);
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = () -> {};
                Method method = Scheduler.cachedMethod(entityScheduler.getClass(), "runAtFixedRate", 5);
                Object handle = method.invoke(entityScheduler, plugin, consumer, retired, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                return handle != null ? () -> Scheduler.cancelTask(handle) : () -> {};
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
                Method method = Scheduler.cachedMethod(scheduler.getClass(), "runNow", 2);
                method.invoke(scheduler, plugin, consumer);
            }
            catch (Exception e) {
                throw new RuntimeException("Folia async scheduler failed", e);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    private static void cancelTask(Object handle) {
        try {
            handle.getClass().getMethod("cancel", new Class[0]).invoke(handle, new Object[0]);
        }
        catch (Exception exception) {
            // empty catch block
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

