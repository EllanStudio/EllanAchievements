package dev.ellan.achievements.paper.display;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.TabDefinition;
import dev.ellan.achievements.core.display.AdvancementJson;
import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.core.progress.PlayerProgress;
import dev.ellan.achievements.core.progress.StepMath;
import dev.ellan.achievements.core.rules.ProgressChange;
import dev.ellan.achievements.core.rules.RuleEngine;
import dev.ellan.achievements.paper.reward.RewardService;
import dev.ellan.achievements.paper.storage.ProgressStore;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Applies gameplay events to players and keeps vanilla advancement progress in step with the counters (spec §6.3–6.4). */
public final class ProgressService implements Consumer<GameplayEvent> {
    private final Plugin plugin;
    private final Supplier<AchievementCatalog> catalog;
    private final ProgressStore store;
    private final RewardService rewards;

    public ProgressService(Plugin plugin, Supplier<AchievementCatalog> catalog, ProgressStore store,
                           RewardService rewards) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.store = store;
        this.rewards = rewards;
    }

    @Override
    public void accept(GameplayEvent event) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> accept(event));
            return;
        }
        Player player = Bukkit.getPlayer(event.player());
        if (player == null || store.readOnly(player.getUniqueId())) {
            return;
        }
        PlayerProgress progress = store.load(player);
        if (store.readOnly(player.getUniqueId())) {
            return;
        }
        var changes = new RuleEngine(catalog.get()).apply(progress, event, id -> isDone(player, id));
        if (changes.isEmpty()) {
            return;
        }
        store.save(player, progress);
        for (ProgressChange change : changes) {
            awardUpTo(player, change.fullId(), StepMath.awardedSteps(change.after(), change.goal()));
        }
    }

    /**
     * Grants tab roots and any steps the counters already earned; completed achievements keep every step even
     * after a goal increase. Never revokes. A goal decrease can complete an achievement without vanilla firing
     * the done event, so completion is checked here and rewards paid if still owed.
     */
    public void align(Player player) {
        AchievementCatalog current = catalog.get();
        for (TabDefinition tab : current.tabs()) {
            Advancement root = AdvancementRegistrar.advancement(AdvancementJson.rootKey(tab.id()));
            if (root != null) {
                player.getAdvancementProgress(root).awardCriteria(AdvancementJson.ROOT_CRITERION);
            }
        }
        PlayerProgress progress = store.load(player);
        for (TabDefinition tab : current.tabs()) {
            for (AchievementDefinition def : tab.achievements()) {
                if (def.disabled()) {
                    continue;
                }
                awardUpTo(player, def.fullId(), StepMath.targetSteps(progress.counter(def.fullId()), def.goal(),
                    progress.completed(def.fullId())));
                if (isDone(player, def.fullId())) {
                    rewards.grant(player, def);
                }
            }
        }
    }

    /** Admin: sets the counter and makes vanilla progress match exactly (may revoke steps). */
    public void setCounter(Player player, AchievementDefinition def, long value) {
        PlayerProgress progress = store.load(player);
        progress.setCounter(def.fullId(), value);
        if (value < def.goal()) {
            progress.unmarkCompleted(def.fullId());
        }
        store.save(player, progress);
        int target = StepMath.awardedSteps(value, def.goal());
        revokeAbove(player, def.fullId(), target);
        awardUpTo(player, def.fullId(), target);
    }

    /** Admin: clears counter and unique values and revokes the advancement; the reward record is kept. */
    public void reset(Player player, AchievementDefinition def) {
        PlayerProgress progress = store.load(player);
        progress.clear(def.fullId());
        store.save(player, progress);
        revokeAbove(player, def.fullId(), 0);
    }

    public boolean isDone(Player player, String fullId) {
        Advancement advancement = AdvancementRegistrar.advancement(fullId);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    private void awardUpTo(Player player, String fullId, int steps) {
        Advancement advancement = AdvancementRegistrar.advancement(fullId);
        if (advancement == null || steps <= 0) {
            return;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (int step = 1; step <= steps; step++) {
            String criterion = AdvancementJson.stepCriterion(step);
            if (progress.getDateAwarded(criterion) == null) {
                progress.awardCriteria(criterion);
            }
        }
    }

    private void revokeAbove(Player player, String fullId, int steps) {
        Advancement advancement = AdvancementRegistrar.advancement(fullId);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (String criterion : progress.getAwardedCriteria()) {
            if (criterion.startsWith("step_") && Integer.parseInt(criterion.substring(5)) > steps) {
                progress.revokeCriteria(criterion);
            }
        }
    }
}
