package dev.ellan.achievements.core.util;

import java.util.HashMap;
import java.util.Map;

/** Limits full stack traces to one per key per window, so a broken adapter cannot flood the log. */
public final class ErrorThrottle {
    private final long windowMillis;
    private final Map<String, Long> lastLogged = new HashMap<>();

    public ErrorThrottle(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    public synchronized boolean shouldLogStack(String key, long nowMillis) {
        Long last = lastLogged.get(key);
        if (last != null && nowMillis - last < windowMillis) {
            return false;
        }
        lastLogged.put(key, nowMillis);
        return true;
    }
}
