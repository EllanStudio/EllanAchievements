package dev.ellan.achievements.paper.reward;

import org.bukkit.Bukkit;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;
import su.nightexpress.excellenteconomy.api.currency.operation.NotificationTarget;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationContext;
import su.nightexpress.excellenteconomy.api.currency.operation.OperationResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** All ExcellentEconomy access. Only loaded when ExcellentEconomy is enabled. */
final class EconomyHook {
    static final String CURRENCY = "ellan_coin";
    private static final OperationContext CONTEXT = OperationContext.custom("EllanAchievements")
        .silentFor(NotificationTarget.EXECUTOR);

    private EconomyHook() {
    }

    static CompletableFuture<Boolean> deposit(UUID player, long coins) {
        var registration = Bukkit.getServicesManager().getRegistration(ExcellentEconomyAPI.class);
        if (registration == null) {
            return CompletableFuture.completedFuture(false);
        }
        ExcellentEconomyAPI api = registration.getProvider();
        if (!api.hasCurrency(CURRENCY) || !api.canPerformOperations()) {
            return CompletableFuture.completedFuture(false);
        }
        return api.depositAsync(player, CURRENCY, coins, CONTEXT).thenApply(OperationResult::success);
    }
}
