package me.bill.fakePlayerPlugin.command;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.BotNameConfig;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BadwordFilter;
import me.bill.fakePlayerPlugin.util.ConfigValidator;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.UpdateChecker;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class ReloadCommand implements FppCommand {

    private static final TextColor ACCENT = TextColor.fromHexString("#A78BFA");
    private static final TextColor GRAY = TextColor.fromHexString("#9691AB");
    private static final TextColor GREEN = TextColor.fromHexString("#BAFF4F");
    private static final TextColor YELLOW = TextColor.fromHexString("#BAFF4F");
    private static final TextColor RED = TextColor.fromHexString("#FF6A5C");

    private static final List<String> TARGETS = List.of("all", "config", "lang");

    private final FakePlayerPlugin plugin;

    public ReloadCommand(FakePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getUsage() {
        return "[all|config|lang]";
    }

    @Override
    public String getDescription() {
        return "重载插件配置（可选指定子系统）。";
    }

    @Override
    public String getPermission() {
        return Perm.RELOAD;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.RELOAD);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String target = args.length > 0 ? args[0].toLowerCase() : "all";

        long start = System.currentTimeMillis();
        String version = plugin.getPluginMeta().getVersion();

        String label = target.equals("all") ? "完整重载" : "重载:" + target;
        sender.sendMessage(Component.text("┌ FakePlayerPlugin v" + version + " - " + label + "…")
                .color(ACCENT));

        switch (target) {
            case "config" -> reloadConfig(sender);
            case "lang" -> reloadLang(sender);
            case "all" -> reloadAll(sender);
            default -> {
                sender.sendMessage(Component.text("│  ")
                        .color(ACCENT)
                        .append(Component.text("✗ 未知目标 '").color(RED))
                        .append(Component.text(target).color(YELLOW))
                        .append(Component.text("'。有效值：" + String.join(", ", TARGETS))
                                .color(RED)));
            }
        }

        long ms = System.currentTimeMillis() - start;
        sender.sendMessage(Component.text("└ ")
                .color(ACCENT)
                .append(Component.text("✓ 完成").color(GREEN))
                .append(Component.text("  耗时 " + ms + "ms").color(GRAY)));
        FppLogger.success("插件已重载 [" + label + "]，执行者 " + sender.getName() + "，耗时 " + ms + "ms。");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return TARGETS.stream().filter(t -> t.startsWith(prefix)).collect(Collectors.toList());
        }
        return List.of();
    }

    private void reloadConfig(CommandSender sender) {
        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BadwordFilter.reload(plugin);
        if (plugin.getSkinManager() != null) plugin.getSkinManager().reload();

        if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
            sender.sendMessage(Component.text("│  ⚠ 脏话过滤器已开启，但没有生效的词库来源 - 请开启"
                            + " 'badword-filter.use-global-list'，或将词语添加到"
                            + " config.yml / bad-words.yml！")
                    .color(YELLOW));
        }

        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) fpm.refreshCleanNamePool();

        sendStep(sender, "配置、语言、名称 (" + BotNameConfig.getNames().size() + ")、脏话过滤器");

        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH") && plugin.getConfigSyncManager() != null) {
            var csm = plugin.getConfigSyncManager();
            FppScheduler.runAsync(plugin, () -> {
                int pushed = csm.pushAll(sender.getName());
                FppScheduler.runSync(
                        plugin,
                        () -> sendStep(sender, "AUTO_PUSH: 已推送 " + pushed + " 个配置文件" + " 到网络"));
            });
        }
    }

    private void reloadLang(CommandSender sender) {
        Lang.reload();
        sendStep(sender, "语言文件已重载");
    }

    private void reloadAll(CommandSender sender) {

        Config.reload();
        Lang.reload();
        BotNameConfig.reload();
        BadwordFilter.reload(plugin);
        if (plugin.getSkinManager() != null) plugin.getSkinManager().reload();

        if (Config.isBadwordFilterEnabled() && BadwordFilter.getBadwordCount() == 0) {
            sender.sendMessage(Component.text("│  ⚠ 脏话过滤器已开启，但没有生效的词库来源 - 请开启"
                            + " 'badword-filter.use-global-list'，或将词语添加到"
                            + " config.yml / bad-words.yml！")
                    .color(YELLOW));
        }

        FakePlayerManager fpm = plugin.getFakePlayerManager();
        if (fpm != null) fpm.refreshCleanNamePool();

        sendStep(sender, "配置、语言、名称 (" + BotNameConfig.getNames().size() + ")、脏话过滤器");

        if (Config.configSyncMode().equalsIgnoreCase("AUTO_PUSH") && plugin.getConfigSyncManager() != null) {
            var csm = plugin.getConfigSyncManager();
            FppScheduler.runAsync(plugin, () -> {
                int pushed = csm.pushAll(sender.getName());
                FppScheduler.runSync(
                        plugin,
                        () -> sendStep(sender, "AUTO_PUSH: 已推送 " + pushed + " 个配置文件" + " 到网络"));
            });
        }

        if (fpm != null) {
            fpm.applyBodyConfig();
            int active = fpm.getCount();
            if (active > 0)
                sendStep(
                        sender,
                        active
                                + " 个活跃假人的状态已更新"
                                + "  （可受伤="
                                + Config.bodyDamageable()
                                + "，可推动="
                                + Config.bodyPushable()
                                + "）");
        }

        sendStep(sender, "LuckPerms - 通过 UserDataRecalculateEvent 自动更新");

        boolean taskPersistActive = Config.persistOnRestart() && plugin.getDatabaseManager() != null;
        String taskPersistDetail = taskPersistActive
                ? "数据库 + yaml  （模式 v" + DatabaseManager.getCurrentSchemaVersion() + "）"
                : Config.persistOnRestart() ? "仅 yaml  （数据库已禁用）" : "已禁用";
        sendStep(sender, "任务持久化 - " + taskPersistDetail);

        int issues = ConfigValidator.validate();
        if (issues > 0) {
            sender.sendMessage(Component.text("│  ⚠ 检测到 " + issues + " 个配置问题 - 请查看控制台")
                    .color(YELLOW));
        } else {
            sendStep(sender, "配置校验通过  （0 个问题）");
        }

        UpdateChecker.invalidateCache();
        UpdateChecker.check(plugin);
        sendStep(sender, "已触发更新检查  （异步）");
    }

    private void sendStep(CommandSender sender, String message) {
        sender.sendMessage(Component.text("│  ")
                .color(ACCENT)
                .append(Component.text("✓ ").color(GREEN))
                .append(Component.text(message).color(GRAY)));
    }
}