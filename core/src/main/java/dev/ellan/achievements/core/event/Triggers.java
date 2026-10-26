package dev.ellan.achievements.core.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** The trigger types from the spec (§4.1) and the attributes each one may be filtered on. */
public final class Triggers {
    private record Spec(Set<String> attributes, Set<String> numeric) {
    }

    private static final Map<String, Spec> TRIGGERS = new LinkedHashMap<>();

    static {
        define("fish_catch", Set.of("loot", "group", "type", "size", "new_record"), Set.of("size"));
        define("crop_plant", Set.of("crop"), Set.of());
        define("crop_harvest", Set.of("crop", "season"), Set.of());
        define("crop_quality", Set.of("item", "quality"), Set.of("quality"));
        define("dish_cook", Set.of("dish", "station"), Set.of());
        define("cooking_stir_fry", Set.of("count"), Set.of("count"));
        define("cooking_grind", Set.of("product"), Set.of());
        define("cooking_sickle", Set.of("block"), Set.of());
        define("cooking_action", Set.of("action"), Set.of());
        define("tavern_press", Set.of("recipe", "product"), Set.of());
        define("tavern_shake", Set.of("recipe", "product"), Set.of());
        define("tavern_tap", Set.of("recipe", "product"), Set.of());
        define("tavern_drink", Set.of("drink"), Set.of());
        define("brew_obtain", Set.of("recipe", "quality", "score"), Set.of("score"));
        define("brew_drink", Set.of("recipe", "quality", "score"), Set.of("score"));
        define("item_obtain", Set.of("item"), Set.of());
        define("item_kill", Set.of("weapon", "entity"), Set.of());
    }

    private Triggers() {
    }

    private static void define(String trigger, Set<String> attributes, Set<String> numeric) {
        TRIGGERS.put(trigger, new Spec(attributes, numeric));
    }

    public static Set<String> all() {
        return Set.copyOf(TRIGGERS.keySet());
    }

    public static Set<String> attributes(String trigger) {
        Spec spec = TRIGGERS.get(trigger);
        return spec == null ? Set.of() : spec.attributes();
    }

    public static boolean isNumeric(String trigger, String attribute) {
        Spec spec = TRIGGERS.get(trigger);
        return spec != null && spec.numeric().contains(attribute);
    }
}
