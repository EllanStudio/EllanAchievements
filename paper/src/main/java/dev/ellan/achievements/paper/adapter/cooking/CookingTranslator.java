package dev.ellan.achievements.paper.adapter.cooking;

import dev.ellan.achievements.core.event.GameplayEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** KaleidoscopeCookery events → cooking triggers. Pure. */
public final class CookingTranslator {
    public static final String POT = "pot";
    public static final String STOCKPOT = "stockpot";
    public static final String STEAMER = "steamer";
    public static final String SHAWARMA = "shawarma";

    private CookingTranslator() {
    }

    public static GameplayEvent dish(UUID player, String dishId, String station) {
        return new GameplayEvent(player, "dish_cook", Map.of("dish", lower(dishId), "station", station));
    }

    public static GameplayEvent stirFry(UUID player, int count) {
        return new GameplayEvent(player, "cooking_stir_fry", Map.of("count", count));
    }

    public static GameplayEvent grind(UUID player, String productId) {
        return new GameplayEvent(player, "cooking_grind", Map.of("product", lower(productId)));
    }

    public static GameplayEvent sickle(UUID player, String blockKey) {
        return new GameplayEvent(player, "cooking_sickle", Map.of("block", lower(blockKey)));
    }

    public static GameplayEvent action(UUID player, String action) {
        return new GameplayEvent(player, "cooking_action", Map.of("action", lower(action)));
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
