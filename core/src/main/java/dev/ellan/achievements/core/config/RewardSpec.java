package dev.ellan.achievements.core.config;

import java.util.List;

public record RewardSpec(int exp, long coins, List<ItemReward> items, List<String> commands) {
    public static final RewardSpec NONE = new RewardSpec(0, 0, List.of(), List.of());

    public RewardSpec {
        items = List.copyOf(items);
        commands = List.copyOf(commands);
    }
}
