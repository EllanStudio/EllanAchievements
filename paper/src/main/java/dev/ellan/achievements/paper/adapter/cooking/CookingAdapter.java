package dev.ellan.achievements.paper.adapter.cooking;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.icon.ItemIds;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static dev.ellan.achievements.paper.adapter.ReflectiveEvents.call;
import static dev.ellan.achievements.paper.adapter.ReflectiveEvents.register;

/**
 * KaleidoscopeCookery, through its public {@code api.event} classes only (no Maven artifact, so bound by name).
 * {@code KaleidoscopeGameplayEvent} is newer than 1.3.0; without it only {@code cooking_action} is unavailable.
 */
public final class CookingAdapter implements Adapter {
    private static final String EVENTS = "net.kaleidoscope.cookery.api.event.";

    @Override
    public String id() {
        return "cooking";
    }

    @Override
    public String requiredPlugin() {
        return "KaleidoscopeCookeryPlugin";
    }

    @Override
    public Set<String> triggers() {
        return Set.of("dish_cook", "cooking_stir_fry", "cooking_grind", "cooking_sickle", "cooking_action");
    }

    @Override
    public Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) {
        require(plugin, "PotExtractDishEvent", event -> List.of(CookingTranslator.dish(
            player(event, "getPlayer"), ItemIds.of((ItemStack) call(event, "dish")), CookingTranslator.POT)), sink);
        require(plugin, "StockpotExtractDishEvent", event -> List.of(CookingTranslator.dish(
            player(event, "getPlayer"), ItemIds.of((ItemStack) call(event, "dish")), CookingTranslator.STOCKPOT)), sink);
        require(plugin, "ShawarmaExtractEvent", event -> List.of(CookingTranslator.dish(
            player(event, "getPlayer"), ItemIds.of((ItemStack) call(event, "product")), CookingTranslator.SHAWARMA)), sink);
        require(plugin, "SteamerBreakFullEvent", event -> products(event).stream()
            .map(product -> CookingTranslator.dish(player(event, "getPlayer"), ItemIds.of(product), CookingTranslator.STEAMER))
            .toList(), sink);
        require(plugin, "PotStirFryEvent", event -> List.of(CookingTranslator.stirFry(
            player(event, "getPlayer"), (Integer) call(event, "count"))), sink);
        require(plugin, "MillstoneGrindCompleteEvent", event -> products(event).stream()
            .map(product -> CookingTranslator.grind(player(event, "player"), ItemIds.of(product))).toList(), sink);
        require(plugin, "SickleHarvestEvent", event -> List.of(CookingTranslator.sickle(
            player(event, "player"), ((Block) call(event, "block")).getType().getKey().toString())), sink);
        boolean actions = register(plugin, EVENTS + "KaleidoscopeGameplayEvent", event -> translate(event,
            e -> List.of(CookingTranslator.action(player(e, "getPlayer"), (String) call(e, "action"))), sink));
        return actions ? Set.of() : Set.of("cooking_action");
    }

    private static void require(Plugin plugin, String event, Function<Event, List<GameplayEvent>> translator,
                                Consumer<GameplayEvent> sink) {
        if (!register(plugin, EVENTS + event, e -> translate(e, translator, sink))) {
            throw new IllegalStateException("KaleidoscopeCookery has no " + event);
        }
    }

    private static void translate(Event event, Function<Event, List<GameplayEvent>> translator,
                                  Consumer<GameplayEvent> sink) {
        translator.apply(event).forEach(sink);
    }

    private static java.util.UUID player(Event event, String accessor) {
        return ((Player) call(event, accessor)).getUniqueId();
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> products(Event event) {
        return (List<ItemStack>) call(event, "products");
    }
}
