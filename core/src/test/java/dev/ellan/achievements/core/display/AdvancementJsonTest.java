package dev.ellan.achievements.core.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.CountMode;
import dev.ellan.achievements.core.config.Frame;
import dev.ellan.achievements.core.config.IconSpec;
import dev.ellan.achievements.core.config.RewardSpec;
import dev.ellan.achievements.core.config.TabDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancementJsonTest {
    static final ResolvedIcon ROD = new ResolvedIcon("minecraft:fishing_rod", Map.of());

    static AchievementDefinition def(String parent, long goal, Frame frame, boolean hidden, boolean toast, boolean announce) {
        return new AchievementDefinition("fishing", "hundred_fish", "fishing/hundred_fish", parent, "百尾渔夫",
            "累计钓上 100 条鱼", new IconSpec("minecraft:fishing_rod"), frame, hidden, announce, toast, "fish_catch",
            Map.of(), goal, CountMode.TOTAL, null, Map.of(), RewardSpec.NONE, false);
    }

    static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void rootKey() {
        assertThat(AdvancementJson.rootKey("fishing")).isEqualTo("fishing/root");
    }

    @Test
    void tabRoot() {
        TabDefinition tab = new TabDefinition("fishing", "垂钓", "与鱼相伴的日子", new IconSpec("minecraft:fishing_rod"),
            "minecraft:block/prismarine", List.of());
        JsonObject json = parse(AdvancementJson.tab(tab, ROD));
        assertThat(json.has("parent")).isFalse();
        JsonObject display = json.getAsJsonObject("display");
        assertThat(display.getAsJsonObject("title").get("text").getAsString()).isEqualTo("垂钓");
        assertThat(display.getAsJsonObject("description").get("text").getAsString()).isEqualTo("与鱼相伴的日子");
        assertThat(display.get("background").getAsString()).isEqualTo("minecraft:block/prismarine");
        assertThat(display.get("show_toast").getAsBoolean()).isFalse();
        assertThat(display.get("announce_to_chat").getAsBoolean()).isFalse();
        assertThat(json.getAsJsonObject("criteria").getAsJsonObject("granted").get("trigger").getAsString())
            .isEqualTo("minecraft:impossible");
    }

    @Test
    void achievementWithGoal100() {
        JsonObject json = parse(AdvancementJson.achievement(
            def("fishing/first_catch", 100, Frame.GOAL, true, false, false), "fishing/first_catch", ROD));
        assertThat(json.get("parent").getAsString()).isEqualTo("ellanachievements:fishing/first_catch");
        JsonObject criteria = json.getAsJsonObject("criteria");
        assertThat(criteria.keySet()).hasSize(100).contains("step_1", "step_100");
        assertThat(criteria.getAsJsonObject("step_37").get("trigger").getAsString()).isEqualTo("minecraft:impossible");
        JsonArray requirements = json.getAsJsonArray("requirements");
        assertThat(requirements).hasSize(100);
        assertThat(requirements.get(0).getAsJsonArray()).hasSize(1);
        assertThat(requirements.get(0).getAsJsonArray().get(0).getAsString()).isEqualTo("step_1");
        JsonObject display = json.getAsJsonObject("display");
        assertThat(display.get("frame").getAsString()).isEqualTo("goal");
        assertThat(display.get("hidden").getAsBoolean()).isTrue();
        assertThat(display.get("show_toast").getAsBoolean()).isFalse();
        assertThat(display.get("announce_to_chat").getAsBoolean()).isFalse();
        assertThat(display.getAsJsonObject("title").get("text").getAsString()).isEqualTo("百尾渔夫");
        assertThat(display.has("background")).isFalse();
    }

    @Test
    void achievementWithoutParentHangsOnRoot() {
        JsonObject json = parse(AdvancementJson.achievement(def(null, 1, Frame.TASK, false, true, true), null, ROD));
        assertThat(json.get("parent").getAsString()).isEqualTo("ellanachievements:fishing/root");
        assertThat(json.getAsJsonObject("criteria").keySet()).containsExactly("step_1");
    }

    @Test
    void goal5000UsesHundredSteps() {
        JsonObject json = parse(AdvancementJson.achievement(def(null, 5000, Frame.TASK, false, true, true), null, ROD));
        assertThat(json.getAsJsonObject("criteria").keySet()).hasSize(100);
    }

    @Test
    void ceIconComponents() {
        ResolvedIcon icon = new ResolvedIcon("minecraft:paper", Map.of("minecraft:item_model", "kaleidoscopecookery:pot"));
        JsonObject iconJson = parse(AdvancementJson.achievement(def(null, 1, Frame.TASK, false, true, true), null, icon))
            .getAsJsonObject("display").getAsJsonObject("icon");
        assertThat(iconJson.get("id").getAsString()).isEqualTo("minecraft:paper");
        assertThat(iconJson.getAsJsonObject("components").get("minecraft:item_model").getAsString())
            .isEqualTo("kaleidoscopecookery:pot");
    }

    @Test
    void vanillaIconHasNoComponents() {
        JsonObject iconJson = parse(AdvancementJson.achievement(def(null, 1, Frame.TASK, false, true, true), null, ROD))
            .getAsJsonObject("display").getAsJsonObject("icon");
        assertThat(iconJson.get("id").getAsString()).isEqualTo("minecraft:fishing_rod");
        assertThat(iconJson.has("components")).isFalse();
    }
}
