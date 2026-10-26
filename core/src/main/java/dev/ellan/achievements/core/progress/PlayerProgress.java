package dev.ellan.achievements.core.progress;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One player's counters, unique-value sets and paid rewards, keyed by achievement full ID. */
public final class PlayerProgress {
    final Map<String, Long> counters = new LinkedHashMap<>();
    final Map<String, Set<String>> uniques = new LinkedHashMap<>();
    final Set<String> rewarded = new LinkedHashSet<>();

    public static PlayerProgress empty() {
        return new PlayerProgress();
    }

    public long counter(String id) {
        return counters.getOrDefault(id, 0L);
    }

    public void setCounter(String id, long value) {
        if (value <= 0) {
            counters.remove(id);
        } else {
            counters.put(id, value);
        }
    }

    /** The mutable unique-value set for an achievement, created on demand. */
    public Set<String> uniques(String id) {
        return uniques.computeIfAbsent(id, key -> new LinkedHashSet<>());
    }

    public boolean rewarded(String id) {
        return rewarded.contains(id);
    }

    public void markRewarded(String id) {
        rewarded.add(id);
    }

    /** Clears the counter and unique values; the reward record is kept so a reset never pays twice. */
    public void clear(String id) {
        counters.remove(id);
        uniques.remove(id);
    }

    public Map<String, Long> counters() {
        return Map.copyOf(counters);
    }
}
