package dev.ellan.achievements.paper.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReflectiveEventsTest {
    public record Sample(String location, int count) {
    }

    @Test
    void callInvokesPublicAccessor() {
        Sample sample = new Sample("world", 3);
        assertThat(ReflectiveEvents.call(sample, "location")).isEqualTo("world");
        assertThat(ReflectiveEvents.call(sample, "count")).isEqualTo(3);
    }

    @Test
    void callOnMissingMethodFails() {
        assertThatThrownBy(() -> ReflectiveEvents.call(new Sample("w", 1), "nope"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingClassReturnsFalse() {
        assertThat(ReflectiveEvents.register(null, "no.such.Event", event -> { })).isFalse();
    }
}
