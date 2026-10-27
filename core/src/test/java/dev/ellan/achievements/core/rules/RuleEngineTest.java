package dev.ellan.achievements.core.rules;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.core.progress.PlayerProgress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {
    static final UUID PLAYER = UUID.randomUUID();
    static final Predicate<String> NONE_DONE = id -> false;

    static final String FISH_100 = """
          fish:
            title: x
            description: x
            icon: { item: "minecraft:stone" }
            trigger: fish_catch
            goal: 100
        """;

    static GameplayEvent fish(long amount) {
        return new GameplayEvent(PLAYER, "fish_catch", Map.of("loot", "tuna_fish"), amount);
    }

    @Test
    void totalCountsAmount() {
        RuleEngine engine = new RuleEngine(Catalogs.of(FISH_100));
        PlayerProgress progress = PlayerProgress.empty();
        engine.apply(progress, fish(3), NONE_DONE);
        engine.apply(progress, fish(2), NONE_DONE);
        assertThat(progress.counter("t/fish")).isEqualTo(5);
    }

    @Test
    void amountCrossingGoalCompletesOnce() {
        RuleEngine engine = new RuleEngine(Catalogs.of(FISH_100));
        PlayerProgress progress = PlayerProgress.empty();
        progress.setCounter("t/fish", 98);

        List<ProgressChange> changes = engine.apply(progress, fish(5), NONE_DONE);

        assertThat(changes).containsExactly(new ProgressChange("t/fish", 98, 103, 100));
        assertThat(engine.apply(progress, fish(1), id -> true)).isEmpty();
        assertThat(progress.counter("t/fish")).isEqualTo(103);
    }

    @Test
    void completedAchievementIsSkipped() {
        RuleEngine engine = new RuleEngine(Catalogs.of(FISH_100));
        PlayerProgress progress = PlayerProgress.empty();
        progress.setCounter("t/fish", 10);
        assertThat(engine.apply(progress, fish(1), "t/fish"::equals)).isEmpty();
        assertThat(progress.counter("t/fish")).isEqualTo(10);
    }

    @Test
    void uniqueCountsDistinctValues() {
        RuleEngine engine = new RuleEngine(Catalogs.of("""
              dishes:
                title: x
                description: x
                icon: { item: "minecraft:stone" }
                trigger: dish_cook
                goal: 10
                count: unique
                unique-by: dish
            """));
        PlayerProgress progress = PlayerProgress.empty();
        for (String dish : List.of("K:A", "k:a", "k:b")) {
            engine.apply(progress, new GameplayEvent(PLAYER, "dish_cook", Map.of("dish", dish)), NONE_DONE);
        }
        assertThat(progress.counter("t/dishes")).isEqualTo(2);
        assertThat(progress.uniques("t/dishes")).containsExactlyInAnyOrder("k:a", "k:b");
    }

    @Test
    void repeatedUniqueValueReportsNoChange() {
        RuleEngine engine = new RuleEngine(Catalogs.of("""
              dishes:
                title: x
                description: x
                icon: { item: "minecraft:stone" }
                trigger: dish_cook
                goal: 10
                count: unique
                unique-by: dish
            """));
        PlayerProgress progress = PlayerProgress.empty();
        GameplayEvent event = new GameplayEvent(PLAYER, "dish_cook", Map.of("dish", "k:a"));
        assertThat(engine.apply(progress, event, NONE_DONE)).hasSize(1);
        assertThat(engine.apply(progress, event, NONE_DONE)).isEmpty();
    }

    @Test
    void uniqueGroupsCountsGroups() {
        RuleEngine engine = new RuleEngine(Catalogs.of("""
              outfit:
                title: x
                description: x
                icon: { item: "minecraft:stone" }
                trigger: item_obtain
                goal: 2
                count: unique
                unique-by: item
                unique-groups:
                  hat: ["k:straw_hat", "k:straw_hat_flower"]
                  boots: ["k:farmer_boots"]
            """));
        PlayerProgress progress = PlayerProgress.empty();
        for (String item : List.of("k:straw_hat", "K:Straw_Hat_Flower", "k:farmer_boots", "k:other")) {
            engine.apply(progress, new GameplayEvent(PLAYER, "item_obtain", Map.of("item", item)), NONE_DONE);
        }
        assertThat(progress.counter("t/outfit")).isEqualTo(2);
        assertThat(progress.uniques("t/outfit")).containsExactlyInAnyOrder("hat", "boots");
    }

    @Test
    void uniqueMissingAttributeNotCounted() {
        RuleEngine engine = new RuleEngine(Catalogs.of("""
              dishes:
                title: x
                description: x
                icon: { item: "minecraft:stone" }
                trigger: dish_cook
                goal: 10
                count: unique
                unique-by: dish
            """));
        PlayerProgress progress = PlayerProgress.empty();
        engine.apply(progress, new GameplayEvent(PLAYER, "dish_cook", Map.of("station", "pot")), NONE_DONE);
        assertThat(progress.counter("t/dishes")).isZero();
    }

    @Test
    void filterMismatchIgnored() {
        RuleEngine engine = new RuleEngine(Catalogs.of("""
              big:
                title: x
                description: x
                icon: { item: "minecraft:stone" }
                trigger: fish_catch
                filter: { size: ">=50", loot: ["tuna_fish"] }
            """));
        PlayerProgress progress = PlayerProgress.empty();
        engine.apply(progress, new GameplayEvent(PLAYER, "fish_catch", Map.of("loot", "tuna_fish", "size", 49.0)), NONE_DONE);
        engine.apply(progress, new GameplayEvent(PLAYER, "fish_catch", Map.of("loot", "tuna_fish")), NONE_DONE);
        engine.apply(progress, new GameplayEvent(PLAYER, "fish_catch", Map.of("loot", "carp", "size", 60.0)), NONE_DONE);
        assertThat(progress.counter("t/big")).isZero();
        engine.apply(progress, new GameplayEvent(PLAYER, "fish_catch", Map.of("loot", "TUNA_FISH", "size", 60.0)), NONE_DONE);
        assertThat(progress.counter("t/big")).isEqualTo(1);
    }

    @Test
    void otherTriggerIgnored() {
        RuleEngine engine = new RuleEngine(Catalogs.of(FISH_100));
        PlayerProgress progress = PlayerProgress.empty();
        assertThat(engine.apply(progress, new GameplayEvent(PLAYER, "crop_plant", Map.of("crop", "x")), NONE_DONE)).isEmpty();
    }

    @Test
    void disabledIgnored() {
        RuleEngine engine = new RuleEngine(Catalogs.of(FISH_100 + "    disabled: true\n"));
        PlayerProgress progress = PlayerProgress.empty();
        assertThat(engine.apply(progress, fish(1), NONE_DONE)).isEmpty();
    }
}
