package dev.ellan.achievements.paper.reward;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.display.AdvancementJson;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.function.Supplier;

/** The single reward entry point: any completion of one of our advancements, including /advancement grant. */
public final class RewardListener implements Listener {
    private final Supplier<AchievementCatalog> catalog;
    private final RewardService rewards;

    public RewardListener(Supplier<AchievementCatalog> catalog, RewardService rewards) {
        this.catalog = catalog;
        this.rewards = rewards;
    }

    @EventHandler
    public void onDone(PlayerAdvancementDoneEvent event) {
        NamespacedKey key = event.getAdvancement().getKey();
        if (!key.getNamespace().equals(AdvancementJson.NAMESPACE) || key.getKey().endsWith("/root")) {
            return;
        }
        catalog.get().byFullId(key.getKey()).ifPresent(def -> rewards.grant(event.getPlayer(), def));
    }
}
