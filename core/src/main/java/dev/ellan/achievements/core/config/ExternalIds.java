package dev.ellan.achievements.core.config;

import java.util.Optional;

/** Lookups against the live server, used only for warnings. {@link Optional#empty()} means the plugin is not loaded. */
public interface ExternalIds {
    boolean itemExists(String id);

    Optional<Boolean> fishingLoot(String id);

    Optional<Boolean> crop(String id);

    Optional<Boolean> brewRecipe(String id);

    boolean triggerAvailable(String trigger);
}
