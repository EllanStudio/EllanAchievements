package dev.ellan.achievements.paper.adapter.tavern;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.icon.ItemIds;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static dev.ellan.achievements.paper.adapter.ReflectiveEvents.call;
import static dev.ellan.achievements.paper.adapter.ReflectiveEvents.register;

/** KaleidoscopeTavern's {@code paper.api.event} classes (EllanStudio/KaleidoscopeTavern#73), bound by name. */
public final class TavernAdapter implements Adapter {
    private static final String EVENTS = "com.github.ysbbbbbb.kaleidoscopetavern.paper.api.event.";
    private static final Map<String, String> PRODUCTION = Map.of(
        "TavernPressEvent", "tavern_press",
        "TavernShakeEvent", "tavern_shake",
        "TavernBarrelTapEvent", "tavern_tap");

    @Override
    public String id() {
        return "tavern";
    }

    @Override
    public String requiredPlugin() {
        return "KaleidoscopeTavern";
    }

    @Override
    public Set<String> triggers() {
        return Set.of("tavern_press", "tavern_shake", "tavern_tap", "tavern_drink");
    }

    @Override
    public Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) {
        PRODUCTION.forEach((event, trigger) -> require(plugin, event, e -> sink.accept(TavernTranslator.production(
            player(e).getUniqueId(), trigger, (String) call(e, "recipeId"), ItemIds.of((ItemStack) call(e, "product"))))));
        require(plugin, "TavernDrinkEvent", e -> sink.accept(TavernTranslator.drink(
            player(e).getUniqueId(), ItemIds.of((ItemStack) call(e, "drink")))));
        return Set.of();
    }

    private static void require(Plugin plugin, String event, Consumer<Event> handler) {
        if (!register(plugin, EVENTS + event, handler)) {
            throw new IllegalStateException("KaleidoscopeTavern has no " + event + " (needs the achievement events build)");
        }
    }

    private static Player player(Event event) {
        return (Player) call(event, "getPlayer");
    }
}
