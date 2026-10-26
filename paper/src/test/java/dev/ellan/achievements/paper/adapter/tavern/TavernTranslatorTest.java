package dev.ellan.achievements.paper.adapter.tavern;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TavernTranslatorTest {
    static final UUID PLAYER = UUID.randomUUID();

    @Test
    void production() {
        GameplayEvent event = TavernTranslator.production(PLAYER, "tavern_shake", "Kaleidoscope_Tavern:Bloody_Mary",
            "kaleidoscope_tavern:bloody_mary");
        assertThat(event.trigger()).isEqualTo("tavern_shake");
        assertThat(event.attributes()).containsEntry("recipe", "kaleidoscope_tavern:bloody_mary")
            .containsEntry("product", "kaleidoscope_tavern:bloody_mary");
    }

    @Test
    void drink() {
        GameplayEvent event = TavernTranslator.drink(PLAYER, "kaleidoscope_tavern:champagne");
        assertThat(event.trigger()).isEqualTo("tavern_drink");
        assertThat(event.attributes()).containsEntry("drink", "kaleidoscope_tavern:champagne");
    }
}
