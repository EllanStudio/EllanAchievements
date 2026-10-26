# EllanAchievements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Paper plugin that turns cooking, tavern, brewing, fishing and farming gameplay into config-driven vanilla advancements shown in the L screen, plus the two small event additions in the cooking fork and the tavern plugin.

**Architecture:** Gradle multi-project. `core` is pure Java (config, rules, progress, advancement JSON) with no Bukkit imports and full unit tests. `paper` wires core to Paper: PDC storage, advancement registration, rewards, commands, and one isolated adapter per gameplay plugin. Adapters for plugins without a published Maven artifact (cooking, tavern, TheBrewingProject) bind events by class name; the rest compile against Maven.

**Tech Stack:** Java 25, Paper API `26.2.build.135-stable`, Gradle 9.4.0 (Kotlin DSL), Shadow `com.gradleup.shadow` 9.6.1, SnakeYAML (Paper-provided), Gson (Paper-provided), JUnit `junit-bom:6.0.3`, AssertJ `3.27.7`.

**Spec:** `docs/superpowers/specs/2026-10-26-ellan-achievements-design.md`

## Global Constraints

- Advancement namespace: `ellanachievements`; full ID `<tab.id>/<achievement id>`; key `ellanachievements:<tab.id>/<achievement id>`; tab root key `ellanachievements:<tab.id>/root` (achievement id `root` is reserved).
- PDC key `ellanachievements:progress`, JSON with `"v": 1`; item marker PDC key `ellanachievements:brew_counted`.
- Step criteria: `steps = min(goal, 100)`; awarded steps `floor(count * steps / goal)`; criteria named `step_1` … `step_<steps>`, all `minecraft:impossible`.
- Currency: ExcellentEconomy `ellan_coin`.
- Admin command `/ea`, permission `ellanachievements.admin` (default op); audit log `plugins/EllanAchievements/audit.log`.
- Third-party plugins (TheBrewingProject, CustomFishing, CustomCrops) are never modified. Cooking fork gets exactly one new event class and one call. Tavern changes live only under `src/paper` and `src/paperTest`.
- All gameplay plugins, CraftEngine and ExcellentEconomy are `softdepend`.
- No NMS, no UltimateAdvancementAPI. Registration uses `Bukkit.getUnsafe().loadAdvancement` / `removeAdvancement`.
- `core` must not import `org.bukkit`, `io.papermc`, or any plugin API (enforced by the `core` Gradle project having no such dependency).
- Commits authored as `TypeThe0ry <104051227+TypeThe0ry@users.noreply.github.com>`, each message ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **One event crosses several steps or the whole goal at once** (CustomFishing `amount` 5 when 98/100): progress jumps to every owed step and the advancement completes exactly once → test `RuleEngineTest.amountCrossingGoalCompletesOnce` (Task 4) and `StepMathTest` (Task 3).
2. **Goal raised after a player already completed**: the advancement stays completed, the counter keeps counting, no revoke on join → test `RuleEngineTest.completedAchievementIsSkipped` (Task 4) and alignment rule "only add" in Task 7.
3. **Filter on an attribute the event does not carry** (e.g. `size: ">=50"` but CustomFishing gave no size; `unique-by` attribute missing): event does not match / is not counted, no exception → `FilterValueTest.missingAttributeNeverMatches` (Task 2).
4. **Corrupt or future-version progress JSON** (hand-edited PDC, a newer plugin version on another server): never overwritten, player treated as empty this session, admins warned → `ProgressCodecTest.unknownVersionIsUnreadable` (Task 3) and the read-only flag in Task 7.
5. **Server switch right after progress** (count on survival, then join redstone): progress and completion arrive via SparrowSync, the reward is not paid twice → `rewarded` checked in Task 8; covered by the acceptance checklist in Task 16.

---

## File Structure

```
EllanAchievements/
  settings.gradle.kts  build.gradle.kts  gradle.properties  gradlew*  gradle/wrapper/*
  .github/workflows/build.yml
  README.md
  core/build.gradle.kts
  core/src/main/java/dev/ellan/achievements/core/
    event/GameplayEvent.java            # record
    event/Triggers.java                 # trigger → allowed attributes
    config/IconSpec.java  RewardSpec.java  ItemReward.java  Frame.java  CountMode.java
    config/FilterValue.java             # list-of-strings or numeric comparison
    config/AchievementDefinition.java  TabDefinition.java  AchievementCatalog.java
    config/ExternalIds.java  Issue.java  LoadResult.java  ConfigLoader.java
    rules/FilterMatcher.java  RuleEngine.java  ProgressChange.java
    progress/PlayerProgress.java  ProgressCodec.java  DecodeResult.java  StepMath.java
    display/ResolvedIcon.java  AdvancementJson.java
    util/ErrorThrottle.java
  core/src/test/java/dev/ellan/achievements/core/...   # one *Test per class above
  paper/build.gradle.kts
  paper/src/main/resources/plugin.yml
  paper/src/main/java/dev/ellan/achievements/paper/
    EllanAchievementsPlugin.java        # wiring only
    storage/ProgressStore.java
    icon/IconResolver.java  icon/ItemIds.java  icon/CraftEngineHook.java
    display/AdvancementRegistrar.java  display/ProgressService.java
    reward/RewardService.java  reward/RewardListener.java
    command/EaCommand.java  command/AuditLog.java
    adapter/Adapter.java  adapter/AdapterRegistry.java  adapter/ReflectiveEvents.java
    adapter/generic/GenericAdapter.java
    adapter/fishing/FishingAdapter.java  adapter/fishing/FishingTranslator.java
    adapter/farming/FarmingAdapter.java  adapter/farming/FarmingTranslator.java
    adapter/cooking/CookingAdapter.java  adapter/cooking/CookingTranslator.java
    adapter/tavern/TavernAdapter.java
    adapter/brewing/BrewingAdapter.java  adapter/brewing/BrewingTranslator.java
    integration/PluginExternalIds.java  # ExternalIds backed by live plugins
  paper/src/test/java/...                # tests for the *Translator classes only
  config/achievements/cooking.yml tavern.yml brewing.yml fishing.yml farming.yml
  tools/migrate_kaleidoscope.py  tools/kaleidoscope.json  tools/test_migrate.py
```

Translators are pure static functions over strings/numbers so they can be unit-tested without a server; the `*Adapter` classes are thin Bukkit listeners that extract values and call them.

---

### Task 1: Scaffold build, CI and the gameplay event core

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, wrapper files, `core/build.gradle.kts`, `paper/build.gradle.kts`, `paper/src/main/resources/plugin.yml`, `.github/workflows/build.yml`
- Create: `core/src/main/java/dev/ellan/achievements/core/event/GameplayEvent.java`, `.../event/Triggers.java`
- Test: `core/src/test/java/dev/ellan/achievements/core/event/TriggersTest.java`

**Interfaces:**
- Produces: `record GameplayEvent(UUID player, String trigger, Map<String, Object> attributes, long amount)` (compact constructor copies attributes into an unmodifiable map, rejects `amount < 1`); `final class Triggers { static Set<String> all(); static Set<String> attributes(String trigger); static boolean isNumeric(String trigger, String attribute); }`

- [ ] **Step 1: Install toolchain locally** — `apt-get install -y openjdk-25-jdk-headless`; download Gradle 9.4.0 once and run `gradle wrapper --gradle-version 9.4.0` in the repo. Expected: `./gradlew --version` prints Gradle 9.4.0 and JVM 25.
- [ ] **Step 2: Write build files.** Root mirrors Bots4VeloRental (`allprojects` repositories: mavenCentral, `https://repo.papermc.io/repository/maven-public/`, `https://repo.nightexpressdev.com/releases`, `https://repo.momirealms.net/releases/`; Java 25 toolchain, `-parameters`, UTF-8, JUnit platform). `version` from `pluginVersion` property, default `1.0.0`. `core`: `compileOnly`/`testImplementation` `org.yaml:snakeyaml:2.5` and `com.google.code.gson:gson:2.13.2`, JUnit + AssertJ. `paper`: `implementation(project(":core"))`, `compileOnly` `io.papermc.paper:paper-api:26.2.build.135-stable`, `net.momirealms:craft-engine-core:26.9.1`, `net.momirealms:craft-engine-bukkit:26.9.1`, `net.momirealms:custom-fishing:2.3.24`, `net.momirealms:custom-crops:3.6.52`, `su.nightexpress.excellenteconomy:ExcellentEconomy:2.8.0`; shadow jar named `EllanAchievements-<version>.jar`, no relocation needed (core has no shaded deps). `plugin.yml`: `name: EllanAchievements`, `main: dev.ellan.achievements.paper.EllanAchievementsPlugin`, `api-version: "26.2"`, `softdepend: [CraftEngine, ExcellentEconomy, CustomFishing, CustomCrops, TheBrewingProject, KaleidoscopeCookeryPlugin, KaleidoscopeTavern]`, command `ea` with permission `ellanachievements.admin` (default op). CI: checkout, setup-java temurin 25 with gradle cache, `./gradlew clean check shadowJar`, upload `paper/build/libs/EllanAchievements-*.jar` and `config/achievements/*.yml`.
- [ ] **Step 3: Write the failing test** `TriggersTest`:

```java
@Test void fishCatchAttributes() {
    assertThat(Triggers.attributes("fish_catch")).containsExactlyInAnyOrder("loot", "group", "type", "size", "new_record");
    assertThat(Triggers.isNumeric("fish_catch", "size")).isTrue();
}
@Test void allSpecTriggersExist() {
    assertThat(Triggers.all()).containsExactlyInAnyOrder(
        "fish_catch", "crop_plant", "crop_harvest", "crop_quality", "dish_cook", "cooking_stir_fry",
        "cooking_grind", "cooking_sickle", "cooking_action", "tavern_press", "tavern_shake", "tavern_tap",
        "tavern_drink", "brew_obtain", "brew_drink", "item_obtain", "item_kill");
}
@Test void unknownTriggerHasNoAttributes() { assertThat(Triggers.attributes("nope")).isEmpty(); }
@Test void eventRejectsZeroAmount() {
    assertThatThrownBy(() -> new GameplayEvent(UUID.randomUUID(), "fish_catch", Map.of(), 0)).isInstanceOf(IllegalArgumentException.class);
}
```

- [ ] **Step 4: Run** `./gradlew :core:test` — Expected: FAIL (classes missing).
- [ ] **Step 5: Implement** `GameplayEvent` and `Triggers` with the attribute table from spec §4.1 verbatim. Numeric attributes: `fish_catch.size`, `crop_quality.quality`, `cooking_stir_fry.count`, `brew_obtain.score`, `brew_drink.score`. `new_record` is boolean (compared as string `"true"`/`"false"`).
- [ ] **Step 6: Run** `./gradlew check` — Expected: BUILD SUCCESSFUL, 4 tests pass.
- [ ] **Step 7: Commit** `chore: scaffold Gradle build and CI; feat(core): gameplay event and trigger table`, push, confirm the GitHub Actions run is green.

---

### Task 2: Config model, loader and validation

**Files:**
- Create: `core/.../config/{IconSpec,RewardSpec,ItemReward,Frame,CountMode,FilterValue,AchievementDefinition,TabDefinition,AchievementCatalog,ExternalIds,Issue,LoadResult,ConfigLoader}.java`
- Test: `core/src/test/java/dev/ellan/achievements/core/config/ConfigLoaderTest.java`, `FilterValueTest.java`

**Interfaces:**
- Consumes: `Triggers` (Task 1).
- Produces:
  - `record IconSpec(String item)`; `record ItemReward(String itemId, int amount)` with `static ItemReward parse(String)` (`"id*2"`, default amount 1); `record RewardSpec(int exp, long coins, List<ItemReward> items, List<String> commands)` with `static RewardSpec NONE`.
  - `enum Frame { TASK, GOAL, CHALLENGE }`, `enum CountMode { TOTAL, UNIQUE }`.
  - `sealed interface FilterValue { boolean test(Object attribute); static FilterValue parse(String trigger, String attribute, Object yaml); }` — implementations `AnyOf(Set<String> lowerCased)` and `Compare(String op, double value)`; `test(null)` is always false.
  - `record AchievementDefinition(String tabId, String id, String fullId, String parent, String title, String description, IconSpec icon, Frame frame, boolean hidden, boolean announce, boolean toast, String trigger, Map<String, FilterValue> filter, long goal, CountMode count, String uniqueBy, Map<String, List<String>> uniqueGroups, RewardSpec rewards, boolean disabled)`; `parent` is a full ID or `null` (= tab root).
  - `record TabDefinition(String id, String title, String description, IconSpec icon, String background, List<AchievementDefinition> achievements)`.
  - `final class AchievementCatalog { List<TabDefinition> tabs(); Optional<AchievementDefinition> byFullId(String); List<AchievementDefinition> byTrigger(String); /* excludes disabled */ String sha256(); }`
  - `interface ExternalIds { boolean itemExists(String id); Optional<Boolean> fishingLoot(String id); Optional<Boolean> crop(String id); Optional<Boolean> brewRecipe(String id); boolean triggerAvailable(String trigger); }` (`Optional.empty()` = plugin not loaded).
  - `enum Severity { ERROR, WARNING }`; `record Issue(Severity severity, String file, String achievement, String field, String message)`.
  - `record LoadResult(AchievementCatalog catalog, List<Issue> issues)`.
  - `final class ConfigLoader { static LoadResult load(SortedMap<String, String> files /* file name → YAML text */, ExternalIds ids); }` — `sha256` is over the files concatenated in name order.

- [ ] **Step 1: Write failing tests** in `ConfigLoaderTest` (use a `FakeIds` that knows `minecraft:fishing_rod`, `customfishing:tuna_fish`, loot `tuna_fish`, triggers all available):
  - `parsesFullAchievement` — the §5.2 example parses to `fullId "fishing/hundred_fish"`, `parent "fishing/first_catch"`, `goal 100`, `frame TASK`, rewards `coins 500`, `items [ItemReward("kaleidoscopecookery:fish_rice",2), ItemReward("minecraft:diamond",1)]`.
  - `defaults` — omitted fields give `frame TASK, hidden false, announce true, toast true, goal 1, count TOTAL, disabled false, RewardSpec.NONE`.
  - `duplicateIdIsError`, `missingParentIsError`, `crossTabParentIsError`, `parentCycleIsError` (a→b→a both skipped), `childOfSkippedIsSkipped`, `reservedRootIdIsError`, `unknownTriggerIsError`, `filterAttributeNotAllowedIsError` (`fish_catch` with `filter.crop`), `badComparisonIsError` (`size: ">>5"`), `uniqueWithoutUniqueByIsError` — each asserts one `Issue` with severity `ERROR`, the right `file`/`achievement`/`field`, and that the achievement is absent from the catalog.
  - `unknownItemIsWarningAndStillLoaded`, `unknownFishLootIsWarning`, `pluginAbsentSkipsExternalCheck` (`fishingLoot` returns empty → no issue), `unavailableTriggerIsWarning`.
  - `disabledIsExcludedFromTrigger` — `byTrigger("fish_catch")` omits it, `byFullId` still finds it.
  - `hashIsStableAndOrderIndependentOfMapInsertion`.
- [ ] **Step 2: Write failing tests** in `FilterValueTest`: `anyOfIsCaseInsensitive` (`["River","lake"]` matches `"river"` and a `List.of("ocean","LAKE")` attribute), `compareOperators` (`">=50"` true for 50.0, false for 49.9; `"<3"`, `">"`, `"<="`, `"=="`), `missingAttributeNeverMatches` (`test(null)` false for both kinds).
- [ ] **Step 3: Run** `./gradlew :core:test` — Expected: FAIL.
- [ ] **Step 4: Implement.** YAML parsed with SnakeYAML `SafeConstructor`. A list-valued attribute (e.g. `group`) matches `AnyOf` if any element matches. Validation order and severities exactly as spec §5.4 (1–3 ERROR → skip, plus their descendants; 4–6 WARNING). Achievement order within a tab follows YAML order.
- [ ] **Step 5: Run** `./gradlew :core:test` — Expected: PASS.
- [ ] **Step 6: Commit** `feat(core): achievement config model, loader and validation`.

---

### Task 3: Player progress, codec and step math

**Files:**
- Create: `core/.../progress/{PlayerProgress,ProgressCodec,DecodeResult,StepMath}.java`, `core/.../util/ErrorThrottle.java`
- Test: `ProgressCodecTest.java`, `StepMathTest.java`, `ErrorThrottleTest.java`

**Interfaces:**
- Produces:
  - `final class PlayerProgress { long counter(String id); void setCounter(String id, long v); Set<String> uniques(String id); /* mutable, created on demand */ boolean rewarded(String id); void markRewarded(String id); void clear(String id); /* counter + uniques only */ static PlayerProgress empty(); }`
  - `sealed interface DecodeResult { record Ok(PlayerProgress progress) ; record Unreadable(String raw, String reason) }`
  - `final class ProgressCodec { static String encode(PlayerProgress p); static DecodeResult decode(String rawOrNull); }` — `null`/blank → `Ok(empty)`.
  - `final class StepMath { static int steps(long goal); static int awardedSteps(long count, long goal); }`
  - `final class ErrorThrottle { ErrorThrottle(long windowMillis); boolean shouldLogStack(String key, long nowMillis); }`

- [ ] **Step 1: Write failing tests:**
  - `ProgressCodecTest.roundTrip` (counters, uniques, rewarded survive), `encodesVersion1` (`"v":1` present, shape exactly as spec §6.1), `nullIsEmpty`, `corruptJsonIsUnreadable` (`"{"` → `Unreadable` with raw kept), `unknownVersionIsUnreadable` (`{"v":2}`).
  - `StepMathTest`: `steps(1)=1`, `steps(100)=100`, `steps(250)=100`; `awardedSteps(37,100)=37`, `awardedSteps(249,250)=99`, `awardedSteps(250,250)=100`, `awardedSteps(999,250)=100` (capped), `awardedSteps(0,10)=0`.
  - `ErrorThrottleTest`: first call true, second within 60 000 ms false, after the window true, different keys independent.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement** with Gson.
- [ ] **Step 4: Run** `./gradlew :core:test` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): player progress codec, step math and error throttle`.

---

### Task 4: Rule engine

**Files:**
- Create: `core/.../rules/{FilterMatcher,RuleEngine,ProgressChange}.java`
- Test: `core/.../rules/RuleEngineTest.java`, `FilterMatcherTest.java`

**Interfaces:**
- Consumes: `AchievementCatalog`, `AchievementDefinition`, `FilterValue` (Task 2), `GameplayEvent` (Task 1), `PlayerProgress` (Task 3).
- Produces: `final class FilterMatcher { static boolean matches(AchievementDefinition def, GameplayEvent e); }`; `record ProgressChange(String fullId, long before, long after, long goal)`; `final class RuleEngine { RuleEngine(AchievementCatalog c); List<ProgressChange> apply(PlayerProgress p, GameplayEvent e, Predicate<String> isCompleted); }` — mutates `p`, returns only achievements whose count changed.

- [ ] **Step 1: Write failing tests** in `RuleEngineTest`:
  - `totalCountsAmount` — two `fish_catch` events with amount 3 and 2 → counter 5.
  - `amountCrossingGoalCompletesOnce` — goal 100, counter 98, event amount 5 → one change `before 98, after 103, goal 100`; a further event still changes the counter (completion is decided by the display layer, not here) only if `isCompleted` returns false; with `isCompleted` true → no change.
  - `completedAchievementIsSkipped` — `isCompleted` true → empty result, counter untouched.
  - `uniqueCountsDistinctValues` — `dish_cook` with dish A, A, B → count 2, uniques `{a,b}` (lower-cased).
  - `uniqueGroupsCountsGroups` — `unique-groups {hat:[straw_hat, straw_hat_flower], boots:[farmer_boots]}`, events straw_hat, straw_hat_flower, farmer_boots, other_item → count 2.
  - `uniqueMissingAttributeNotCounted`.
  - `filterMismatchIgnored`, `otherTriggerIgnored`, `disabledIgnored`.
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*RuleEngineTest' --tests '*FilterMatcherTest'` — Expected: FAIL.
- [ ] **Step 3: Implement.** For `UNIQUE`, the counter is always set to the size of the unique set (so it self-heals).
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): rule engine with total, unique and grouped counting`.

---

### Task 5: Advancement JSON

**Files:**
- Create: `core/.../display/{ResolvedIcon,AdvancementJson}.java`
- Test: `AdvancementJsonTest.java`

**Interfaces:**
- Consumes: `TabDefinition`, `AchievementDefinition` (Task 2), `StepMath` (Task 3).
- Produces: `record ResolvedIcon(String itemId, Map<String, Object> components)`; `final class AdvancementJson { static String rootKey(String tabId); /* "<tab>/root" */ static String tab(TabDefinition t, ResolvedIcon icon); static String achievement(AchievementDefinition a, ResolvedIcon icon); }`

- [ ] **Step 1: Write failing tests** (parse the output with Gson and assert fields):
  - `tabRoot` — `display.title` = title, `display.background` = background, `display.show_toast false`, `announce_to_chat false`, one criterion `granted` of `minecraft:impossible`, no `parent`.
  - `achievementWithGoal100` — `parent` = `ellanachievements:fishing/first_catch`, 100 criteria `step_1..step_100` each `minecraft:impossible`, `requirements` = 100 single-element arrays, `display.frame "task"`, `hidden`, `show_toast`, `announce_to_chat` copied.
  - `achievementWithoutParentHangsOnRoot` — `parent` = `ellanachievements:fishing/root`.
  - `goal5000UsesHundredSteps`.
  - `ceIconComponents` — icon `ResolvedIcon("minecraft:paper", Map.of("minecraft:item_model","kaleidoscopecookery:pot"))` → `display.icon = {"id":"minecraft:paper","components":{"minecraft:item_model":"kaleidoscopecookery:pot"}}`.
  - `vanillaIconHasNoComponents`.
  - Titles/descriptions are written as plain text components (`{"text": "..."}`) so Chinese renders without a lang file.
- [ ] **Step 2: Run** — Expected: FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(core): vanilla advancement JSON generation`.

---

### Task 6: Paper plugin shell, adapter registry, storage, icons and registration

**Files:**
- Create: `paper/.../EllanAchievementsPlugin.java`, `adapter/{Adapter,AdapterRegistry,ReflectiveEvents}.java`, `storage/ProgressStore.java`, `icon/{IconResolver,ItemIds,CraftEngineHook}.java`, `display/AdvancementRegistrar.java`, `integration/PluginExternalIds.java`
- Test: `paper/src/test/java/.../adapter/ReflectiveEventsTest.java`

**Interfaces:**
- Consumes: all of `core`.
- Produces:
  - `interface Adapter { String id(); /* "fishing" etc. */ String requiredPlugin(); /* null = always */ Set<String> triggers(); void enable(EllanAchievementsPlugin plugin, Consumer<GameplayEvent> sink) throws Throwable; }`
  - `final class AdapterRegistry { void enableAll(List<Adapter>); Map<String, String> status(); /* adapter id → "enabled" | "not installed" | "incompatible: <reason>" */ boolean triggerAvailable(String trigger); }` — catches `Throwable` per adapter.
  - `final class ReflectiveEvents { static boolean register(Plugin p, String eventClassName, Consumer<Event> handler); static Object call(Object target, String method); }` — returns false if the class is absent; `call` uses `MethodHandles.publicLookup()` and caches handles.
  - `final class ProgressStore { PlayerProgress load(Player p); /* Unreadable → logs raw, returns empty, marks player read-only, warns online admins */ void save(Player p, PlayerProgress pr); /* no-op when read-only */ boolean readOnly(UUID id); }`
  - `final class ItemIds { static String of(ItemStack); /* CE id if CraftEngine present and item is custom, else minecraft key */ static Optional<ItemStack> build(String id, int amount); }`
  - `final class IconResolver { ResolvedIcon resolve(IconSpec spec); /* unknown → minecraft:barrier + warning */ }` — CE items: build via `CraftEngineHook`, read `DataComponentTypes.ITEM_MODEL` and `CUSTOM_MODEL_DATA` from the stack.
  - `final class AdvancementRegistrar { void registerAll(AchievementCatalog c); /* remove every key in our namespace, load tabs then achievements in parent-first order, then resend to online players */ }`
  - `final class PluginExternalIds implements ExternalIds` — items via `ItemIds.build`, CF loot via `BukkitCustomFishingPlugin.getInstance().getLootManager()`, CC crops via `BukkitCustomCropsPlugin.getInstance().getRegistry()`, TBP recipes reflectively via the `TheBrewingProjectApi` service's `getRecipeRegistry()`; each wrapped so a missing plugin yields `Optional.empty()`.
  - `EllanAchievementsPlugin`: on enable copy bundled `achievements/*.yml` if folder missing, load config, log issues as a table, log `config sha256=<hash>`, register advancements, enable adapters, log adapter status table.

- [ ] **Step 1: Write failing test** `ReflectiveEventsTest.callInvokesPublicAccessor` (a test record with `location()` returns the value) and `missingClassReturnsFalse` (register with `"no.such.Event"` and a `null` plugin returns `false` without touching Bukkit).
- [ ] **Step 2: Run** `./gradlew :paper:test` — Expected: FAIL.
- [ ] **Step 3: Implement** the files above. Use `Bukkit.getPluginManager().registerEvent(eventClass, listener, EventPriority.MONITOR, executor, plugin, true)` in `ReflectiveEvents`; handler exceptions go through `ErrorThrottle` and are never rethrown.
- [ ] **Step 4: Run** `./gradlew check shadowJar` — Expected: PASS, jar built.
- [ ] **Step 5: Smoke test on the test server** with one hand-written `config/achievements/fishing.yml` (tab + one achievement, CE icon `customfishing:tuna_fish`): start, then check log shows the hash, no issues, adapter table; `/advancement grant <you> only ellanachievements:fishing/root` and open L → tab with the tuna icon visible.
- [ ] **Step 6: Commit** `feat(paper): plugin shell, adapter registry, PDC storage, icons and advancement registration`.

---

### Task 7: Progress service and join alignment

**Files:**
- Create: `paper/.../display/ProgressService.java`

**Interfaces:**
- Consumes: `RuleEngine`, `StepMath`, `ProgressStore`, `AdvancementJson.rootKey`.
- Produces: `final class ProgressService implements Consumer<GameplayEvent> { void accept(GameplayEvent e); /* main thread; offline players ignored */ void align(Player p); /* grant tab roots; for each enabled achievement award missing steps up to awardedSteps(count, goal); never revoke */ void setCounter(Player p, String fullId, long value); void reset(Player p, String fullId); /* clear counter+uniques, revoke all criteria, keep rewarded */ }`

- [ ] **Step 1: Implement.** `accept`: load progress → `RuleEngine.apply(..., id -> player.getAdvancementProgress(adv).isDone())` → for each change award criteria `step_1..step_awardedSteps(after, goal)` not yet awarded → save. Read-only players: compute nothing, log once. `align` runs on `PlayerJoinEvent` one tick after join (after SparrowSync has applied data — if SparrowSync exposes a "data loaded" event, prefer it; otherwise 40-tick delay) and after `/ea reload` for everyone online.
- [ ] **Step 2: Test on the test server** (run after Task 9 lands, using `/ea set` to drive counts): set `goal: 3` on the smoke-test achievement; verify: progress bar 1/3, 2/3, then toast + chat once; lower goal to 2 + `/ea reload` → already-complete player unchanged, a player at 2/3 completes on next join.
- [ ] **Step 3: Commit** `feat(paper): progress service with step awarding and join alignment`.

---

### Task 8: Rewards

**Files:**
- Create: `paper/.../reward/{RewardService,RewardListener}.java`
- Test: `core/.../config/ItemRewardTest.java` (parsing lives in core)

**Interfaces:**
- Consumes: `RewardSpec`, `ItemReward`, `ProgressStore`, `ItemIds.build`.
- Produces: `final class RewardListener implements Listener` handling `PlayerAdvancementDoneEvent` for namespace `ellanachievements` (ignores `*/root`); `final class RewardService { void grant(Player p, AchievementDefinition a); }`.

- [ ] **Step 1: Write failing tests** `ItemRewardTest`: `"minecraft:diamond*3"` → amount 3; `"kaleidoscopecookery:fish_rice"` → amount 1; `"x*0"` and `"x*abc"` rejected with `IllegalArgumentException` (and ConfigLoader turns that into an ERROR issue on field `rewards.items` — add `invalidRewardItemIsError` to `ConfigLoaderTest`).
- [ ] **Step 2: Run** — Expected: FAIL; implement; run — Expected: PASS.
- [ ] **Step 3: Implement `RewardService`/`RewardListener`:** if `progress.rewarded(id)` → return; else mark rewarded and save **first**, then give exp (`giveExp`), coins via ExcellentEconomy service (`depositAsync(uuid, "ellan_coin", coins, OperationContext.custom("EllanAchievements"))`; skipped with a log line if the service is absent), items (`addItem`, leftovers `dropItemNaturally` at the player), commands via console with `{player}` replaced. Read-only players get no reward and a log line.
- [ ] **Step 4: Test on the test server:** complete an achievement with exp+coins+CE item+command rewards → each paid once; `/advancement revoke` then `grant` again → nothing paid the second time.
- [ ] **Step 5: Commit** `feat: idempotent achievement rewards`.

---

### Task 9: Admin command and audit log

**Files:**
- Create: `paper/.../command/{EaCommand,AuditLog}.java`

**Interfaces:**
- Consumes: `AdapterRegistry.status()`, `AchievementCatalog`, `ProgressService.setCounter/reset`, `ProgressStore`, `EllanAchievementsPlugin.reload()` (reload = load config, log issues, `registerAll`, `align` online players; returns `LoadResult`).
- Produces: `/ea status|reload|progress <player> [achievement]|set <player> <achievement> <count>|reset <player> <achievement>` with tab completion of subcommands, online players and full IDs; `AuditLog.append(String operator, String player, String achievement, String before, String after)` writing one tab-separated line with ISO timestamp.

- [ ] **Step 1: Implement** with messages in Chinese (e.g. `已重载：载入 %d 个成就，跳过 %d 个，警告 %d 条。配置哈希 %s`). `progress` without achievement lists every non-zero counter and completion/rewarded flags.
- [ ] **Step 2: Test on the test server:** each subcommand once; check `audit.log` lines for `set` and `reset`; `reset` then completing again pays no reward.
- [ ] **Step 3: Commit** `feat(paper): /ea admin command with audit log`.

---

### Task 10: Fishing adapter

**Files:**
- Create: `paper/.../adapter/fishing/{FishingAdapter,FishingTranslator}.java`
- Test: `paper/src/test/.../adapter/fishing/FishingTranslatorTest.java`

**Interfaces:**
- Produces: `final class FishingTranslator { static Optional<GameplayEvent> catchEvent(UUID player, boolean success, String lootId, String[] groups, String lootType, Float size, Boolean newRecord, int amount); }` → trigger `fish_catch`, attributes `loot`, `group` (List), `type` (lower-case), `size` (Double, omitted when null), `new_record`, amount `max(1, amount)`.

- [ ] **Step 1: Write failing tests:** success maps every attribute; failure → empty; `size null` → attribute absent; `groups null` → empty list; amount 0 → 1.
- [ ] **Step 2: Run** — FAIL; **Step 3: implement** `FishingAdapter` (`requiredPlugin "CustomFishing"`, listens `FishingResultEvent` at MONITOR ignoring cancelled; reads `getLoot().id()`, `lootGroup()`, `type()`, `getContext().arg(ContextKeys.SIZE)`, `arg(ContextKeys.IS_NEW_SIZE_RECORD)`, `getAmount()`); **Step 4: run** — PASS.
- [ ] **Step 5: Test on the test server:** catch fish → `/ea progress` shows counters; a `size: ">=50"` filter only counts big fish.
- [ ] **Step 6: Commit** `feat(adapter): CustomFishing`.

---

### Task 11: Farming adapter

**Files:**
- Create: `paper/.../adapter/farming/{FarmingAdapter,FarmingTranslator}.java`
- Test: `FarmingTranslatorTest.java`

**Interfaces:**
- Produces: `final class FarmingTranslator { static GameplayEvent plant(UUID p, String cropId); static Optional<GameplayEvent> harvest(UUID p, String cropId, boolean byPlayer, boolean finalStage, String seasonOrNull); static Optional<GameplayEvent> quality(UUID p, String itemId, String[] qualityCrops); }` — `quality` attribute = 1-based index of `itemId` in `qualityCrops` (empty if not found).

- [ ] **Step 1: Write failing tests:** harvest not by player → empty; not final stage → empty; season null → attribute absent, else lower-cased; quality index 1..n; unknown item → empty.
- [ ] **Step 2: Run** — FAIL; **Step 3: implement** `FarmingAdapter` (`requiredPlugin "CustomCrops"`): `CropPlantEvent`; `CropBreakEvent` with `entityBreaker() instanceof Player`, final stage = `cropStageItemID()` equals the stage with the highest point in `cropConfig().stages()`; season from the CustomCrops world API for `location().getWorld()` (absent → null); `QualityCropActionEvent` player from `context().holder()` when it is a `Player`, one event per item in `items()` using `ItemIds.of`. **Step 4: run** — PASS.
- [ ] **Step 5: Test on the test server:** plant, harvest mature vs. immature crop, quality crop drop.
- [ ] **Step 6: Commit** `feat(adapter): CustomCrops`.

---

### Task 12: Generic adapter (item_obtain, item_kill)

**Files:**
- Create: `paper/.../adapter/generic/GenericAdapter.java`

**Interfaces:**
- Produces: adapter id `generic`, `requiredPlugin null`, triggers `item_obtain`, `item_kill`. `item_obtain` from `EntityPickupItemEvent` (player), `CraftItemEvent` result, `FurnaceExtractEvent`, and `InventoryClickEvent` where the clicked inventory is not the player's own and the item moves into the player inventory (shift-click, or pickup then the cursor item); attribute `item` = `ItemIds.of(stack)`, amount = stack amount. `item_kill` from `EntityDeathEvent` with `getEntity().getKiller() != null`: `weapon` = `ItemIds.of(mainHand)`, `entity` = entity type key.

- [ ] **Step 1: Implement**, all at MONITOR ignoring cancelled.
- [ ] **Step 2: Test on the test server:** pick up `kaleidoscopecookery:pot` → `/ea progress` shows a matching `item_obtain` counter; kill a mob with `kaleidoscopecookery:iron_kitchen_knife`.
- [ ] **Step 3: Commit** `feat(adapter): generic item obtain and kill`.

---

### Task 13: Cooking — fork event, upstream PR, adapter

**Repos:** `EllanStudio/KaleidoscopeCookery_Plugin` (fork of `Nicoppara/KaleidoscopeCookery_Plugin`) and this repo.

**Files:**
- Fork — Create: `src/main/java/net/kaleidoscope/cookery/api/event/KaleidoscopeGameplayEvent.java`; Modify: `src/main/java/net/kaleidoscope/cookery/api/KaleidoscopeAdvancements.java` (both `recordEvent` overloads).
- This repo — Create: `paper/.../adapter/cooking/{CookingAdapter,CookingTranslator}.java`; Test: `CookingTranslatorTest.java`.

**Interfaces:**
- Fork produces: `public class KaleidoscopeGameplayEvent extends org.bukkit.event.player.PlayerEvent { public KaleidoscopeGameplayEvent(Player player, String action); public String action(); }` (not cancellable, with the standard static `HandlerList`).
- Adapter produces: triggers `dish_cook`, `cooking_stir_fry`, `cooking_grind`, `cooking_sickle`, `cooking_action`; `CookingTranslator.dish(UUID, String dishId, String station)`, `stirFry(UUID, int count)`, `grind(UUID, String productId)`, `sickle(UUID, String blockKey)`, `action(UUID, String action)`.

- [ ] **Step 1 (fork):** branch `feat/gameplay-event` from fork `main`; add the event; in `recordEvent(org.bukkit.entity.Player, String)` call `Bukkit.getPluginManager().callEvent(new KaleidoscopeGameplayEvent(player, eventName))` after the existing logic; the CraftEngine-player overload must reach the same call (it converts to a Bukkit player). Build with the fork's Gradle; expected BUILD SUCCESSFUL.
- [ ] **Step 2 (fork):** open a PR to the fork's `main`, merge it; open a PR from `EllanStudio:feat/gameplay-event` to `Nicoppara/KaleidoscopeCookery_Plugin` titled `feat: fire KaleidoscopeGameplayEvent for every recorded gameplay action` explaining it lets other plugins observe gameplay without UltimateAdvancementAPI. Record the upstream PR URL in this repo's README.
- [ ] **Step 3: Write failing `CookingTranslatorTest`:** each factory sets the trigger and attributes from spec §4.1 (`station` values exactly `pot`, `stockpot`, `steamer`, `shawarma`), IDs lower-cased.
- [ ] **Step 4: Run** — FAIL; **Step 5: implement** `CookingAdapter` (`requiredPlugin "KaleidoscopeCookeryPlugin"`) using `ReflectiveEvents` for `net.kaleidoscope.cookery.api.event.{PotExtractDishEvent, StockpotExtractDishEvent, SteamerBreakFullEvent, ShawarmaExtractEvent, PotStirFryEvent, MillstoneGrindCompleteEvent, SickleHarvestEvent, KaleidoscopeGameplayEvent}`; accessors `getPlayer()`/`player()`, `dish()`, `products()`, `product()`, `count()`, `block()`, `action()`. If `KaleidoscopeGameplayEvent` is absent, mark only `cooking_action` unavailable (status `enabled (cooking_action unavailable: old cooking plugin)`). **Step 6: run** — PASS.
- [ ] **Step 7: Test on the test server** with the rebuilt fork: stir-fry, take a dish out of the pot, light the stove → `cooking_action stir_fry_in_pot`, `dish_cook`, `cooking_action lit_the_stove` counters appear.
- [ ] **Step 8: Commit** (this repo) `feat(adapter): KaleidoscopeCookery`.

---

### Task 14: Tavern — events in the tavern plugin, adapter

**Repos:** `EllanStudio/KaleidoscopeTavern` (read its `AGENTS.md` first and follow it) and this repo.

**Files:**
- Tavern — Create: `src/paper/java/com/github/ysbbbbbb/kaleidoscopetavern/paper/api/event/{TavernPressEvent,TavernShakeEvent,TavernBarrelTapEvent,TavernDrinkEvent}.java`; Modify: `paper/game/pressing/PressingTubService.java` (the branch at ~line 237 that gives the pressed result to the player), `paper/game/station/StationService.java` (the shaker-result and barrel-tap success branches), and the drink-consume path (locate where a tavern drink's effects are applied on consumption under `paper/game/drink` / `paper/item`); Test: `src/paperTest/.../paper/api/event/TavernEventsTest.java`.
- This repo — Create: `paper/.../adapter/tavern/TavernAdapter.java`.

**Interfaces:**
- Tavern produces (all `extends PlayerEvent`, not cancellable): `TavernPressEvent(Player, Location, String recipeId, ItemStack product)`, `TavernShakeEvent(...same)`, `TavernBarrelTapEvent(...same)`, `TavernDrinkEvent(Player, ItemStack drink)`; accessors `location()`, `recipeId()`, `product()`, `drink()`.
- Adapter produces triggers `tavern_press`, `tavern_shake`, `tavern_tap` (`recipe` = `recipeId`, `product` = `ItemIds.of(product)`), `tavern_drink` (`drink` = `ItemIds.of(drink)`).

- [ ] **Step 1 (tavern):** write `TavernEventsTest` asserting that the press, shake, tap and drink success paths each fire exactly one event with the expected recipe ID and product (use the existing `paperTest` fixtures for those services); run the tavern test task — FAIL.
- [ ] **Step 2 (tavern):** add the events and fire them only in the success branches; run tests — PASS; PR to tavern `main`, merge.
- [ ] **Step 3 (this repo):** implement `TavernAdapter` (`requiredPlugin "KaleidoscopeTavern"`) with `ReflectiveEvents` on the four class names under `com.github.ysbbbbbb.kaleidoscopetavern.paper.api.event`.
- [ ] **Step 4: Test on the test server:** press grapes, shake a cocktail, tap a barrel, drink → four counters.
- [ ] **Step 5: Commit** (this repo) `feat(adapter): KaleidoscopeTavern`.

---

### Task 15: Brewing adapter (TheBrewingProject)

**Files:**
- Create: `paper/.../adapter/brewing/{BrewingAdapter,BrewingTranslator}.java`
- Test: `BrewingTranslatorTest.java`

**Interfaces:**
- Produces: `final class BrewingTranslator { static GameplayEvent drink(UUID p, String recipe, String quality, double score); static Optional<GameplayEvent> obtain(UUID p, Set<UUID> brewers, boolean alreadyCounted, String recipe, String quality, double score); }` — `obtain` is empty when `p ∉ brewers` or `alreadyCounted`; `quality` lower-cased (`bad`/`good`/`excellent`).

- [ ] **Step 1: Write failing tests:** not a brewer → empty; already counted → empty; brewer → event with `recipe`, `quality`, `score`; drink always produces an event.
- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implement `BrewingAdapter`** (`requiredPlugin "TheBrewingProject"`), all TBP access reflective so a TBP API change disables only this adapter: get `dev.jsinco.brewery.bukkit.api.TheBrewingProjectApi` from the Bukkit services manager; read a `Brew` from an `ItemStack` through its `getBrewManager()` (confirm the exact method with `javap` against the 3.4.0-beta jar on the server before coding and note it in a comment); recipe = `brew.closestRecipe(api.getRecipeRegistry())` → `getRecipeName()`; quality/score from `brew.score(recipe)` → `brewQuality()`, `score()`; brewers = `brew.getBrewers()`. `brew_drink` from `dev.jsinco.brewery.bukkit.api.event.BrewConsumeEvent` (`getPlayer()`, `getItem()`). `brew_obtain` from the same item-acquisition events as Task 12; after counting, set PDC `ellanachievements:brew_counted` (byte 1) on the stack in place.
- [ ] **Step 4: Run** — PASS.
- [ ] **Step 5: Test on the test server:** brew a beer yourself → obtain counted once; move it between chests → not counted again; a brew made by another player → not counted; drink → `brew_drink`. **Check stacking**: two finished bottles of the same brew, one counted and one not — if they no longer stack and that matters, switch `brew_obtain` to count only when taken from a TBP barrel/distillery inventory and drop the marker (spec §4 fallback); record the decision in the README.
- [ ] **Step 6: Commit** `feat(adapter): TheBrewingProject`.

---

### Task 16: Achievement content — cooking migration and example tabs

**Files:**
- Create: `tools/kaleidoscope.json` (copied from the cooking plugin jar's `advancements/kaleidoscope.json`), `tools/migrate_kaleidoscope.py`, `tools/test_migrate.py`, `config/achievements/{cooking,tavern,brewing,fishing,farming}.yml`, and copy the same files into `paper/src/main/resources/achievements/` via a Gradle `processResources` `from("../config/achievements")`.
- Test: `core/src/test/java/.../config/ShippedConfigTest.java`

**Interfaces:**
- Consumes: `ConfigLoader`, `ExternalIds`.

- [ ] **Step 1: Write failing `tools/test_migrate.py`** (run with `python3 -I -m unittest tools/test_migrate.py`): output has a `tab` with id `cooking`, title `森罗厨房` and the root's icon; exactly 33 achievements; the nitrogen four are absent; every achievement's `parent` equals the source parent mapped to `null` when the source parent is `root`; `dual_chili` has `count: unique`, `unique-by: item`, two groups, `goal: 2`; `farmer_set` has four groups, `goal: 4`, its hat group lists both straw hats; `dark_cuisine` has `goal: 1` and `filter.item` with both items; `oil` uses `trigger: item_kill`; `add_broth` uses `trigger: cooking_action` with `filter.action: put_soup_base_in_stockpot`.
- [ ] **Step 2: Run** — FAIL; **Step 3: implement** the script per spec §7 mapping (title/description/frame/icon/hidden/toast/announce copied; icons are the source `icon` IDs), write `cooking.yml`; **Step 4: run** — PASS.
- [ ] **Step 5: Write the four example tabs** (titles/descriptions in Chinese, CE icons from the IDs below, rewards modest: exp 10–100, coins 50–1000):
  - `fishing.yml` (tab icon `customfishing:gold_fish`): first catch; 100 fish; 1000 fish (`challenge`); catch `tuna_fish_golden_star`; 10 distinct species via `count: unique, unique-by: loot`; a `size: ">=50"` catch; any `group: [void]` catch (hidden); a `new_record: "true"` catch.
  - `farming.yml` (icon `customcrops:` a tomato-like crop item that exists): plant first crop; harvest 100 mature crops; harvest 10 different crops (`unique-by: crop`); harvest `durian`; get a quality-3 crop (`crop_quality`, `quality: ">=3"`); harvest in each season (`unique-by: season`, `goal: 4`).
  - `brewing.yml` (icon a brew-related CE or vanilla barrel): obtain first own brew; obtain an `excellent` brew; obtain 10 different recipes (`unique-by: recipe`); drink 50 brews; obtain `lychee_wine`; obtain `durian_spirit` (hidden).
  - `tavern.yml` (icon `kaleidoscope_tavern:champagne`): first press; first shake; tap `kaleidoscope_tavern:brandy`; shake 5 different cocktails (`unique-by: recipe`); shake `kaleidoscope_tavern:bloody_mary`; drink 20 tavern drinks.
- [ ] **Step 6: Write `ShippedConfigTest.allShippedFilesLoadWithoutErrors`**: loads every file in `../config/achievements` with an `ExternalIds` that answers `true` for every item/loot/crop/recipe and asserts zero `ERROR` issues and 5 tabs; run — PASS. Then on the test server `/ea reload` must show zero warnings for unknown IDs (this is the real ID check); fix any ID it reports.
- [ ] **Step 7: Commit** `feat(content): migrated cooking achievements and example tabs`.

---

### Task 17: README, release and test-server acceptance

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write README** (Chinese): purpose; supported plugins and the adapter status table meaning; config format reference (copy spec §5.2–§5.4); the "never change or reuse IDs" rule; admin commands; deployment order (cooking fork + tavern first, then this plugin, same jar and same `achievements/` on survival, redstone, spawn; compare the logged hash); the upstream cooking PR link; the `brew_counted` decision from Task 15.
- [ ] **Step 2: Acceptance on the test server** (spec §11): complete at least one achievement per tab; L screen shows five tabs with CE icons and progress bars; toast and chat once; switch survival ↔ redstone (or two test instances sharing SparrowSync) and confirm progress and completion match and the reward is not paid again; `/ea status` shows all six adapters enabled.
- [ ] **Step 3: Tag** `v1.0.0`, confirm CI produced the jar, attach it to a GitHub release.
- [ ] **Step 4: Commit** `docs: README and deployment guide`.
