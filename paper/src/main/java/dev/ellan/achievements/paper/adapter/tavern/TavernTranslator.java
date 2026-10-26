package dev.ellan.achievements.paper.adapter.tavern;

import dev.ellan.achievements.core.event.GameplayEvent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** KaleidoscopeTavern events → tavern triggers. Pure. */
public final class TavernTranslator {
    private TavernTranslator() {
    }

    public static GameplayEvent production(UUID player, String trigger, String recipeId, String productId) {
        return new GameplayEvent(player, trigger, Map.of("recipe", lower(recipeId), "product", lower(productId)));
    }

    public static GameplayEvent drink(UUID player, String drinkId) {
        return new GameplayEvent(player, "tavern_drink", Map.of("drink", lower(drinkId)));
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
