package dev.ellan.achievements.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {
    static final class FakeIds implements ExternalIds {
        Set<String> items = Set.of("minecraft:fishing_rod", "customfishing:tuna_fish", "customfishing:golden_carp",
            "kaleidoscopecookery:fish_rice", "minecraft:diamond");
        Optional<Set<String>> loots = Optional.of(Set.of("tuna_fish", "golden_carp"));
        Set<String> unavailableTriggers = Set.of();

        @Override public boolean itemExists(String id) { return items.contains(id); }
        @Override public Optional<Boolean> fishingLoot(String id) { return loots.map(set -> set.contains(id)); }
        @Override public Optional<Boolean> crop(String id) { return Optional.empty(); }
        @Override public Optional<Boolean> brewRecipe(String id) { return Optional.empty(); }
        @Override public boolean triggerAvailable(String trigger) { return !unavailableTriggers.contains(trigger); }
    }

    static final String TAB = """
        tab:
          id: fishing
          title: "垂钓"
          description: "与鱼相伴的日子"
          icon: { item: "customfishing:golden_carp" }
          background: "minecraft:block/prismarine"
        """;

    static LoadResult load(String achievements) {
        return load(achievements, new FakeIds());
    }

    static LoadResult load(String achievements, ExternalIds ids) {
        SortedMap<String, String> files = new TreeMap<>();
        files.put("fishing.yml", TAB + "achievements:\n" + achievements);
        return ConfigLoader.load(files, ids);
    }

    static final String FIRST = """
          first_catch:
            title: "第一口咬钩"
            description: "钓上任意一条鱼"
            icon: { item: "minecraft:fishing_rod" }
            trigger: fish_catch
        """;

    @Test
    void parsesFullAchievement() {
        LoadResult result = load(FIRST + """
              hundred_fish:
                parent: first_catch
                title: "百尾渔夫"
                description: "累计钓上 100 条鱼"
                icon: { item: "customfishing:tuna_fish" }
                frame: task
                trigger: fish_catch
                filter:
                  loot: ["tuna_fish", "golden_carp"]
                  size: ">=50"
                goal: 100
                rewards:
                  exp: 50
                  coins: 500
                  items: ["kaleidoscopecookery:fish_rice*2", "minecraft:diamond*1"]
                  commands: ["say {player}"]
            """);
        assertThat(result.issues()).isEmpty();
        AchievementDefinition def = result.catalog().byFullId("fishing/hundred_fish").orElseThrow();
        assertThat(def.tabId()).isEqualTo("fishing");
        assertThat(def.parent()).isEqualTo("fishing/first_catch");
        assertThat(def.goal()).isEqualTo(100);
        assertThat(def.frame()).isEqualTo(Frame.TASK);
        assertThat(def.filter()).containsOnlyKeys("loot", "size");
        assertThat(def.rewards().exp()).isEqualTo(50);
        assertThat(def.rewards().coins()).isEqualTo(500);
        assertThat(def.rewards().items()).containsExactly(
            new ItemReward("kaleidoscopecookery:fish_rice", 2), new ItemReward("minecraft:diamond", 1));
        assertThat(def.rewards().commands()).containsExactly("say {player}");
        TabDefinition tab = result.catalog().tabs().getFirst();
        assertThat(tab.title()).isEqualTo("垂钓");
        assertThat(tab.icon().item()).isEqualTo("customfishing:golden_carp");
        assertThat(tab.achievements()).extracting(AchievementDefinition::id).containsExactly("first_catch", "hundred_fish");
    }

    @Test
    void defaults() {
        AchievementDefinition def = load(FIRST).catalog().byFullId("fishing/first_catch").orElseThrow();
        assertThat(def.parent()).isNull();
        assertThat(def.frame()).isEqualTo(Frame.TASK);
        assertThat(def.hidden()).isFalse();
        assertThat(def.announce()).isTrue();
        assertThat(def.toast()).isTrue();
        assertThat(def.goal()).isEqualTo(1);
        assertThat(def.count()).isEqualTo(CountMode.TOTAL);
        assertThat(def.disabled()).isFalse();
        assertThat(def.rewards()).isEqualTo(RewardSpec.NONE);
    }

    @Test
    void uniqueGroupsParsed() {
        LoadResult result = load("""
              set:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: item_obtain
                goal: 2
                count: unique
                unique-by: item
                unique-groups:
                  hat: ["a:hat", "a:hat_flower"]
                  boots: ["a:boots"]
            """);
        AchievementDefinition def = result.catalog().byFullId("fishing/set").orElseThrow();
        assertThat(def.count()).isEqualTo(CountMode.UNIQUE);
        assertThat(def.uniqueBy()).isEqualTo("item");
        assertThat(def.uniqueGroups()).containsEntry("hat", List.of("a:hat", "a:hat_flower"));
    }

    private static void assertSingleError(LoadResult result, String achievement, String field) {
        assertThat(result.issues()).filteredOn(issue -> issue.severity() == Severity.ERROR)
            .singleElement()
            .satisfies(issue -> {
                assertThat(issue.file()).isEqualTo("fishing.yml");
                assertThat(issue.achievement()).isEqualTo(achievement);
                assertThat(issue.field()).isEqualTo(field);
            });
        assertThat(result.catalog().byFullId("fishing/" + achievement)).isEmpty();
    }

    @Test
    void duplicateIdIsError() {
        // YAML maps cannot repeat keys, so duplicates are detected across files of the same tab.
        SortedMap<String, String> files = new TreeMap<>();
        files.put("a.yml", TAB + "achievements:\n" + FIRST);
        files.put("b.yml", TAB + "achievements:\n" + FIRST);
        LoadResult result = ConfigLoader.load(files, new FakeIds());
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.file()).isEqualTo("b.yml");
        });
        assertThat(result.catalog().tabs()).hasSize(1);
    }

    @Test
    void missingParentIsError() {
        assertSingleError(load("""
              orphan:
                parent: nobody
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """), "orphan", "parent");
    }

    @Test
    void crossTabParentIsError() {
        assertSingleError(load("""
              cross:
                parent: "farming/x"
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """), "cross", "parent");
    }

    @Test
    void parentCycleIsError() {
        LoadResult result = load("""
              a:
                parent: b
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
              b:
                parent: a
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """);
        assertThat(result.issues()).filteredOn(issue -> issue.severity() == Severity.ERROR).hasSize(2)
            .allSatisfy(issue -> assertThat(issue.field()).isEqualTo("parent"));
        assertThat(result.catalog().byFullId("fishing/a")).isEmpty();
        assertThat(result.catalog().byFullId("fishing/b")).isEmpty();
    }

    @Test
    void childOfSkippedIsSkipped() {
        LoadResult result = load("""
              bad:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: no_such_trigger
              child:
                parent: bad
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """);
        assertThat(result.catalog().byFullId("fishing/child")).isEmpty();
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.achievement()).isEqualTo("child");
            assertThat(issue.field()).isEqualTo("parent");
        });
    }

    @Test
    void reservedRootIdIsError() {
        assertSingleError(load("""
              root:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """), "root", "id");
    }

    @Test
    void unknownTriggerIsError() {
        assertSingleError(load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: no_such_trigger
            """), "x", "trigger");
    }

    @Test
    void missingTitleIsError() {
        assertSingleError(load("""
              x:
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """), "x", "title");
    }

    @Test
    void badFrameIsError() {
        assertSingleError(load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                frame: huge
                trigger: fish_catch
            """), "x", "frame");
    }

    @Test
    void filterAttributeNotAllowedIsError() {
        assertSingleError(load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                filter: { crop: "tomato" }
            """), "x", "filter.crop");
    }

    @Test
    void badComparisonIsError() {
        assertSingleError(load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                filter: { size: ">>5" }
            """), "x", "filter.size");
    }

    @Test
    void uniqueWithoutUniqueByIsError() {
        assertSingleError(load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                count: unique
                goal: 3
            """), "x", "unique-by");
    }

    @Test
    void invalidRewardItemIsError() {
        assertSingleError(load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                rewards: { items: ["minecraft:diamond*0"] }
            """), "x", "rewards.items");
    }

    @Test
    void unknownItemIsWarningAndStillLoaded() {
        LoadResult result = load("""
              x:
                title: t
                description: d
                icon: { item: "nowhere:thing" }
                trigger: fish_catch
            """);
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.field()).isEqualTo("icon.item");
        });
        assertThat(result.catalog().byFullId("fishing/x")).isPresent();
    }

    @Test
    void unknownFishLootIsWarning() {
        LoadResult result = load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                filter: { loot: "kraken" }
            """);
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.field()).isEqualTo("filter.loot");
        });
    }

    @Test
    void pluginAbsentSkipsExternalCheck() {
        FakeIds ids = new FakeIds();
        ids.loots = Optional.empty();
        LoadResult result = load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                filter: { loot: "kraken" }
            """, ids);
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void unavailableTriggerIsWarning() {
        FakeIds ids = new FakeIds();
        ids.unavailableTriggers = Set.of("fish_catch");
        LoadResult result = load(FIRST, ids);
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.field()).isEqualTo("trigger");
        });
        assertThat(result.catalog().byFullId("fishing/first_catch")).isPresent();
    }

    @Test
    void disabledIsExcludedFromTrigger() {
        LoadResult result = load("""
              x:
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                disabled: true
            """);
        assertThat(result.catalog().byTrigger("fish_catch")).isEmpty();
        assertThat(result.catalog().byFullId("fishing/x")).isPresent();
    }

    @Test
    void childOfDisabledReattachesToEnabledAncestor() {
        LoadResult result = load(FIRST + """
              middle:
                parent: first_catch
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
                disabled: true
              leaf:
                parent: middle
                title: t
                description: d
                icon: { item: "minecraft:diamond" }
                trigger: fish_catch
            """);
        AchievementCatalog catalog = result.catalog();
        assertThat(catalog.effectiveParent(catalog.byFullId("fishing/leaf").orElseThrow()))
            .isEqualTo("fishing/first_catch");
        assertThat(catalog.effectiveParent(catalog.byFullId("fishing/first_catch").orElseThrow())).isNull();
    }

    @Test
    void yamlSyntaxErrorReportsFile() {
        SortedMap<String, String> files = new TreeMap<>();
        files.put("broken.yml", "tab: [unclosed");
        LoadResult result = ConfigLoader.load(files, new FakeIds());
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.ERROR);
            assertThat(issue.file()).isEqualTo("broken.yml");
        });
        assertThat(result.catalog().tabs()).isEmpty();
    }

    @Test
    void hashIsStableAndOrderIndependentOfMapInsertion() {
        SortedMap<String, String> first = new TreeMap<>();
        first.put("a.yml", TAB);
        first.put("b.yml", TAB.replace("fishing", "farming"));
        SortedMap<String, String> second = new TreeMap<>();
        second.put("b.yml", TAB.replace("fishing", "farming"));
        second.put("a.yml", TAB);
        String hash = ConfigLoader.load(first, new FakeIds()).catalog().sha256();
        assertThat(hash).hasSize(64).isEqualTo(ConfigLoader.load(second, new FakeIds()).catalog().sha256());
        second.put("a.yml", TAB + "\n");
        assertThat(ConfigLoader.load(second, new FakeIds()).catalog().sha256()).isNotEqualTo(hash);
    }
}
