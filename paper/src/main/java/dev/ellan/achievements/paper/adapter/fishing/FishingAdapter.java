package dev.ellan.achievements.paper.adapter.fishing;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.adapter.Guard;
import net.momirealms.customfishing.api.event.FishingResultEvent;
import net.momirealms.customfishing.api.mechanic.context.ContextKeys;
import net.momirealms.customfishing.api.mechanic.loot.Loot;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.function.Consumer;

public final class FishingAdapter implements Adapter {
    @Override
    public String id() {
        return "fishing";
    }

    @Override
    public String requiredPlugin() {
        return "CustomFishing";
    }

    @Override
    public Set<String> triggers() {
        return Set.of("fish_catch");
    }

    @Override
    public Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onResult(FishingResultEvent event) {
                Guard.run(plugin, "FishingResultEvent", () -> handle(event, sink));
            }
        }, plugin);
        return Set.of();
    }

    private static void handle(FishingResultEvent event, Consumer<GameplayEvent> sink) {
        Loot loot = event.getLoot();
        if (loot == null) {
            return;
        }
        FishingTranslator.catchEvent(event.getPlayer().getUniqueId(),
            event.getResult() == FishingResultEvent.Result.SUCCESS, loot.id(), loot.lootGroup(),
            loot.type() == null ? null : loot.type().toString(), event.getContext().arg(ContextKeys.SIZE),
            event.getContext().arg(ContextKeys.IS_NEW_SIZE_RECORD), event.getAmount()).ifPresent(sink);
    }
}
