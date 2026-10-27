package dev.ellan.achievements.paper.adapter.farming;

import dev.ellan.achievements.core.event.GameplayEvent;
import dev.ellan.achievements.paper.adapter.Adapter;
import dev.ellan.achievements.paper.adapter.Guard;
import dev.ellan.achievements.paper.icon.ItemIds;
import net.momirealms.customcrops.api.BukkitCustomCropsPlugin;
import net.momirealms.customcrops.api.core.mechanic.crop.CropConfig;
import net.momirealms.customcrops.api.core.mechanic.crop.CropStageConfig;
import net.momirealms.customcrops.api.core.world.Season;
import net.momirealms.customcrops.api.event.CropBreakEvent;
import net.momirealms.customcrops.api.event.CropPlantEvent;
import net.momirealms.customcrops.api.event.QualityCropActionEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class FarmingAdapter implements Adapter {
    @Override
    public String id() {
        return "farming";
    }

    @Override
    public String requiredPlugin() {
        return "CustomCrops";
    }

    @Override
    public Set<String> triggers() {
        return Set.of("crop_plant", "crop_harvest", "crop_quality");
    }

    @Override
    public Set<String> enable(Plugin plugin, Consumer<GameplayEvent> sink) {
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onPlant(CropPlantEvent event) {
                Guard.run(plugin, "CropPlantEvent",
                    () -> sink.accept(FarmingTranslator.plant(event.getPlayer().getUniqueId(), event.cropConfig().id())));
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onBreak(CropBreakEvent event) {
                Guard.run(plugin, "CropBreakEvent", () -> harvest(event, sink));
            }

            private void harvest(CropBreakEvent event, Consumer<GameplayEvent> sink) {
                if (!(event.entityBreaker() instanceof Player player)) {
                    return;
                }
                CropConfig crop = event.cropConfig();
                FarmingTranslator.harvest(player.getUniqueId(), crop.id(), true,
                    isFinalStage(crop, event.cropStageItemID()), season(event.location().getWorld())).ifPresent(sink);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onQuality(QualityCropActionEvent event) {
                Guard.run(plugin, "QualityCropActionEvent", () -> quality(event, sink));
            }

            private void quality(QualityCropActionEvent event, Consumer<GameplayEvent> sink) {
                if (!(event.context().holder() instanceof Player player)) {
                    return;
                }
                for (ItemStack item : event.items()) {
                    FarmingTranslator.quality(player.getUniqueId(), ItemIds.of(item), event.qualityCrops())
                        .ifPresent(sink);
                }
            }
        }, plugin);
        return Set.of();
    }

    private static boolean isFinalStage(CropConfig crop, String stageItemId) {
        CropStageConfig last = crop.stages().stream().max(Comparator.comparingInt(CropStageConfig::point)).orElse(null);
        if (last == null || stageItemId == null) {
            return false;
        }
        String finalId = last.stageID() != null ? last.stageID()
            : crop.stageWithModelByPoint(last.point()) == null ? null : crop.stageWithModelByPoint(last.point()).stageID();
        return Objects.equals(finalId, stageItemId);
    }

    private static String season(World world) {
        try {
            Season season = BukkitCustomCropsPlugin.getInstance().getWorldManager().getSeason(world);
            return season == null || season == Season.DISABLE ? null : season.name();
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
