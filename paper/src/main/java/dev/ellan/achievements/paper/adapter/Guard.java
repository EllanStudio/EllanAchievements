package dev.ellan.achievements.paper.adapter;

import dev.ellan.achievements.core.util.ErrorThrottle;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/** Runs adapter listener code so a failure is logged (one stack trace per minute per key) and never reaches the gameplay plugin. */
public final class Guard {
    private static final ErrorThrottle THROTTLE = new ErrorThrottle(60_000);

    private Guard() {
    }

    public static void run(Plugin plugin, String key, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException | LinkageError failure) {
            if (THROTTLE.shouldLogStack(key, System.currentTimeMillis())) {
                plugin.getLogger().log(Level.WARNING, "Achievement adapter failed handling " + key, failure);
            }
        }
    }
}
