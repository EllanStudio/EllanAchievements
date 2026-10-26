package dev.ellan.achievements.core.progress;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressCodecTest {
    @Test
    void roundTrip() {
        PlayerProgress progress = PlayerProgress.empty();
        progress.setCounter("fishing/hundred_fish", 37);
        progress.uniques("cooking/ten_dishes").add("kaleidoscopecookery:mapo_tofu");
        progress.markRewarded("fishing/first_catch");

        DecodeResult result = ProgressCodec.decode(ProgressCodec.encode(progress));

        assertThat(result).isInstanceOf(DecodeResult.Ok.class);
        PlayerProgress decoded = ((DecodeResult.Ok) result).progress();
        assertThat(decoded.counter("fishing/hundred_fish")).isEqualTo(37);
        assertThat(decoded.uniques("cooking/ten_dishes")).containsExactly("kaleidoscopecookery:mapo_tofu");
        assertThat(decoded.rewarded("fishing/first_catch")).isTrue();
        assertThat(decoded.rewarded("fishing/hundred_fish")).isFalse();
    }

    @Test
    void encodesVersion1() {
        PlayerProgress progress = PlayerProgress.empty();
        progress.setCounter("a/b", 2);
        JsonObject json = JsonParser.parseString(ProgressCodec.encode(progress)).getAsJsonObject();
        assertThat(json.get("v").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonObject("counters").get("a/b").getAsLong()).isEqualTo(2);
        assertThat(json.has("uniques")).isTrue();
        assertThat(json.has("rewarded")).isTrue();
    }

    @Test
    void nullIsEmpty() {
        assertThat(ProgressCodec.decode(null)).isInstanceOf(DecodeResult.Ok.class);
        assertThat(ProgressCodec.decode("  ")).isInstanceOf(DecodeResult.Ok.class);
    }

    @Test
    void corruptJsonIsUnreadable() {
        DecodeResult result = ProgressCodec.decode("{");
        assertThat(result).isInstanceOf(DecodeResult.Unreadable.class);
        assertThat(((DecodeResult.Unreadable) result).raw()).isEqualTo("{");
    }

    @Test
    void unknownVersionIsUnreadable() {
        assertThat(ProgressCodec.decode("{\"v\":2}")).isInstanceOf(DecodeResult.Unreadable.class);
    }

    @Test
    void clearKeepsRewarded() {
        PlayerProgress progress = PlayerProgress.empty();
        progress.setCounter("a/b", 5);
        progress.uniques("a/b").add("x");
        progress.markRewarded("a/b");
        progress.clear("a/b");
        assertThat(progress.counter("a/b")).isZero();
        assertThat(progress.uniques("a/b")).isEmpty();
        assertThat(progress.rewarded("a/b")).isTrue();
    }
}
