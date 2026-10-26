package dev.ellan.achievements.paper.adapter.cooking;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CookingTranslatorTest {
    static final UUID PLAYER = UUID.randomUUID();

    @Test
    void dish() {
        GameplayEvent event = CookingTranslator.dish(PLAYER, "KaleidoscopeCookery:Mapo_Tofu", CookingTranslator.POT);
        assertThat(event.trigger()).isEqualTo("dish_cook");
        assertThat(event.attributes()).containsEntry("dish", "kaleidoscopecookery:mapo_tofu").containsEntry("station", "pot");
    }

    @Test
    void stationNames() {
        assertThat(CookingTranslator.POT).isEqualTo("pot");
        assertThat(CookingTranslator.STOCKPOT).isEqualTo("stockpot");
        assertThat(CookingTranslator.STEAMER).isEqualTo("steamer");
        assertThat(CookingTranslator.SHAWARMA).isEqualTo("shawarma");
    }

    @Test
    void stirFry() {
        GameplayEvent event = CookingTranslator.stirFry(PLAYER, 3);
        assertThat(event.trigger()).isEqualTo("cooking_stir_fry");
        assertThat(event.attributes()).containsEntry("count", 3);
    }

    @Test
    void grind() {
        assertThat(CookingTranslator.grind(PLAYER, "KaleidoscopeCookery:Flour").attributes())
            .containsEntry("product", "kaleidoscopecookery:flour");
        assertThat(CookingTranslator.grind(PLAYER, "x").trigger()).isEqualTo("cooking_grind");
    }

    @Test
    void sickle() {
        GameplayEvent event = CookingTranslator.sickle(PLAYER, "minecraft:wheat");
        assertThat(event.trigger()).isEqualTo("cooking_sickle");
        assertThat(event.attributes()).containsEntry("block", "minecraft:wheat");
    }

    @Test
    void action() {
        GameplayEvent event = CookingTranslator.action(PLAYER, "Stir_Fry_In_Pot");
        assertThat(event.trigger()).isEqualTo("cooking_action");
        assertThat(event.attributes()).containsEntry("action", "stir_fry_in_pot");
    }
}
