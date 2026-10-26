package dev.ellan.achievements.paper.adapter.brewing;

import dev.ellan.achievements.paper.adapter.ReflectiveEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    /** What the achievements need to know about one brew item. */
    public record BrewInfo(String recipe, String quality, double score, Set<UUID> brewers) {
    }

    /** Empty when the item is not a TheBrewingProject brew. */
    public Optional<BrewInfo> inspect(ItemStack stack) {
        Optional<?> brew = (Optional<?>) invoke(brewManager(), "fromItem", stack);
        if (brew.isEmpty()) {
            return Optional.empty();
        }
        Object value = brew.get();
        Optional<?> recipe = (Optional<?>) invoke(value, "closestRecipe", recipeRegistry());
        String recipeName = recipe.map(r -> (String) ReflectiveEvents.call(r, "getRecipeName")).orElse(null);
        String quality = null;
        double score = 0;
        if (recipe.isPresent()) {
            Object brewScore = invoke(value, "score", recipe.get());
            Object brewQuality = ReflectiveEvents.call(brewScore, "brewQuality");
            quality = brewQuality == null ? null : brewQuality.toString();
            score = ((Number) ReflectiveEvents.call(brewScore, "score")).doubleValue();
        }
        Set<UUID> brewers = new LinkedHashSet<>();
        for (Object brewer : (Collection<?>) ReflectiveEvents.call(value, "getBrewers")) {
            brewers.add((UUID) brewer);
        }
        return Optional.of(new BrewInfo(recipeName, quality, score, brewers));
    }

    /** Calls a public one-argument method whose parameter accepts {@code argument}. */
    static Object invoke(Object target, String name, Object argument) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                && method.getParameterTypes()[0].isInstance(argument)) {
                try {
                    method.setAccessible(true);
                    return method.invoke(target, argument);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }
        throw new IllegalStateException(target.getClass().getName() + " has no " + name + "(" + argument.getClass().getName() + ")");
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
