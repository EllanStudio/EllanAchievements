package dev.ellan.achievements.core.config;

import dev.ellan.achievements.core.event.Triggers;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.function.Function;

/** Parses and validates achievement files (spec §5). Invalid achievements are skipped, never fatal. */
public final class ConfigLoader {
    private static final String RESERVED_ROOT = "root";
    /** Tab and achievement ids become advancement key paths, so they must be valid key characters. */
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("[a-z0-9_.-]+");
    /** Attributes whose values are item IDs and are checked against the server's items. */
    private static final Set<String> ITEM_ATTRIBUTES = Set.of("item", "dish", "weapon", "product", "drink");

    private final ExternalIds ids;
    private final List<Issue> issues = new ArrayList<>();

    private ConfigLoader(ExternalIds ids) {
        this.ids = ids;
    }

    public static LoadResult load(SortedMap<String, String> files, ExternalIds ids) {
        ConfigLoader loader = new ConfigLoader(ids);
        List<TabDefinition> tabs = new ArrayList<>();
        Set<String> tabIds = new HashSet<>();
        files.forEach((file, text) -> loader.loadFile(file, text, tabIds).ifPresent(tabs::add));
        return new LoadResult(new AchievementCatalog(tabs, sha256(files)), List.copyOf(loader.issues));
    }

    private Optional<TabDefinition> loadFile(String file, String text, Set<String> tabIds) {
        Map<String, Object> root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Object parsed = new Yaml(new SafeConstructor(options)).load(text);
            if (!(parsed instanceof Map<?, ?>)) {
                error(file, null, "file", "expected a mapping with 'tab' and 'achievements'");
                return Optional.empty();
            }
            root = cast(parsed);
        } catch (RuntimeException failure) {
            error(file, null, "file", "YAML syntax error: " + firstLine(failure.getMessage()));
            return Optional.empty();
        }

        if (!(root.get("tab") instanceof Map<?, ?> tabYaml)) {
            error(file, null, "tab", "missing 'tab' section");
            return Optional.empty();
        }
        Map<String, Object> tab = cast(tabYaml);
        String tabId = string(tab, "id");
        String tabTitle = string(tab, "title");
        String tabIcon = tab.get("icon") instanceof Map<?, ?> icon ? string(cast(icon), "item") : null;
        if (tabId == null || tabTitle == null || tabIcon == null) {
            error(file, null, tabId == null ? "tab.id" : tabTitle == null ? "tab.title" : "tab.icon.item",
                "required");
            return Optional.empty();
        }
        if (!ID.matcher(tabId).matches()) {
            error(file, null, "tab.id", "must use only a-z 0-9 _ . - (got '" + tabId + "')");
            return Optional.empty();
        }
        if (!tabIds.add(tabId)) {
            error(file, null, "tab.id", "tab '" + tabId + "' is already defined by another file");
            return Optional.empty();
        }
        checkItem(file, null, "tab.icon.item", tabIcon);

        Map<String, Object> achievementsYaml = root.get("achievements") instanceof Map<?, ?> map
            ? cast(map) : Map.of();
        Map<String, AchievementDefinition> parsed = new LinkedHashMap<>();
        Set<String> invalid = new HashSet<>();
        achievementsYaml.forEach((id, value) -> {
            if (!(value instanceof Map<?, ?> body)) {
                error(file, id, "achievement", "expected a mapping");
                invalid.add(id);
                return;
            }
            AchievementDefinition def = parseAchievement(file, tabId, id, cast(body));
            if (def == null) {
                invalid.add(id);
            } else {
                parsed.put(id, def);
            }
        });

        List<AchievementDefinition> valid = new ArrayList<>();
        Map<String, Boolean> resolved = new LinkedHashMap<>();
        for (AchievementDefinition def : parsed.values()) {
            if (resolveParent(file, tabId, def, parsed, invalid, resolved, new HashSet<>())) {
                valid.add(withFullParent(tabId, def));
            }
        }
        for (AchievementDefinition def : valid) {
            warnExternal(file, def);
        }
        return Optional.of(new TabDefinition(tabId, tabTitle, stringOr(tab, "description", ""),
            new IconSpec(tabIcon), stringOr(tab, "background", "minecraft:block/stone"), valid));
    }

    /** Returns null (after recording an error) when the achievement is intrinsically invalid. */
    private AchievementDefinition parseAchievement(String file, String tabId, String id, Map<String, Object> body) {
        if (id.equals(RESERVED_ROOT)) {
            return fail(file, id, "id", "'root' is reserved for the tab itself");
        }
        if (!ID.matcher(id).matches()) {
            return fail(file, id, "id", "must use only a-z 0-9 _ . - (got '" + id + "')");
        }
        for (String field : List.of("title", "description", "trigger")) {
            if (string(body, field) == null) {
                return fail(file, id, field, "required");
            }
        }
        String icon = body.get("icon") instanceof Map<?, ?> iconYaml ? string(cast(iconYaml), "item") : null;
        if (icon == null) {
            return fail(file, id, "icon.item", "required");
        }
        String trigger = string(body, "trigger");
        if (!Triggers.all().contains(trigger)) {
            return fail(file, id, "trigger", "unknown trigger '" + trigger + "'");
        }

        Frame frame = enumValue(body, "frame", Frame.TASK, Frame::valueOf);
        if (frame == null) {
            return fail(file, id, "frame", "must be task, goal or challenge");
        }
        CountMode count = enumValue(body, "count", CountMode.TOTAL, CountMode::valueOf);
        if (count == null) {
            return fail(file, id, "count", "must be total or unique");
        }
        Long goal = number(body, "goal", 1L);
        if (goal == null || goal < 1) {
            return fail(file, id, "goal", "must be a whole number of at least 1");
        }

        Map<String, FilterValue> filter = new LinkedHashMap<>();
        if (body.containsKey("filter")) {
            if (!(body.get("filter") instanceof Map<?, ?> filterYaml)) {
                return fail(file, id, "filter", "expected a mapping");
            }
            for (Map.Entry<String, Object> entry : cast(filterYaml).entrySet()) {
                String attribute = entry.getKey();
                if (!Triggers.attributes(trigger).contains(attribute)) {
                    return fail(file, id, "filter." + attribute,
                        "'" + trigger + "' has no attribute '" + attribute + "'; allowed: "
                            + Triggers.attributes(trigger));
                }
                try {
                    filter.put(attribute, FilterValue.parse(trigger, attribute, entry.getValue()));
                } catch (IllegalArgumentException failure) {
                    return fail(file, id, "filter." + attribute, failure.getMessage());
                }
            }
        }

        String uniqueBy = string(body, "unique-by");
        Map<String, List<String>> uniqueGroups = new LinkedHashMap<>();
        if (count == CountMode.UNIQUE) {
            if (uniqueBy == null) {
                return fail(file, id, "unique-by", "required when count is unique");
            }
            if (!Triggers.attributes(trigger).contains(uniqueBy)) {
                return fail(file, id, "unique-by", "'" + trigger + "' has no attribute '" + uniqueBy + "'");
            }
        }
        if (body.containsKey("unique-groups")) {
            if (count != CountMode.UNIQUE) {
                return fail(file, id, "unique-groups", "only allowed when count is unique");
            }
            if (!(body.get("unique-groups") instanceof Map<?, ?> groupsYaml)) {
                return fail(file, id, "unique-groups", "expected a mapping of group name to values");
            }
            for (Map.Entry<String, Object> entry : cast(groupsYaml).entrySet()) {
                if (!(entry.getValue() instanceof List<?> values) || values.isEmpty()) {
                    return fail(file, id, "unique-groups." + entry.getKey(), "expected a non-empty list");
                }
                uniqueGroups.put(entry.getKey(), values.stream().map(Object::toString).toList());
            }
        }

        RewardSpec rewards;
        try {
            rewards = rewards(body.get("rewards"));
        } catch (RewardException failure) {
            return fail(file, id, failure.field, failure.getMessage());
        }

        String parent = string(body, "parent");
        return new AchievementDefinition(tabId, id, tabId + "/" + id, parent, string(body, "title"),
            string(body, "description"), new IconSpec(icon), frame, bool(body, "hidden", false),
            bool(body, "announce", true), bool(body, "toast", true), trigger, filter, goal, count, uniqueBy,
            uniqueGroups, rewards, bool(body, "disabled", false));
    }

    /**
     * Validates the parent chain. The definition's {@code parent} holds the raw config value here;
     * on success it is normalised to a full ID (the record is replaced in {@code parsed}).
     */
    private boolean resolveParent(String file, String tabId, AchievementDefinition def,
                                  Map<String, AchievementDefinition> parsed, Set<String> invalid,
                                  Map<String, Boolean> resolved, Set<String> visiting) {
        if (resolved.containsKey(def.id())) {
            return resolved.get(def.id());
        }
        String raw = def.parent();
        if (raw == null) {
            resolved.put(def.id(), true);
            return true;
        }
        String local = raw;
        if (raw.contains("/")) {
            if (!raw.startsWith(tabId + "/")) {
                return reject(file, def, resolved, "parent '" + raw + "' is in another tab");
            }
            local = raw.substring(tabId.length() + 1);
        }
        if (invalid.contains(local)) {
            return reject(file, def, resolved, "parent '" + local + "' was skipped because it is invalid");
        }
        AchievementDefinition parent = parsed.get(local);
        if (parent == null) {
            return reject(file, def, resolved, "parent '" + local + "' does not exist");
        }
        if (!visiting.add(def.id())) {
            return reject(file, def, resolved, "parent chain forms a cycle");
        }
        boolean ok = resolveParent(file, tabId, parent, parsed, invalid, resolved, visiting);
        if (resolved.containsKey(def.id())) {
            return resolved.get(def.id());
        }
        if (!ok) {
            return reject(file, def, resolved, "parent '" + local + "' was skipped because it is invalid");
        }
        resolved.put(def.id(), true);
        return true;
    }

    private static AchievementDefinition withFullParent(String tabId, AchievementDefinition def) {
        if (def.parent() == null || def.parent().contains("/")) {
            return def;
        }
        return new AchievementDefinition(def.tabId(), def.id(), def.fullId(), tabId + "/" + def.parent(),
            def.title(), def.description(), def.icon(), def.frame(), def.hidden(), def.announce(), def.toast(),
            def.trigger(), def.filter(), def.goal(), def.count(), def.uniqueBy(), def.uniqueGroups(), def.rewards(),
            def.disabled());
    }

    private boolean reject(String file, AchievementDefinition def, Map<String, Boolean> resolved, String message) {
        if (!resolved.containsKey(def.id())) {
            error(file, def.id(), "parent", message);
            resolved.put(def.id(), false);
        }
        return false;
    }

    private void warnExternal(String file, AchievementDefinition def) {
        checkItem(file, def.id(), "icon.item", def.icon().item());
        for (ItemReward item : def.rewards().items()) {
            checkItem(file, def.id(), "rewards.items", item.itemId());
        }
        def.filter().forEach((attribute, value) -> {
            if (!(value instanceof FilterValue.AnyOf anyOf)) {
                return;
            }
            for (String candidate : anyOf.values()) {
                String field = "filter." + attribute;
                if (ITEM_ATTRIBUTES.contains(attribute)) {
                    checkItem(file, def.id(), field, candidate);
                } else if (attribute.equals("loot")) {
                    checkExternal(file, def.id(), field, candidate, ids.fishingLoot(candidate), "CustomFishing loot");
                } else if (attribute.equals("crop")) {
                    checkExternal(file, def.id(), field, candidate, ids.crop(candidate), "CustomCrops crop");
                } else if (attribute.equals("recipe") && def.trigger().startsWith("brew_")) {
                    checkExternal(file, def.id(), field, candidate, ids.brewRecipe(candidate),
                        "TheBrewingProject recipe");
                }
            }
        });
        if (!ids.triggerAvailable(def.trigger())) {
            warning(file, def.id(), "trigger",
                "'" + def.trigger() + "' is not available on this server, so this achievement cannot progress");
        }
    }

    private void checkItem(String file, String achievement, String field, String id) {
        if (!ids.itemExists(id)) {
            warning(file, achievement, field, "unknown item '" + id + "'");
        }
    }

    private void checkExternal(String file, String achievement, String field, String id,
                               Optional<Boolean> exists, String kind) {
        if (exists.isPresent() && !exists.get()) {
            warning(file, achievement, field, "unknown " + kind + " '" + id + "'");
        }
    }

    private static RewardSpec rewards(Object yaml) {
        if (yaml == null) {
            return RewardSpec.NONE;
        }
        if (!(yaml instanceof Map<?, ?> map)) {
            throw new RewardException("rewards", "expected a mapping");
        }
        Map<String, Object> body = cast(map);
        Long exp = number(body, "exp", 0L);
        if (exp == null || exp < 0 || exp > Integer.MAX_VALUE) {
            throw new RewardException("rewards.exp", "must be a non-negative whole number");
        }
        Long coins = number(body, "coins", 0L);
        if (coins == null || coins < 0) {
            throw new RewardException("rewards.coins", "must be a non-negative whole number");
        }
        List<ItemReward> items = new ArrayList<>();
        for (Object item : list(body, "items", "rewards.items")) {
            try {
                items.add(ItemReward.parse(String.valueOf(item)));
            } catch (IllegalArgumentException failure) {
                throw new RewardException("rewards.items", failure.getMessage());
            }
        }
        List<String> commands = list(body, "commands", "rewards.commands").stream().map(String::valueOf).toList();
        return new RewardSpec(exp.intValue(), coins, items, commands);
    }

    private static List<?> list(Map<String, Object> body, String key, String field) {
        Object value = body.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new RewardException(field, "expected a list");
        }
        return list;
    }

    private static final class RewardException extends RuntimeException {
        private final String field;

        RewardException(String field, String message) {
            super(message);
            this.field = field;
        }
    }

    private AchievementDefinition fail(String file, String achievement, String field, String message) {
        error(file, achievement, field, message);
        return null;
    }

    private void error(String file, String achievement, String field, String message) {
        issues.add(new Issue(Severity.ERROR, file, achievement, field, message));
    }

    private void warning(String file, String achievement, String field, String message) {
        issues.add(new Issue(Severity.WARNING, file, achievement, field, message));
    }

    private static <E extends Enum<E>> E enumValue(Map<String, Object> body, String key, E fallback,
                                                   Function<String, E> valueOf) {
        Object value = body.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return valueOf.apply(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    private static Long number(Map<String, Object> body, String key, Long fallback) {
        Object value = body.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static boolean bool(Map<String, Object> body, String key, boolean fallback) {
        return body.get(key) instanceof Boolean value ? value : fallback;
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String stringOr(Map<String, Object> body, String key, String fallback) {
        String value = string(body, key);
        return value == null ? fallback : value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        ((Map<Object, Object>) map).forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> cast(Object map) {
        return cast((Map<?, ?>) map);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    private static String sha256(SortedMap<String, String> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            files.forEach((name, text) -> {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(text.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
