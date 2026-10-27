package dev.ellan.achievements.core.progress;

/** Maps counts onto vanilla criteria steps (spec §6.2): at most 100 steps per achievement. */
public final class StepMath {
    public static final int MAX_STEPS = 100;

    private StepMath() {
    }

    public static int steps(long goal) {
        return (int) Math.min(goal, MAX_STEPS);
    }

    /** Steps a player should hold: all of them once completed (even if the goal was raised since), else by count. */
    public static int targetSteps(long count, long goal, boolean completed) {
        return completed ? steps(goal) : awardedSteps(count, goal);
    }

    public static int awardedSteps(long count, long goal) {
        if (count <= 0) {
            return 0;
        }
        if (count >= goal) {
            return steps(goal);
        }
        // Exact integer arithmetic unless count * steps would overflow.
        int steps = steps(goal);
        if (count <= Long.MAX_VALUE / steps) {
            return (int) (count * steps / goal);
        }
        return (int) Math.floor((double) count / goal * steps);
    }
}
