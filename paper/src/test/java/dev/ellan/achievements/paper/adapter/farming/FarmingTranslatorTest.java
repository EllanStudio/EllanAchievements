package dev.ellan.achievements.paper.adapter.farming;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FarmingTranslatorTest {
    static final UUID PLAYER = UUID.randomUUID();
    static final String[] QUALITY = {"customcrops:tomato", "customcrops:tomato_silver_star", "customcrops:tomato_golden_star"};

    @Test
    void plant() {
        GameplayEvent event = FarmingTranslator.plant(PLAYER, "Tomato");
        assertThat(event.trigger()).isEqualTo("crop_plant");
        assertThat(event.attributes()).containsEntry("crop", "tomato");
    }

    @Test
    void harvestByPlayerAtFinalStage() {
        GameplayEvent event = FarmingTranslator.harvest(PLAYER, "tomato", true, true, "SUMMER").orElseThrow();
        assertThat(event.trigger()).isEqualTo("crop_harvest");
        assertThat(event.attributes()).containsEntry("crop", "tomato").containsEntry("season", "summer");
    }

    @Test
    void harvestNotByPlayerIgnored() {
        assertThat(FarmingTranslator.harvest(PLAYER, "tomato", false, true, null)).isEmpty();
    }

    @Test
    void immatureHarvestIgnored() {
        assertThat(FarmingTranslator.harvest(PLAYER, "tomato", true, false, null)).isEmpty();
    }

    @Test
    void seasonAbsentWhenUnknown() {
        assertThat(FarmingTranslator.harvest(PLAYER, "tomato", true, true, null).orElseThrow().attributes())
            .doesNotContainKey("season");
    }

    @Test
    void qualityIsOneBasedIndex() {
        assertThat(FarmingTranslator.quality(PLAYER, "customcrops:tomato", QUALITY).orElseThrow().attributes())
            .containsEntry("quality", 1).containsEntry("item", "customcrops:tomato");
        assertThat(FarmingTranslator.quality(PLAYER, "customcrops:tomato_golden_star", QUALITY).orElseThrow()
            .attributes()).containsEntry("quality", 3);
    }

    @Test
    void qualityOfUnknownItemIgnored() {
        assertThat(FarmingTranslator.quality(PLAYER, "minecraft:dirt", QUALITY)).isEmpty();
        assertThat(FarmingTranslator.quality(PLAYER, "customcrops:tomato", null)).isEmpty();
    }
}
