package dev.ellan.achievements.core.progress;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Map;
import java.util.Set;

/** The PDC JSON format, version 1 (spec §6.1). */
public final class ProgressCodec {
    private static final int VERSION = 1;

    private ProgressCodec() {
    }

    public static String encode(PlayerProgress progress) {
        JsonObject root = new JsonObject();
        root.addProperty("v", VERSION);
        JsonObject counters = new JsonObject();
        progress.counters.forEach(counters::addProperty);
        root.add("counters", counters);
        JsonObject uniques = new JsonObject();
        progress.uniques.forEach((id, values) -> {
            if (!values.isEmpty()) {
                uniques.add(id, array(values));
            }
        });
        root.add("uniques", uniques);
        root.add("rewarded", array(progress.rewarded));
        return root.toString();
    }

    public static DecodeResult decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return new DecodeResult.Ok(PlayerProgress.empty());
        }
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (!root.has("v") || root.get("v").getAsInt() != VERSION) {
                return new DecodeResult.Unreadable(raw, "unsupported version " + root.get("v"));
            }
            PlayerProgress progress = PlayerProgress.empty();
            for (Map.Entry<String, JsonElement> entry : object(root, "counters").entrySet()) {
                progress.setCounter(entry.getKey(), entry.getValue().getAsLong());
            }
            for (Map.Entry<String, JsonElement> entry : object(root, "uniques").entrySet()) {
                Set<String> values = progress.uniques(entry.getKey());
                entry.getValue().getAsJsonArray().forEach(value -> values.add(value.getAsString()));
            }
            if (root.has("rewarded")) {
                root.getAsJsonArray("rewarded").forEach(value -> progress.markRewarded(value.getAsString()));
            }
            return new DecodeResult.Ok(progress);
        } catch (RuntimeException failure) {
            return new DecodeResult.Unreadable(raw, failure.toString());
        }
    }

    private static JsonObject object(JsonObject root, String key) {
        return root.has(key) ? root.getAsJsonObject(key) : new JsonObject();
    }

    private static JsonArray array(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
