package dev.ellan.achievements.paper.adapter;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Event binding by class name, for gameplay plugins that publish no Maven artifact
 * (KaleidoscopeCookery, KaleidoscopeTavern, TheBrewingProject). A missing or changed
 * class disables only the adapter that asked for it.
 */
public final class ReflectiveEvents {
    private static final Listener OWNER = new Listener() {
    };
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private ReflectiveEvents() {
    }

    /** Returns false if the event class does not exist; otherwise listens at MONITOR, ignoring cancelled events. */
    public static boolean register(Plugin plugin, String eventClassName, Consumer<Event> handler) {
        Class<? extends Event> type = eventClass(plugin, eventClassName);
        if (type == null) {
            return false;
        }
        Bukkit.getPluginManager().registerEvent(type, OWNER, EventPriority.MONITOR, (listener, event) -> {
            if (type.isInstance(event)) {
                handler.accept(event);
            }
        }, plugin, true);
        return true;
    }

    /** Calls a public no-argument method by name. */
    public static Object call(Object target, String method) {
        Method resolved = METHODS.computeIfAbsent(target.getClass().getName() + "#" + method, key -> {
            try {
                Method found = target.getClass().getMethod(method);
                found.setAccessible(true);
                return found;
            } catch (NoSuchMethodException failure) {
                throw new IllegalStateException(target.getClass().getName() + " has no method " + method + "()");
            }
        });
        try {
            return resolved.invoke(target);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(failure.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventClass(Plugin plugin, String name) {
        ClassLoader loader = plugin == null ? ReflectiveEvents.class.getClassLoader() : plugin.getClass().getClassLoader();
        try {
            Class<?> type = Class.forName(name, true, loader);
            return Event.class.isAssignableFrom(type) ? (Class<? extends Event>) type : null;
        } catch (ClassNotFoundException | LinkageError failure) {
            return null;
        }
    }
}
