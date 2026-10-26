package dev.ellan.achievements.paper.adapter.farming;

import dev.ellan.achievements.core.event.GameplayEvent;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** CustomCrops events → {@code crop_plant}, {@code crop_harvest}, {@code crop_quality}. Pure. */
public final class FarmingTranslator {
    private FarmingTranslator() {
    }

    public static GameplayEvent plant(UUID player, String cropId) {
        return new GameplayEvent(player, "crop_plant", Map.of("crop", lower(cropId)));
    }

    /** Only a player breaking a fully grown crop counts as a harvest. */
    public static Optional<GameplayEvent> harvest(UUID player, String cropId, boolean byPlayer, boolean finalStage,
                                                  String seasonOrNull) {
        if (!byPlayer || !finalStage || cropId == null) {
            return Optional.empty();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("crop", lower(cropId));
        attributes.put("season", seasonOrNull == null ? null : lower(seasonOrNull));
        return Optional.of(new GameplayEvent(player, "crop_harvest", attributes));
    }

    /** {@code quality} is the item's 1-based position in the crop's quality list. */
    public static Optional<GameplayEvent> quality(UUID player, String itemId, String[] qualityCrops) {
        if (itemId == null || qualityCrops == null) {
            return Optional.empty();
        }
        for (int index = 0; index < qualityCrops.length; index++) {
            if (itemId.equalsIgnoreCase(qualityCrops[index])) {
                return Optional.of(new GameplayEvent(player, "crop_quality",
                    Map.of("item", lower(itemId), "quality", index + 1)));
            }
        }
        return Optional.empty();
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
