package dev.ellan.achievements.core.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The real config/achievements files must load cleanly. Unknown IDs are only checked on a live server (/ea reload). */
class ShippedConfigTest {
    static final ExternalIds ANYTHING = new ExternalIds() {
        @Override public boolean itemExists(String id) { return true; }
        @Override public Optional<Boolean> fishingLoot(String id) { return Optional.of(true); }
        @Override public Optional<Boolean> crop(String id) { return Optional.of(true); }
        @Override public Optional<Boolean> brewRecipe(String id) { return Optional.of(true); }
        @Override public boolean triggerAvailable(String trigger) { return true; }
    };

    @Test
    void allShippedFilesLoadWithoutErrors() throws IOException {
        SortedMap<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.list(Path.of("..", "config", "achievements"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".yml")).toList()) {
                files.put(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        LoadResult result = ConfigLoader.load(files, ANYTHING);

        assertThat(result.issues()).isEmpty();
        assertThat(result.catalog().tabs()).extracting(TabDefinition::id)
            .containsExactly("brewing", "cooking", "farming", "fishing", "tavern");
        assertThat(result.catalog().tabs().stream().filter(tab -> tab.id().equals("cooking")).findFirst().orElseThrow()
            .achievements()).hasSize(33);
    }
}
