package dev.ellan.achievements.core.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The loaded, valid achievements. Immutable. */
public final class AchievementCatalog {
    private final List<TabDefinition> tabs;
    private final Map<String, AchievementDefinition> byFullId = new LinkedHashMap<>();
    private final Map<String, List<AchievementDefinition>> byTrigger = new LinkedHashMap<>();
    private final String sha256;

    public AchievementCatalog(List<TabDefinition> tabs, String sha256) {
        this.tabs = List.copyOf(tabs);
        this.sha256 = sha256;
        for (TabDefinition tab : this.tabs) {
            for (AchievementDefinition def : tab.achievements()) {
                byFullId.put(def.fullId(), def);
                if (!def.disabled()) {
                    byTrigger.computeIfAbsent(def.trigger(), key -> new ArrayList<>()).add(def);
                }
            }
        }
    }

    public List<TabDefinition> tabs() {
        return tabs;
    }

    public Optional<AchievementDefinition> byFullId(String fullId) {
        return Optional.ofNullable(byFullId.get(fullId));
    }

    /** Enabled achievements for a trigger. */
    public List<AchievementDefinition> byTrigger(String trigger) {
        return List.copyOf(byTrigger.getOrDefault(trigger, List.of()));
    }

    /**
     * The nearest enabled ancestor's full ID, or null for the tab root. Disabled achievements
     * are not registered, so their children re-attach to the closest registered ancestor.
     */
    public String effectiveParent(AchievementDefinition def) {
        String parent = def.parent();
        while (parent != null) {
            AchievementDefinition ancestor = byFullId.get(parent);
            if (ancestor == null) {
                return null;
            }
            if (!ancestor.disabled()) {
                return parent;
            }
            parent = ancestor.parent();
        }
        return null;
    }

    public String sha256() {
        return sha256;
    }
}
