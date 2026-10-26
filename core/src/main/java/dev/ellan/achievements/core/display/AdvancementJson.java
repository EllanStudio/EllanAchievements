package dev.ellan.achievements.core.display;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.TabDefinition;
import dev.ellan.achievements.core.progress.StepMath;

import java.util.Locale;

/** Builds vanilla advancement JSON (spec §6.2). All criteria are {@code minecraft:impossible}; the plugin grants them. */
public final class AdvancementJson {
    public static final String NAMESPACE = "ellanachievements";
    public static final String ROOT_CRITERION = "granted";
    private static final Gson GSON = new Gson();

    private AdvancementJson() {
    }

    /** Key path (without namespace) of a tab's root advancement. */
    public static String rootKey(String tabId) {
        return tabId + "/root";
    }

    public static String stepCriterion(int step) {
        return "step_" + step;
    }

    public static String tab(TabDefinition tab, ResolvedIcon icon) {
        JsonObject display = display(icon, tab.title(), tab.description(), "task", false, false, false);
        display.addProperty("background", tab.background());
        JsonObject root = new JsonObject();
        root.add("display", display);
        JsonObject criteria = new JsonObject();
        criteria.add(ROOT_CRITERION, impossible());
        root.add("criteria", criteria);
        JsonArray requirements = new JsonArray();
        requirements.add(single(ROOT_CRITERION));
        root.add("requirements", requirements);
        return GSON.toJson(root);
    }

    /**
     * @param effectiveParent full ID of the registered parent, or null to hang under the tab root
     *                        (see {@code AchievementCatalog.effectiveParent})
     */
    public static String achievement(AchievementDefinition def, String effectiveParent, ResolvedIcon icon) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", NAMESPACE + ":" + (effectiveParent == null ? rootKey(def.tabId()) : effectiveParent));
        root.add("display", display(icon, def.title(), def.description(), def.frame().name().toLowerCase(Locale.ROOT),
            def.toast(), def.announce(), def.hidden()));
        JsonObject criteria = new JsonObject();
        JsonArray requirements = new JsonArray();
        for (int step = 1; step <= StepMath.steps(def.goal()); step++) {
            criteria.add(stepCriterion(step), impossible());
            requirements.add(single(stepCriterion(step)));
        }
        root.add("criteria", criteria);
        root.add("requirements", requirements);
        return GSON.toJson(root);
    }

    private static JsonObject display(ResolvedIcon icon, String title, String description, String frame,
                                      boolean toast, boolean announce, boolean hidden) {
        JsonObject display = new JsonObject();
        JsonObject iconJson = new JsonObject();
        iconJson.addProperty("id", icon.itemId());
        if (!icon.components().isEmpty()) {
            iconJson.add("components", GSON.toJsonTree(icon.components()));
        }
        display.add("icon", iconJson);
        display.add("title", text(title));
        display.add("description", text(description));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", toast);
        display.addProperty("announce_to_chat", announce);
        display.addProperty("hidden", hidden);
        return display;
    }

    private static JsonObject text(String value) {
        JsonObject text = new JsonObject();
        text.addProperty("text", value);
        return text;
    }

    private static JsonObject impossible() {
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", "minecraft:impossible");
        return criterion;
    }

    private static JsonArray single(String criterion) {
        JsonArray array = new JsonArray();
        array.add(criterion);
        return array;
    }
}
