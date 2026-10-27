package dev.ellan.achievements.paper.display;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Resends the advancement tree to online players. On a datapack reload the server resends each player's tree
 * before our advancements are registered again, and Paper's loadAdvancements never resends, so without this
 * online players keep the old tree until they rejoin.
 *
 * <p>Uses the Mojang-mapped server internals reflectively: {@code ServerPlayer.getAdvancements().save()} then
 * {@code reload(MinecraftServer.getServer().getAdvancements())}, which makes the next tick send the full tree.
 */
final class OnlineAdvancementRefresh {
    private OnlineAdvancementRefresh() {
    }

    static void refreshAll(Logger logger) {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }
        try {
            Object server = Class.forName("net.minecraft.server.MinecraftServer").getMethod("getServer").invoke(null);
            Object manager = server.getClass().getMethod("getAdvancements").invoke(server);
            for (Player player : Bukkit.getOnlinePlayers()) {
                Object handle = player.getClass().getMethod("getHandle").invoke(player);
                Object advancements = handle.getClass().getMethod("getAdvancements").invoke(handle);
                advancements.getClass().getMethod("save").invoke(advancements);
                Method reload = advancements.getClass().getMethod("reload", manager.getClass());
                reload.invoke(advancements, manager);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            logger.warning("Cannot resend advancements to online players; they will see changes after rejoining ("
                + failure + ")");
        }
    }
}
