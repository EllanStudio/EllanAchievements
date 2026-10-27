package dev.ellan.achievements.core.rules;

import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.event.GameplayEvent;

public final class FilterMatcher {
    private FilterMatcher() {
    }

    /** All filter entries must pass; an achievement without a filter matches every event of its trigger. */
    public static boolean matches(AchievementDefinition def, GameplayEvent event) {
        return def.trigger().equals(event.trigger())
            && def.filter().entrySet().stream()
                .allMatch(entry -> entry.getValue().test(event.attributes().get(entry.getKey())));
    }
}
