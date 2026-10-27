package dev.ellan.achievements.core.config;

import java.util.List;
import java.util.Map;

/**
 * One achievement. {@code fullId} is {@code <tabId>/<id>}; {@code parent} is a full ID,
 * or null when the achievement hangs directly under the tab root.
 */
public record AchievementDefinition(String tabId, String id, String fullId, String parent, String title,
                                    String description, IconSpec icon, Frame frame, boolean hidden, boolean announce,
                                    boolean toast, String trigger, Map<String, FilterValue> filter, long goal,
                                    CountMode count, String uniqueBy, Map<String, List<String>> uniqueGroups,
                                    RewardSpec rewards, boolean disabled) {
    public AchievementDefinition {
        filter = Map.copyOf(filter);
        uniqueGroups = Map.copyOf(uniqueGroups);
    }
}
