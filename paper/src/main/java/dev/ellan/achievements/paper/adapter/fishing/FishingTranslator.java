package dev.ellan.achievements.paper.adapter.fishing;

import dev.ellan.achievements.core.event.GameplayEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** CustomFishing result → {@code fish_catch}. Pure, so it is tested without a server. */
public final class FishingTranslator {
    private FishingTranslator() {
    }

    public static Optional<GameplayEvent> catchEvent(UUID player, boolean success, String lootId, String[] groups,
                                                     String lootType, Float size, Boolean newRecord, int amount) {
        if (!success || lootId == null) {
            return Optional.empty();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("loot", lootId);
        attributes.put("group", groups == null ? List.of() : List.of(groups));
        attributes.put("type", lootType == null ? null : lootType.toLowerCase(Locale.ROOT));
        attributes.put("size", size == null ? null : size.doubleValue());
        attributes.put("new_record", Boolean.TRUE.equals(newRecord));
        return Optional.of(new GameplayEvent(player, "fish_catch", attributes, Math.max(1, amount)));
    }
}
