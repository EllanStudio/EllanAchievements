package dev.ellan.achievements.core.display;

import java.util.Map;

/** An advancement icon: a vanilla item ID plus optional data components (e.g. {@code minecraft:item_model}). */
public record ResolvedIcon(String itemId, Map<String, Object> components) {
    public ResolvedIcon {
        components = Map.copyOf(components);
    }
}
