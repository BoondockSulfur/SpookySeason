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

    /**
     * Der DEKLARIERTE Rückgabetyp des Bukkit-Getters, also das öffentliche Scheduler-Interface.
     *
     * Wichtig: Folias Implementierungsklassen sind teils paketprivat. Ein Method-Objekt, das über
     * {@code instanz.getClass()} gefunden wurde, lässt sich dann nicht aufrufen
     * (IllegalAccessException) — die Methode muss am öffentlichen Interface gesucht werden.
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

    /**
     * Führt die Aufgabe im Region-Thread der Entity aus — egal, wo die Entity gerade ist.
     * Anders als {@link #runAtLocation} braucht das keinen vorab bekannten Ort und bleibt
     * korrekt, wenn sich die Entity seit dem Aufruf in eine andere Region bewegt hat.
     */
    public static void runOnEntity(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            try {
                Method getScheduler = Scheduler.cachedMethod(entity.getClass(), "getScheduler", 0);
                Object entityScheduler = getScheduler.invoke((Object)entity);
                Class<?> schedulerType = getScheduler.getReturnType();
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = () -> {};
                Method method = Scheduler.cachedMethod(schedulerType, "run", 3);
                method.invoke(entityScheduler, plugin, consumer, retired);
            }
            catch (Exception e) {
                if (!plugin.isEnabled()) {
                    // Folia lehnt Tasks für deaktivierte Plugins ab (z.B. während onDisable).
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
     * Wie {@link #runEntityLater(Plugin, Entity, Runnable, long)}, aber mit Callback für den Fall,
     * dass die Entity vor Ablauf der Verzögerung verschwindet (auf Folia z.B. ein Logout).
     * Wer in der Aufgabe etwas Verbindliches tut, darf sie sonst still verlieren.
     */
    public static void runEntityLater(Plugin plugin, Entity entity, Runnable task, Runnable onRetired, long delayTicks) {
        if (FOLIA) {
            try {
                Method getScheduler = Scheduler.cachedMethod(entity.getClass(), "getScheduler", 0);
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
     * Wie {@link #runEntityTimer(Plugin, Entity, Runnable, long, long)}, aber mit Callback für den
     * Fall, dass Folia den Task fallen lässt, weil die Entity verschwunden ist (Tod, Chunk-Entladung).
     * Ohne diesen Callback bleibt der Aufrufer auf einem Zustand sitzen, den nie jemand aufräumt —
     * auf Paper übernimmt das der weiterlaufende Timer selbst, auf Folia niemand.
     */
    public static TaskHandle runEntityTimer(Plugin plugin, Entity entity, Runnable task, Runnable onRetired, long delayTicks, long periodTicks) {
        if (FOLIA) {
            try {
                Method getScheduler = Scheduler.cachedMethod(entity.getClass(), "getScheduler", 0);
                Object entityScheduler = getScheduler.invoke((Object)entity);
                Class<?> schedulerType = getScheduler.getReturnType();
                Consumer<Object> consumer = t -> task.run();
                Runnable retired = onRetired;
                Method method = Scheduler.cachedMethod(schedulerType, "runAtFixedRate", 5);
                Object handle = method.invoke(entityScheduler, plugin, consumer, retired, Math.max(1L, delayTicks), Math.max(1L, periodTicks));
                if (handle == null) {
                    // Entity war schon weg, bevor der Task überhaupt lief — Folia ruft dann
                    // auch das retired-Callback nicht mehr auf.
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
            // Bewusst NICHT verschlucken: Genau eine leere catch-Klausel an dieser Stelle hat
            // verdeckt, dass auf Folia überhaupt nichts abgebrochen wurde — bei jedem
            // /spooky reload lief ein weiterer Timer-Satz zusätzlich weiter.
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

