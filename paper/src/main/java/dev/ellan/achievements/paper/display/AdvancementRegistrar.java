package dev.ellan.achievements.paper.display;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.TabDefinition;
import dev.ellan.achievements.core.display.AdvancementJson;
import dev.ellan.achievements.paper.icon.IconResolver;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads our advancements into the running server, in memory only (not persisted to the bukkit
 * datapack). A datapack reload drops them, so the plugin re-registers on ServerResourcesReloadedEvent.
 */
public final class AdvancementRegistrar {
    private final IconResolver icons;
    private final Logger logger;

    public AdvancementRegistrar(IconResolver icons, Logger logger) {
        this.icons = icons;
        this.logger = logger;
    }

    public static NamespacedKey key(String fullId) {
        return new NamespacedKey(AdvancementJson.NAMESPACE, fullId);
    }

    public static Advancement advancement(String fullId) {
        return Bukkit.getAdvancement(key(fullId));
    }

    /** Parents are always emitted before children (tab order, then YAML order with parents validated first). */
    public int registerAll(AchievementCatalog catalog) {
        Map<Key, String> json = new LinkedHashMap<>();
        for (TabDefinition tab : catalog.tabs()) {
            try {
                json.put(key(AdvancementJson.rootKey(tab.id())), AdvancementJson.tab(tab, icons.resolve(tab.icon())));
            } catch (RuntimeException failure) {
                logger.severe("Skipping tab " + tab.id() + ": " + failure);
                continue;
            }
            for (AchievementDefinition def : parentsFirst(catalog, tab.achievements())) {
                if (def.disabled()) {
                    continue;
                }
                // One broken entry must never stop the others from registering.
                try {
                    json.put(key(def.fullId()), AdvancementJson.achievement(def, catalog.effectiveParent(def),
                        icons.resolve(def.icon())));
                } catch (RuntimeException failure) {
                    logger.severe("Skipping achievement " + def.fullId() + ": " + failure);
                }
            }
        }
        List<Advancement> loaded = Bukkit.getUnsafe().loadAdvancements(json, false);
        if (loaded.size() != json.size()) {
            logger.warning("Registered " + loaded.size() + " of " + json.size() + " advancements");
        }
        long modelled = json.values().stream().filter(entry -> entry.contains("\"minecraft:item_model\"")
            || entry.contains("\"minecraft:custom_model_data\"")).count();
        logger.info(modelled + " of " + json.size() + " icons use a custom model");
        return loaded.size();
    }

    private static List<AchievementDefinition> parentsFirst(AchievementCatalog catalog, List<AchievementDefinition> defs) {
        Map<String, AchievementDefinition> ordered = new LinkedHashMap<>();
        for (AchievementDefinition def : defs) {
            visit(catalog, def, ordered);
        }
        return List.copyOf(ordered.values());
    }

    private static void visit(AchievementCatalog catalog, AchievementDefinition def, Map<String, AchievementDefinition> ordered) {
        if (ordered.containsKey(def.fullId())) {
            return;
        }
        if (def.parent() != null) {
            catalog.byFullId(def.parent()).ifPresent(parent -> visit(catalog, parent, ordered));
        }
        ordered.put(def.fullId(), def);
    }
}
