package dev.ellan.achievements.paper.storage;

import dev.ellan.achievements.core.progress.DecodeResult;
import dev.ellan.achievements.core.progress.PlayerProgress;
import dev.ellan.achievements.core.progress.ProgressCodec;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Progress in the player's PDC (spec §6.1). Unreadable data is never overwritten. */
public final class ProgressStore {
    private final Plugin plugin;
    private final NamespacedKey key;
    private final Set<UUID> readOnly = ConcurrentHashMap.newKeySet();

    public ProgressStore(Plugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey("ellanachievements", "progress");
    }

    public PlayerProgress load(Player player) {
        String raw = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        DecodeResult result = ProgressCodec.decode(raw);
        if (result instanceof DecodeResult.Ok ok) {
            return ok.progress();
        }
        DecodeResult.Unreadable unreadable = (DecodeResult.Unreadable) result;
        if (readOnly.add(player.getUniqueId())) {
            plugin.getLogger().severe("Unreadable achievement progress for " + player.getName() + " ("
                + unreadable.reason() + "); kept as-is, progress paused this session. Raw: " + unreadable.raw());
            Component warning = Component.text("[EllanAchievements] " + player.getName()
                + " 的成就数据无法读取，本次会话已暂停其成就进度，详见后台日志。", NamedTextColor.RED);
            Bukkit.getOnlinePlayers().stream()
                .filter(online -> online.hasPermission("ellanachievements.admin"))
                .forEach(online -> online.sendMessage(warning));
        }
        return PlayerProgress.empty();
    }

    public void save(Player player, PlayerProgress progress) {
        if (readOnly(player.getUniqueId())) {
            return;
        }
        player.getPersistentDataContainer().set(key, PersistentDataType.STRING, ProgressCodec.encode(progress));
    }

    public boolean readOnly(UUID player) {
        return readOnly.contains(player);
    }

    public void forget(UUID player) {
        readOnly.remove(player);
    }
}
