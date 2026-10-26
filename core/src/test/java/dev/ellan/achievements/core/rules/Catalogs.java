package dev.ellan.achievements.core.rules;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.config.ConfigLoader;
import dev.ellan.achievements.core.config.ExternalIds;
import dev.ellan.achievements.core.config.LoadResult;
import dev.ellan.achievements.core.config.Severity;

import java.util.Optional;
import java.util.TreeMap;

/** Builds a catalog from inline YAML achievements in a tab called {@code t}. */
final class Catalogs {
    static final ExternalIds ALL = new ExternalIds() {
        @Override public boolean itemExists(String id) { return true; }
        @Override public Optional<Boolean> fishingLoot(String id) { return Optional.empty(); }
        @Override public Optional<Boolean> crop(String id) { return Optional.empty(); }
        @Override public Optional<Boolean> brewRecipe(String id) { return Optional.empty(); }
        @Override public boolean triggerAvailable(String trigger) { return true; }
    };

    private Catalogs() {
    }

    static AchievementCatalog of(String achievements) {
        TreeMap<String, String> files = new TreeMap<>();
        files.put("t.yml", """
            tab: { id: t, title: T, icon: { item: "minecraft:stone" } }
            achievements:
            """ + achievements);
        LoadResult result = ConfigLoader.load(files, ALL);
        if (result.issues().stream().anyMatch(issue -> issue.severity() == Severity.ERROR)) {
            throw new IllegalStateException("bad test config: " + result.issues());
        }
        return result.catalog();
    }
}
