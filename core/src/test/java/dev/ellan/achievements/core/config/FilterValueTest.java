package dev.ellan.achievements.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterValueTest {
    @Test
    void anyOfIsCaseInsensitive() {
        FilterValue value = FilterValue.parse("fish_catch", "group", List.of("River", "lake"));
        assertThat(value.test("river")).isTrue();
        assertThat(value.test(List.of("ocean", "LAKE"))).isTrue();
        assertThat(value.test("ocean")).isFalse();
        assertThat(value.test(List.of("ocean"))).isFalse();
    }

    @Test
    void singleStringIsAnyOfOne() {
        FilterValue value = FilterValue.parse("fish_catch", "loot", "Tuna_Fish");
        assertThat(value.test("tuna_fish")).isTrue();
    }

    @Test
    void booleanAttributeComparesAsString() {
        FilterValue value = FilterValue.parse("fish_catch", "new_record", true);
        assertThat(value.test(Boolean.TRUE)).isTrue();
        assertThat(value.test(Boolean.FALSE)).isFalse();
    }

    @Test
    void compareOperators() {
        assertThat(FilterValue.parse("fish_catch", "size", ">=50").test(50.0)).isTrue();
        assertThat(FilterValue.parse("fish_catch", "size", ">=50").test(49.9)).isFalse();
        assertThat(FilterValue.parse("fish_catch", "size", ">50").test(50.0)).isFalse();
        assertThat(FilterValue.parse("fish_catch", "size", "<3").test(2)).isTrue();
        assertThat(FilterValue.parse("fish_catch", "size", "<=3").test(3)).isTrue();
        assertThat(FilterValue.parse("fish_catch", "size", "==3").test(3.0f)).isTrue();
        assertThat(FilterValue.parse("fish_catch", "size", 7).test(7)).isTrue();
    }

    @Test
    void numericAttributeWithNonNumberNeverMatches() {
        assertThat(FilterValue.parse("fish_catch", "size", ">=1").test("big")).isFalse();
    }

    @Test
    void badComparisonRejected() {
        assertThatThrownBy(() -> FilterValue.parse("fish_catch", "size", ">>5"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FilterValue.parse("fish_catch", "size", "big"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingAttributeNeverMatches() {
        assertThat(FilterValue.parse("fish_catch", "loot", "x").test(null)).isFalse();
        assertThat(FilterValue.parse("fish_catch", "size", ">=0").test(null)).isFalse();
    }
}
