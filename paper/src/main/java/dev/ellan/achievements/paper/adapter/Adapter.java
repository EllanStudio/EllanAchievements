package dev.ellan.achievements.paper.adapter;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.function.Consumer;

/** Translates one gameplay plugin's events into {@link GameplayEvent}s. The only code that knows that plugin. */
public interface Adapter {
    /** Short id shown in {@code /ea status}, e.g. {@code fishing}. */
    String id();

    /** Plugin that must be enabled, or null when the adapter only needs Paper. */
    String requiredPlugin();

    Set<String> triggers();

    /**
     * Registers listeners. Throwing disables this adapter only.
     *
     * @return triggers that are unavailable even though the adapter enabled (e.g. an older plugin version)
     */
    Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) throws Throwable;
}
