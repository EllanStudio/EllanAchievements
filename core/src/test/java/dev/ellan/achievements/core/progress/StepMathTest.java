package dev.ellan.achievements.core.progress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StepMathTest {
    @Test
    void steps() {
        assertThat(StepMath.steps(1)).isEqualTo(1);
        assertThat(StepMath.steps(100)).isEqualTo(100);
        assertThat(StepMath.steps(250)).isEqualTo(100);
    }

    @Test
    void awardedSteps() {
        assertThat(StepMath.awardedSteps(37, 100)).isEqualTo(37);
        assertThat(StepMath.awardedSteps(249, 250)).isEqualTo(99);
        assertThat(StepMath.awardedSteps(250, 250)).isEqualTo(100);
        assertThat(StepMath.awardedSteps(999, 250)).isEqualTo(100);
        assertThat(StepMath.awardedSteps(0, 10)).isZero();
        assertThat(StepMath.awardedSteps(Long.MAX_VALUE, 1_000_000_000L)).isEqualTo(100);
    }
}
