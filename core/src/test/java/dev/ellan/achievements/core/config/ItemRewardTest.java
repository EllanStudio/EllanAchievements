package dev.ellan.achievements.core.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemRewardTest {
    @Test
    void parsesAmount() {
        assertThat(ItemReward.parse("minecraft:diamond*3")).isEqualTo(new ItemReward("minecraft:diamond", 3));
    }

    @Test
    void amountDefaultsToOne() {
        assertThat(ItemReward.parse("kaleidoscopecookery:fish_rice")).isEqualTo(new ItemReward("kaleidoscopecookery:fish_rice", 1));
    }

    @Test
    void rejectsBadAmounts() {
        assertThatThrownBy(() -> ItemReward.parse("x*0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemReward.parse("x*abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ItemReward.parse("*2")).isInstanceOf(IllegalArgumentException.class);
    }
}
