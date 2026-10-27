package dev.ellan.achievements.paper.adapter.generic;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.adapter.Guard;
import dev.ellan.achievements.paper.icon.ItemIds;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** {@code item_obtain} and {@code item_kill}, from vanilla events and CraftEngine item IDs. */
public final class GenericAdapter implements Adapter {
    @Override
    public String id() {
        return "generic";
    }

    @Override
    public String requiredPlugin() {
        return null;
    }

    @Override
    public Set<String> triggers() {
        return Set.of("item_obtain", "item_kill");
    }

    @Override
    public Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) {
        ItemAcquisition.listen(plugin, (player, stack, replace) -> sink.accept(new GameplayEvent(player.getUniqueId(),
            "item_obtain", Map.of("item", ItemIds.of(stack)), stack.getAmount())));
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onDeath(EntityDeathEvent event) {
                Guard.run(plugin, "EntityDeathEvent", () -> kill(event, sink));
            }

            private void kill(EntityDeathEvent event, Consumer<GameplayEvent> sink) {
                Player killer = event.getEntity().getKiller();
                if (killer == null) {
                    return;
                }
                ItemStack weapon = killer.getInventory().getItemInMainHand();
                sink.accept(new GameplayEvent(killer.getUniqueId(), "item_kill", Map.of(
                    "weapon", ItemIds.of(weapon), "entity", event.getEntity().getType().getKey().toString())));
            }
        }, plugin);
        return Set.of();
    }
}
