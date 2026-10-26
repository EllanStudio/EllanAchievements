package dev.ellan.achievements.paper.reward;

import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.ItemReward;
import dev.ellan.achievements.core.config.RewardSpec;
import dev.ellan.achievements.core.progress.PlayerProgress;
import dev.ellan.achievements.paper.icon.ItemIds;
import dev.ellan.achievements.paper.storage.ProgressStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/** Pays an achievement's rewards at most once per player (spec §6.5). */
public final class RewardService {
    private final Plugin plugin;
    private final ProgressStore store;

    public RewardService(Plugin plugin, ProgressStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    /** Records completion and pays the rewards unless already paid. Safe to call repeatedly. */
    public void grant(Player player, AchievementDefinition def) {
        PlayerProgress progress = store.load(player);
        if (store.readOnly(player.getUniqueId())) {
            plugin.getLogger().warning("Not rewarding " + def.fullId() + " to " + player.getName()
                + ": progress data is unreadable");
            return;
        }
        boolean newlyCompleted = progress.markCompleted(def.fullId());
        if (progress.rewarded(def.fullId())) {
            if (newlyCompleted) {
                store.save(player, progress);
            }
            return;
        }
        // Record first: a crash mid-payment must not lead to paying twice.
        progress.markRewarded(def.fullId());
        store.save(player, progress);

        RewardSpec rewards = def.rewards();
        if (rewards.exp() > 0) {
            player.giveExp(rewards.exp());
        }
        if (rewards.coins() > 0) {
            depositCoins(player, def, rewards.coins());
        }
        for (ItemReward item : rewards.items()) {
            ItemStack stack = ItemIds.build(item.itemId(), item.amount()).orElse(null);
            if (stack == null) {
                plugin.getLogger().warning("Reward item " + item.itemId() + " for " + def.fullId() + " not found");
                continue;
            }
            player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        for (String command : rewards.commands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", player.getName()));
        }
    }

    private void depositCoins(Player player, AchievementDefinition def, long coins) {
        if (!Bukkit.getPluginManager().isPluginEnabled("ExcellentEconomy")) {
            plugin.getLogger().warning("ExcellentEconomy missing; skipped " + coins + " coins for " + def.fullId()
                + " to " + player.getName());
            return;
        }
        EconomyHook.deposit(player.getUniqueId(), coins).whenComplete((ok, failure) -> {
            if (failure != null || !Boolean.TRUE.equals(ok)) {
                plugin.getLogger().log(Level.WARNING, "Coin reward failed: " + coins + " to " + player.getName()
                    + " for " + def.fullId(), failure);
            }
        });
    }
}
