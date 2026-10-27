package dev.ellan.achievements.core.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A gameplay action translated out of some gameplay plugin. Attribute values are
 * strings, numbers, booleans, or lists of strings; absent attributes are simply missing.
 */
public record GameplayEvent(UUID player, String trigger, Map<String, Object> attributes, long amount) {
    public GameplayEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(trigger, "trigger");
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (value != null) {
                    copy.put(key, value);
                }
            });
        }
        attributes = Collections.unmodifiableMap(copy);
    }

    public GameplayEvent(UUID player, String trigger, Map<String, Object> attributes) {
        this(player, trigger, attributes, 1);
    }
}
