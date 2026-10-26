package dev.ellan.achievements.paper;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.config.ConfigLoader;
import dev.ellan.achievements.core.config.Issue;
import dev.ellan.achievements.core.config.LoadResult;
import dev.ellan.achievements.core.config.Severity;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.adapter.AdapterRegistry;
import dev.ellan.achievements.paper.adapter.farming.FarmingAdapter;
import dev.ellan.achievements.paper.adapter.fishing.FishingAdapter;
import dev.ellan.achievements.paper.command.AuditLog;
import dev.ellan.achievements.paper.command.EaCommand;
import dev.ellan.achievements.paper.display.AdvancementRegistrar;
import dev.ellan.achievements.paper.display.ProgressService;
import dev.ellan.achievements.paper.icon.IconResolver;
import dev.ellan.achievements.paper.integration.PluginExternalIds;
import dev.ellan.achievements.paper.reward.RewardListener;
import dev.ellan.achievements.paper.reward.RewardService;
import dev.ellan.achievements.paper.storage.ProgressStore;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class EllanAchievementsPlugin extends JavaPlugin implements Listener {
    private static final long ALIGN_DELAY_TICKS = 40;
    private final AdapterRegistry adapters = new AdapterRegistry();
    private ProgressStore store;
    private AdvancementRegistrar registrar;
    private volatile AchievementCatalog catalog = new AchievementCatalog(List.of(), "");
    private ProgressService progress;

    @Override
    public void onEnable() {
        store = new ProgressStore(this);
        registrar = new AdvancementRegistrar(new IconResolver(getLogger()), getLogger());
        progress = new ProgressService(this, this::catalog, store);
        copyBundledAchievements();
        adapters.enableAll(this, adapters(), progress);
        getLogger().info("Adapters: " + adapters.orderedStatus());
        Bukkit.getPluginManager().registerEvents(this, this);
        EaCommand command = new EaCommand(this, new AuditLog(getDataFolder().toPath().resolve("audit.log")));
        var registered = getCommand("ea");
        if (registered != null) {
            registered.setExecutor(command);
            registered.setTabCompleter(command);
        }
        Bukkit.getPluginManager().registerEvents(new RewardListener(this::catalog, new RewardService(this, store)), this);
        // First tick: every plugin is enabled and CraftEngine has loaded its items.
        Bukkit.getScheduler().runTask(this, () -> {
            loadConfig();
            registerAdvancements();
        });
    }

    /** Adapters in display order; later tasks add to this list. */
    private List<Adapter> adapters() {
        return List.of(new FishingAdapter(), new FarmingAdapter());
    }

    /** Re-registers after any datapack reload, which drops in-memory advancements. */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event) {
        registerAdvancements();
    }

    public LoadResult loadConfig() {
        LoadResult result = ConfigLoader.load(readAchievementFiles(), new PluginExternalIds(this, adapters));
        catalog = result.catalog();
        long errors = result.issues().stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        for (Issue issue : result.issues()) {
            if (issue.severity() == Severity.ERROR) {
                getLogger().severe(issue.toString());
            } else {
                getLogger().warning(issue.toString());
            }
        }
        long loaded = catalog.tabs().stream().mapToLong(tab -> tab.achievements().size()).sum();
        getLogger().info("Loaded " + catalog.tabs().size() + " tabs, " + loaded + " achievements, " + errors
            + " errors, " + (result.issues().size() - errors) + " warnings; config sha256=" + catalog.sha256());
        return result;
    }

    private void registerAdvancements() {
        int registered = registrar.registerAll(catalog);
        getLogger().info("Registered " + registered + " advancements");
        Bukkit.getOnlinePlayers().forEach(progress::align);
    }

    /** Aligns after SparrowSync has applied the player's synced data (it loads shortly after join). */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                progress.align(player);
            }
        }, ALIGN_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store.forget(event.getPlayer().getUniqueId());
    }

    private Path achievementsFolder() {
        return getDataFolder().toPath().resolve("achievements");
    }

    private SortedMap<String, String> readAchievementFiles() {
        SortedMap<String, String> files = new TreeMap<>();
        try (Stream<Path> paths = Files.list(achievementsFolder())) {
            for (Path path : paths.filter(path -> path.getFileName().toString().endsWith(".yml")).toList()) {
                files.put(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
            }
        } catch (IOException failure) {
            getLogger().severe("Cannot read " + achievementsFolder() + ": " + failure);
        }
        return files;
    }

    /** On first start, copies the achievement files shipped in the jar. Never overwrites existing files. */
    private void copyBundledAchievements() {
        Path folder = achievementsFolder();
        if (Files.isDirectory(folder)) {
            return;
        }
        try (JarFile jar = new JarFile(getFile())) {
            Files.createDirectories(folder);
            for (JarEntry entry : jar.stream().toList()) {
                String name = entry.getName();
                if (name.startsWith("achievements/") && name.endsWith(".yml")) {
                    try (InputStream input = jar.getInputStream(entry)) {
                        Files.copy(input, folder.resolve(name.substring("achievements/".length())));
                    }
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public AchievementCatalog catalog() {
        return catalog;
    }

    public AdapterRegistry adapterRegistry() {
        return adapters;
    }

    public ProgressService progress() {
        return progress;
    }

    public ProgressStore store() {
        return store;
    }
}
