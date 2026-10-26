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
    /** Achievements the player completed, so a raised goal never un-completes them. Cleared by reset. */
    final Set<String> completed = new LinkedHashSet<>();

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

    public boolean completed(String id) {
        return completed.contains(id);
    }

    /** Returns true if this changed anything. */
    public boolean markCompleted(String id) {
        return completed.add(id);
    }

    public void unmarkCompleted(String id) {
        completed.remove(id);
    }

    /** Clears counter, unique values and completion; the reward record is kept so a reset never pays twice. */
    public void clear(String id) {
        counters.remove(id);
        uniques.remove(id);
        completed.remove(id);
    }

    public Map<String, Long> counters() {
        return Map.copyOf(counters);
    }
}
