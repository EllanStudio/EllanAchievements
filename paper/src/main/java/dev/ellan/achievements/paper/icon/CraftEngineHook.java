package dev.ellan.achievements.paper.icon;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.item.ItemBuildContext;
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
}
