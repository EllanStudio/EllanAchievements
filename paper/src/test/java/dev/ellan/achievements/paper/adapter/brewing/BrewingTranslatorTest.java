package dev.ellan.achievements.paper.adapter.brewing;

import dev.ellan.achievements.core.event.GameplayEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BrewingTranslatorTest {
    static final UUID PLAYER = UUID.randomUUID();

    @Test
    void notABrewerIsIgnored() {
        assertThat(BrewingTranslator.obtain(PLAYER, Set.of(UUID.randomUUID()), false, "beer", "GOOD", 7.5)).isEmpty();
    }

    @Test
    void alreadyCountedIsIgnored() {
        assertThat(BrewingTranslator.obtain(PLAYER, Set.of(PLAYER), true, "beer", "GOOD", 7.5)).isEmpty();
    }

    @Test
    void brewerObtains() {
        GameplayEvent event = BrewingTranslator.obtain(PLAYER, Set.of(PLAYER), false, "Lychee_Wine", "EXCELLENT", 9.25)
            .orElseThrow();
        assertThat(event.trigger()).isEqualTo("brew_obtain");
        assertThat(event.attributes()).containsEntry("recipe", "lychee_wine").containsEntry("quality", "excellent")
            .containsEntry("score", 9.25);
    }

    @Test
    void drinkAlwaysCounts() {
        GameplayEvent event = BrewingTranslator.drink(PLAYER, "beer", "BAD", 2.0);
        assertThat(event.trigger()).isEqualTo("brew_drink");
        assertThat(event.attributes()).containsEntry("recipe", "beer").containsEntry("quality", "bad");
    }

    @Test
    void unknownRecipeOrQualityStillCountsWithoutThoseAttributes() {
        GameplayEvent event = BrewingTranslator.drink(PLAYER, null, null, 0);
        assertThat(event.attributes()).doesNotContainKeys("recipe", "quality");
    }
}
