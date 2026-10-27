package dev.ellan.achievements.core.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorThrottleTest {
    @Test
    void throttlesPerKeyWithinWindow() {
        ErrorThrottle throttle = new ErrorThrottle(60_000);
        assertThat(throttle.shouldLogStack("a", 0)).isTrue();
        assertThat(throttle.shouldLogStack("a", 59_999)).isFalse();
        assertThat(throttle.shouldLogStack("b", 1)).isTrue();
        assertThat(throttle.shouldLogStack("a", 60_000)).isTrue();
    }
}
