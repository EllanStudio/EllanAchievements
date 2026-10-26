package dev.ellan.achievements.paper.integration;

import dev.ellan.achievements.core.config.ExternalIds;
import dev.ellan.achievements.paper.adapter.AdapterRegistry;
import dev.ellan.achievements.paper.adapter.brewing.TbpApi;
import dev.ellan.achievements.paper.icon.ItemIds;
import net.momirealms.customcrops.api.core.Registries;
import net.momirealms.customfishing.api.BukkitCustomFishingPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.function.Supplier;

/** {@link ExternalIds} backed by the live plugins. A plugin that is missing or throws yields "unknown" (no warning). */
public final class PluginExternalIds implements ExternalIds {
    private final Plugin plugin;
    private final AdapterRegistry adapters;

    public PluginExternalIds(Plugin plugin, AdapterRegistry adapters) {
        this.plugin = plugin;
        this.adapters = adapters;
    }

    @Override
    public boolean itemExists(String id) {
        return ItemIds.build(id, 1).isPresent();
    }

    @Override
    public Optional<Boolean> fishingLoot(String id) {
        return ask("CustomFishing", () -> BukkitCustomFishingPlugin.getInstance().getLootManager().getLoot(id).isPresent());
    }

    @Override
    public Optional<Boolean> crop(String id) {
        return ask("CustomCrops", () -> Registries.CROP.containsKey(id));
    }

    @Override
    public Optional<Boolean> brewRecipe(String id) {
        return ask("TheBrewingProject", () -> TbpApi.find(plugin).map(api -> api.recipeExists(id)).orElseThrow());
    }

    @Override
    public boolean triggerAvailable(String trigger) {
        return adapters.triggerAvailable(trigger);
    }

    private static Optional<Boolean> ask(String pluginName, Supplier<Boolean> question) {
        if (!Bukkit.getPluginManager().isPluginEnabled(pluginName)) {
            return Optional.empty();
        }
        try {
            return Optional.of(question.get());
        } catch (RuntimeException | LinkageError failure) {
            return Optional.empty();
        }
    }
}
