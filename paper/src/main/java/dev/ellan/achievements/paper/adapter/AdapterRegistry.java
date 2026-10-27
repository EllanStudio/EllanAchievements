package dev.ellan.achievements.paper.adapter;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Enables adapters one by one; a failing adapter is reported and skipped, never fatal. */
public final class AdapterRegistry {
    private final Map<String, String> status = new LinkedHashMap<>();
    private final Set<String> availableTriggers = new HashSet<>();

    public void enableAll(Plugin plugin, List<Adapter> adapters, Consumer<GameplayEvent> sink) {
        for (Adapter adapter : adapters) {
            String required = adapter.requiredPlugin();
            if (required != null && !Bukkit.getPluginManager().isPluginEnabled(required)) {
                status.put(adapter.id(), "not installed (" + required + ")");
                continue;
            }
            try {
                Set<String> unavailable = adapter.enable(plugin, sink);
                for (String trigger : adapter.triggers()) {
                    if (!unavailable.contains(trigger)) {
                        availableTriggers.add(trigger);
                    }
                }
                status.put(adapter.id(), unavailable.isEmpty() ? "enabled"
                    : "enabled (unavailable: " + String.join(", ", unavailable) + ")");
            } catch (Throwable failure) {
                status.put(adapter.id(), "incompatible: " + failure);
                plugin.getLogger().log(Level.WARNING, "Adapter " + adapter.id() + " disabled", failure);
            }
        }
    }

    public Map<String, String> status() {
        return Map.copyOf(status);
    }

    public Map<String, String> orderedStatus() {
        return new LinkedHashMap<>(status);
    }

    public boolean triggerAvailable(String trigger) {
        return availableTriggers.contains(trigger);
    }
}
