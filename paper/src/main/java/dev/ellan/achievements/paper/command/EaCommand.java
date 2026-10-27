package dev.ellan.achievements.paper.command;

import dev.ellan.achievements.core.config.AchievementCatalog;
import dev.ellan.achievements.core.config.AchievementDefinition;
import dev.ellan.achievements.core.config.LoadResult;
import dev.ellan.achievements.core.config.Severity;
import dev.ellan.achievements.core.progress.PlayerProgress;
import dev.ellan.achievements.paper.EllanAchievementsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** {@code /ea status|reload|progress|set|reset} (spec §10). */
public final class EaCommand implements TabExecutor {
    private static final List<String> SUBCOMMANDS = List.of("status", "reload", "progress", "set", "reset");
    private final EllanAchievementsPlugin plugin;
    private final AuditLog audit;

    public EaCommand(EllanAchievementsPlugin plugin, AuditLog audit) {
        this.plugin = plugin;
        this.audit = audit;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "progress" -> progress(sender, args);
            case "set" -> set(sender, args);
            case "reset" -> reset(sender, args);
            default -> send(sender, "用法：/ea <status|reload|progress|set|reset>", NamedTextColor.RED);
        }
        return true;
    }

    private void status(CommandSender sender) {
        AchievementCatalog catalog = plugin.catalog();
        long count = catalog.tabs().stream().mapToLong(tab -> tab.achievements().size()).sum();
        send(sender, "成就：" + catalog.tabs().size() + " 个分页，" + count + " 个成就；配置哈希 " + catalog.sha256(),
            NamedTextColor.GOLD);
        plugin.adapterRegistry().orderedStatus().forEach((id, state) ->
            send(sender, "  " + id + "：" + state, state.startsWith("enabled") ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    private void reload(CommandSender sender) {
        LoadResult result = plugin.loadConfig();
        long errors = result.issues().stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        long loaded = result.catalog().tabs().stream().mapToLong(tab -> tab.achievements().size()).sum();
        result.issues().forEach(issue -> send(sender, issue.toString(),
            issue.severity() == Severity.ERROR ? NamedTextColor.RED : NamedTextColor.YELLOW));
        send(sender, String.format("已重载：载入 %d 个成就，跳过 %d 个，警告 %d 条。配置哈希 %s。正在重载数据包以刷新成就…",
            loaded, errors, result.issues().size() - errors, result.catalog().sha256()), NamedTextColor.GREEN);
        // Drops the old in-memory advancements; ServerResourcesReloadedEvent re-registers and re-aligns.
        Bukkit.reloadData();
    }

    private void progress(CommandSender sender, String[] args) {
        Player target = player(sender, args, 1);
        if (target == null) {
            return;
        }
        PlayerProgress progress = plugin.store().load(target);
        Stream<AchievementDefinition> defs = args.length >= 3
            ? plugin.catalog().byFullId(args[2]).stream()
            : plugin.catalog().tabs().stream().flatMap(tab -> tab.achievements().stream())
                .filter(def -> progress.counter(def.fullId()) > 0 || plugin.progress().isDone(target, def.fullId()));
        send(sender, target.getName() + " 的成就进度：", NamedTextColor.GOLD);
        defs.forEach(def -> send(sender, String.format("  %s  %d/%d%s%s%s", def.fullId(), progress.counter(def.fullId()),
            def.goal(), plugin.progress().isDone(target, def.fullId()) ? "  已完成" : "",
            progress.rewarded(def.fullId()) ? "  已领奖" : "",
            progress.uniques(def.fullId()).isEmpty() ? "" : "  " + progress.uniques(def.fullId())), NamedTextColor.WHITE));
    }

    private void set(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "用法：/ea set <玩家> <成就> <计数>", NamedTextColor.RED);
            return;
        }
        Player target = player(sender, args, 1);
        AchievementDefinition def = achievement(sender, args[2]);
        if (target == null || def == null) {
            return;
        }
        long value;
        try {
            value = Long.parseLong(args[3]);
        } catch (NumberFormatException failure) {
            send(sender, "计数必须是整数", NamedTextColor.RED);
            return;
        }
        if (value < 0) {
            send(sender, "计数不能为负数", NamedTextColor.RED);
            return;
        }
        long before = plugin.store().load(target).counter(def.fullId());
        plugin.progress().setCounter(target, def, value);
        audit(sender, target, def, Long.toString(before), Long.toString(value));
        send(sender, "已将 " + target.getName() + " 的 " + def.fullId() + " 设为 " + value, NamedTextColor.GREEN);
    }

    private void reset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "用法：/ea reset <玩家> <成就>", NamedTextColor.RED);
            return;
        }
        Player target = player(sender, args, 1);
        AchievementDefinition def = achievement(sender, args[2]);
        if (target == null || def == null) {
            return;
        }
        long before = plugin.store().load(target).counter(def.fullId());
        plugin.progress().reset(target, def);
        audit(sender, target, def, Long.toString(before), "reset");
        send(sender, "已重置 " + target.getName() + " 的 " + def.fullId() + "（领奖记录保留，不会重复发奖）", NamedTextColor.GREEN);
    }

    private void audit(CommandSender sender, Player target, AchievementDefinition def, String before, String after) {
        try {
            audit.append(sender.getName(), target.getName(), def.fullId(), before, after);
        } catch (IOException failure) {
            plugin.getLogger().severe("Could not write audit log: " + failure);
        }
    }

    private Player player(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            send(sender, "请指定在线玩家", NamedTextColor.RED);
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[index]);
        if (target == null) {
            send(sender, "玩家 " + args[index] + " 不在线", NamedTextColor.RED);
        }
        return target;
    }

    private AchievementDefinition achievement(CommandSender sender, String fullId) {
        AchievementDefinition def = plugin.catalog().byFullId(fullId).orElse(null);
        if (def == null) {
            send(sender, "没有成就 " + fullId + "（格式：分页/成就）", NamedTextColor.RED);
        }
        return def;
    }

    private static void send(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS.stream(), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("progress", "set", "reset").contains(sub)) {
            return List.of();
        }
        if (args.length == 2) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName), args[1]);
        }
        if (args.length == 3) {
            return filter(plugin.catalog().tabs().stream().flatMap(tab -> tab.achievements().stream())
                .map(AchievementDefinition::fullId), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
