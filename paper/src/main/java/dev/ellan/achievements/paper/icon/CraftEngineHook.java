package dev.ellan.achievements.paper.icon;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItem;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.processor.ItemProcessor;
import net.momirealms.craftengine.core.plugin.context.ContextHolder;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** All CraftEngine access. Only loaded when CraftEngine is enabled. */
final class CraftEngineHook {
    private CraftEngineHook() {
    }

    static Optional<String> customId(ItemStack stack) {
        if (stack == null || !CraftEngineItems.isCustomItem(stack)) {
            return Optional.empty();
        }
        Key id = CraftEngineItems.getCustomItemId(stack);
        return Optional.ofNullable(id).map(Key::asString);
    }

    static Optional<ItemStack> build(String id, int amount) {
        BukkitItemDefinition definition = CraftEngineItems.byId(id);
        if (definition == null) {
            return Optional.empty();
        }
        ItemStack stack = definition.buildBukkitItem(ItemBuildContext.empty(), amount);
        return Optional.ofNullable(stack);
    }

    /**
     * The item as clients see it. With {@code client-bound-model} CraftEngine only adds the model when it sends item
     * packets, and advancement icons never pass through that, so apply the client-bound changes here.
     */
    static Optional<ItemStack> buildClientView(String id) {
        BukkitItemDefinition definition = CraftEngineItems.byId(id);
        if (definition == null) {
            return Optional.empty();
        }
        Item item = definition.buildItem(ItemBuildContext.empty(), 1);
        if (definition.hasClientboundMaterial() && definition.clientBoundMaterial() != null) {
            item = item.transmuteCopy(definition.clientBoundMaterial());
        }
        // Processors read and replace the item held by the context.
        ItemBuildContext context = ItemBuildContext.of(null, item, ContextHolder.empty());
        for (ItemProcessor processor : definition.clientBoundProcessors()) {
            item = item.apply(processor, context);
        }
        return Optional.ofNullable(((BukkitItem) item).getBukkitItem());
    }
}
