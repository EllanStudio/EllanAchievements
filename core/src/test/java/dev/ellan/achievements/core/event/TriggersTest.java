package dev.ellan.achievements.core.event;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriggersTest {
    @Test
    void fishCatchAttributes() {
        assertThat(Triggers.attributes("fish_catch"))
            .containsExactlyInAnyOrder("loot", "group", "type", "size", "new_record");
        assertThat(Triggers.isNumeric("fish_catch", "size")).isTrue();
        assertThat(Triggers.isNumeric("fish_catch", "loot")).isFalse();
    }

    @Test
    void allSpecTriggersExist() {
        assertThat(Triggers.all()).containsExactlyInAnyOrder(
            "fish_catch", "crop_plant", "crop_harvest", "crop_quality", "dish_cook", "cooking_stir_fry",
            "cooking_grind", "cooking_sickle", "cooking_action", "tavern_press", "tavern_shake", "tavern_tap",
            "tavern_drink", "brew_obtain", "brew_drink", "item_obtain", "item_kill");
    }

    @Test
    void unknownTriggerHasNoAttributes() {
        assertThat(Triggers.attributes("nope")).isEmpty();
    }

    @Test
    void eventRejectsZeroAmount() {
        assertThatThrownBy(() -> new GameplayEvent(UUID.randomUUID(), "fish_catch", Map.of(), 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
