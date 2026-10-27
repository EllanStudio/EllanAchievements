package dev.ellan.achievements.core.config;

import java.util.List;

public record LoadResult(AchievementCatalog catalog, List<Issue> issues) {
}
