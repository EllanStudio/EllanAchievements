package dev.ellan.achievements.paper.adapter.brewing;

import dev.ellan.achievements.paper.adapter.ReflectiveEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflective access to TheBrewingProject's API (3.4.0-beta is not on any Maven repository).
 * Method names were checked with javap against the 3.4.0-beta jar on the server.
 */
public final class TbpApi {
    static final String API_CLASS = "dev.jsinco.brewery.bukkit.api.TheBrewingProjectApi";
    private final Object api;

    private TbpApi(Object api) {
        this.api = api;
    }

    /** Empty when TheBrewingProject is not loaded or its API service is missing. */
    public static Optional<TbpApi> find(Plugin plugin) {
        try {
            Class<?> type = Class.forName(API_CLASS, true, plugin.getClass().getClassLoader());
            Method getRegistration = Bukkit.getServicesManager().getClass().getMethod("getRegistration", Class.class);
            Object registration = getRegistration.invoke(Bukkit.getServicesManager(), type);
            if (registration instanceof RegisteredServiceProvider<?> provider) {
                return Optional.of(new TbpApi(provider.getProvider()));
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | LinkageError failure) {
            return Optional.empty();
        }
    }

    Object recipeRegistry() {
        return ReflectiveEvents.call(api, "getRecipeRegistry");
    }

    Object brewManager() {
        return ReflectiveEvents.call(api, "getBrewManager");
    }

    public boolean recipeExists(String name) {
        try {
            Object registry = recipeRegistry();
            Method getRecipe = registry.getClass().getMethod("getRecipe", String.class);
            getRecipe.setAccessible(true);
            return ((Optional<?>) getRecipe.invoke(registry, name)).isPresent();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
