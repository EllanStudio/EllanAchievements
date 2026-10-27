package dev.ellan.achievements.paper.adapter.fishing;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FishingTranslatorTest {
    static final UUID PLAYER = UUID.randomUUID();

    @Test
    void successMapsEveryAttribute() {
        GameplayEvent event = FishingTranslator.catchEvent(PLAYER, true, "tuna_fish",
            new String[]{"ocean", "fishing_pack_water"}, "ITEM", 52.5f, true, 2).orElseThrow();
        assertThat(event.trigger()).isEqualTo("fish_catch");
        assertThat(event.player()).isEqualTo(PLAYER);
        assertThat(event.amount()).isEqualTo(2);
        assertThat(event.attributes()).containsEntry("loot", "tuna_fish")
            .containsEntry("group", List.of("ocean", "fishing_pack_water"))
            .containsEntry("type", "item")
            .containsEntry("size", 52.5)
            .containsEntry("new_record", true);
    }

    @Test
    void failureIsIgnored() {
        assertThat(FishingTranslator.catchEvent(PLAYER, false, "tuna_fish", null, "ITEM", null, null, 1)).isEmpty();
    }

    @Test
    void missingSizeAndGroups() {
        GameplayEvent event = FishingTranslator.catchEvent(PLAYER, true, "rubbish", null, "ITEM", null, null, 1).orElseThrow();
        assertThat(event.attributes()).doesNotContainKey("size").containsEntry("group", List.of())
            .containsEntry("new_record", false);
    }

    @Test
    void zeroAmountCountsAsOne() {
        assertThat(FishingTranslator.catchEvent(PLAYER, true, "x", null, "ITEM", null, null, 0).orElseThrow().amount())
            .isEqualTo(1);
    }
}
