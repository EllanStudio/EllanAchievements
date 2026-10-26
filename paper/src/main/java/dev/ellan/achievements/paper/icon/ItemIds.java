package dev.ellan.achievements.paper.icon;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Optional;

/** Item IDs as written in config: CraftEngine IDs for custom items, {@code minecraft:*} otherwise. */
public final class ItemIds {
    private ItemIds() {
    }

    private static boolean craftEngine() {
        return Bukkit.getPluginManager().isPluginEnabled("CraftEngine");
    }

    public static String of(ItemStack stack) {
        if (craftEngine()) {
            Optional<String> custom = CraftEngineHook.customId(stack);
            if (custom.isPresent()) {
                return custom.get();
            }
        }
        return stack.getType().getKey().toString();
    }

    public static Optional<ItemStack> build(String id, int amount) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("minecraft:") || !normalized.contains(":")) {
            Material material = Material.matchMaterial(normalized);
            return material == null || !material.isItem() ? Optional.empty() : Optional.of(new ItemStack(material, amount));
        }
        return craftEngine() ? CraftEngineHook.build(normalized, amount) : Optional.empty();
    }
}
