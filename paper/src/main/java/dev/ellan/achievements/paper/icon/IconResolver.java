package dev.ellan.achievements.paper.icon;

import dev.ellan.achievements.core.config.IconSpec;
import dev.ellan.achievements.core.display.ResolvedIcon;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.key.Key;
import org.bukkit.Color;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/** Turns a configured icon into a vanilla item plus the components that make CraftEngine items render. */
public final class IconResolver {
    private static final ResolvedIcon UNKNOWN = new ResolvedIcon("minecraft:barrier", Map.of());
    private final Logger logger;

    public IconResolver(Logger logger) {
        this.logger = logger;
    }

    public ResolvedIcon resolve(IconSpec spec) {
        ItemStack stack;
        try {
            stack = ItemIds.buildIcon(spec.item()).orElse(null);
        } catch (RuntimeException failure) {
            logger.warning("Icon item '" + spec.item() + "' has no client view (" + failure + "); using the plain item");
            stack = ItemIds.build(spec.item(), 1).orElse(null);
        }
        if (stack == null) {
            logger.warning("Icon item '" + spec.item() + "' not found; using minecraft:barrier");
            return UNKNOWN;
        }
        Map<String, Object> components = new LinkedHashMap<>();
        Key model = stack.getData(DataComponentTypes.ITEM_MODEL);
        if (model != null && !model.equals(stack.getType().getKey())) {
            components.put("minecraft:item_model", model.asString());
        }
        CustomModelData modelData = stack.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        if (modelData != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("floats", modelData.floats());
            data.put("flags", modelData.flags());
            data.put("strings", modelData.strings());
            data.put("colors", modelData.colors().stream().map(Color::asRGB).toList());
            components.put("minecraft:custom_model_data", data);
        }
        return new ResolvedIcon(stack.getType().getKey().toString(), components);
    }
}
