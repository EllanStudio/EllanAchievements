package dev.ellan.achievements.paper.adapter.brewing;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.adapter.ReflectiveEvents;
import dev.ellan.achievements.paper.adapter.generic.ItemAcquisition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.function.Consumer;

/** TheBrewingProject, entirely through reflection: a TBP API change disables only this adapter. */
public final class BrewingAdapter implements Adapter {
    private static final String CONSUME_EVENT = "dev.jsinco.brewery.bukkit.api.event.BrewConsumeEvent";
    private static final NamespacedKey COUNTED = new NamespacedKey("ellanachievements", "brew_counted");

    @Override
    public String id() {
        return "brewing";
    }

    @Override
    public String requiredPlugin() {
        return "TheBrewingProject";
    }

    @Override
    public Set<String> triggers() {
        return Set.of("brew_obtain", "brew_drink");
    }

    @Override
    public Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) {
        TbpApi api = TbpApi.find(plugin).orElseThrow(() -> new IllegalStateException("TheBrewingProject API service missing"));
        if (!ReflectiveEvents.register(plugin, CONSUME_EVENT, event -> {
            Player player = (Player) ReflectiveEvents.call(event, "getPlayer");
            api.inspect((ItemStack) ReflectiveEvents.call(event, "getItem")).ifPresent(brew -> sink.accept(
                BrewingTranslator.drink(player.getUniqueId(), brew.recipe(), brew.quality(), brew.score())));
        })) {
            throw new IllegalStateException("TheBrewingProject has no BrewConsumeEvent");
        }
        ItemAcquisition.listen(plugin, (player, stack, replace) -> api.inspect(stack).ifPresent(brew -> {
            boolean counted = stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(COUNTED, PersistentDataType.BYTE);
            BrewingTranslator.obtain(player.getUniqueId(), brew.brewers(), counted, brew.recipe(), brew.quality(),
                brew.score()).ifPresent(event -> {
                    sink.accept(event);
                    ItemStack marked = stack.clone();
                    marked.editMeta(meta -> meta.getPersistentDataContainer().set(COUNTED, PersistentDataType.BYTE, (byte) 1));
                    replace.accept(marked);
                });
        }));
        return Set.of();
    }
}
