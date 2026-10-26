package dev.ellan.achievements.core.config;

import java.util.List;

public record TabDefinition(String id, String title, String description, IconSpec icon, String background,
                            List<AchievementDefinition> achievements) {
    public TabDefinition {
        achievements = List.copyOf(achievements);
    }
}
