package dev.ellan.achievements.core.rules;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.CountMode;
import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.core.progress.PlayerProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Applies gameplay events to a player's counters (spec §6.3 step 2). */
public final class RuleEngine {
    private final AchievementCatalog catalog;

    public RuleEngine(AchievementCatalog catalog) {
        this.catalog = catalog;
    }

    /** Mutates {@code progress}; returns the achievements whose count changed. Completed ones are skipped. */
    public List<ProgressChange> apply(PlayerProgress progress, GameplayEvent event, Predicate<String> isCompleted) {
        List<ProgressChange> changes = new ArrayList<>();
        for (AchievementDefinition def : catalog.byTrigger(event.trigger())) {
            if (isCompleted.test(def.fullId()) || !FilterMatcher.matches(def, event)) {
                continue;
            }
            long before = progress.counter(def.fullId());
            long after = def.count() == CountMode.UNIQUE ? countUnique(progress, def, event) : saturatingAdd(before, event.amount());
            if (after != before) {
                progress.setCounter(def.fullId(), after);
                changes.add(new ProgressChange(def.fullId(), before, after, def.goal()));
            }
        }
        return changes;
    }

    private static long countUnique(PlayerProgress progress, AchievementDefinition def, GameplayEvent event) {
        Set<String> seen = progress.uniques(def.fullId());
        Object attribute = event.attributes().get(def.uniqueBy());
        if (attribute != null) {
            String key = uniqueKey(def, attribute.toString().toLowerCase(Locale.ROOT));
            if (key != null) {
                seen.add(key);
            }
        }
        // Always derive the count from the set so it self-heals after edits.
        return seen.size();
    }

    private static String uniqueKey(AchievementDefinition def, String value) {
        if (def.uniqueGroups().isEmpty()) {
            return value;
        }
        for (Map.Entry<String, List<String>> group : def.uniqueGroups().entrySet()) {
            if (group.getValue().stream().anyMatch(member -> member.equalsIgnoreCase(value))) {
                return group.getKey();
            }
        }
        return null;
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < a ? Long.MAX_VALUE : sum;
    }
}
