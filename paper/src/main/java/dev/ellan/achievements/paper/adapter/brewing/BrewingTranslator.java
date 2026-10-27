package dev.ellan.achievements.paper.adapter.brewing;

import dev.ellan.achievements.core.event.GameplayEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** TheBrewingProject brews → {@code brew_obtain} / {@code brew_drink}. Pure. */
public final class BrewingTranslator {
    private BrewingTranslator() {
    }

    /** Counts only a brew the player helped make, and only the first time that bottle is obtained. */
    public static Optional<GameplayEvent> obtain(UUID player, Set<UUID> brewers, boolean alreadyCounted,
                                                 String recipe, String quality, double score) {
        if (alreadyCounted || brewers == null || !brewers.contains(player)) {
            return Optional.empty();
        }
        return Optional.of(new GameplayEvent(player, "brew_obtain", attributes(recipe, quality, score)));
    }

    public static GameplayEvent drink(UUID player, String recipe, String quality, double score) {
        return new GameplayEvent(player, "brew_drink", attributes(recipe, quality, score));
    }

    private static Map<String, Object> attributes(String recipe, String quality, double score) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("recipe", recipe == null ? null : recipe.toLowerCase(Locale.ROOT));
        attributes.put("quality", quality == null ? null : quality.toLowerCase(Locale.ROOT));
        attributes.put("score", score);
        return attributes;
    }
}
