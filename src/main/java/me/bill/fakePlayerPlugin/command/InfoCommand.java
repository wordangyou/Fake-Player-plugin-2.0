package me.bill.fakePlayerPlugin.command;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.BotRecord;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.TextUtil;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class InfoCommand implements FppCommand {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final TextColor ACCENT = TextColor.fromHexString("#A78BFA");
    private static final TextColor LABEL = TextColor.fromHexString("#9691AB");
    private static final TextColor VALUE = TextColor.fromHexString("#EEECF7");
    private static final TextColor MUTED = TextColor.fromHexString("#5F5B73");
    private static final TextColor OK = TextColor.fromHexString("#BAFF4F");
    private static final TextColor ERR = TextColor.fromHexString("#FF6A5C");

    private final DatabaseManager db;
    private final FakePlayerManager manager;

    public InfoCommand(DatabaseManager db, FakePlayerManager manager) {
        this.db = db;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getUsage() {
        return "[bot|spawner] <name>";
    }

    @Override
    public String getDescription() {
        return "从数据库查询假人会话历史。";
    }

    @Override
    public String getPermission() {
        return Perm.INFO;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.INFO) || Perm.has(sender, Perm.USER_INFO);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean isAdmin = Perm.has(sender, Perm.INFO);
        boolean isUser = Perm.has(sender, Perm.USER_INFO);

        if (!isAdmin && !isUser) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (!isAdmin) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Lang.get("player-only"));
                return true;
            }
            if (args.length == 0) {

                showUserOwnBots(sender, player);
                return true;
            }

            showUserBotInfo(sender, player, args[0]);
            return true;
        }

        if (args.length == 0) {

            showAdminLiveBots(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if ((sub.equals("bot") || sub.equals("spawner")) && args.length < 2) {
            sender.sendMessage(Component.empty()
                    .append(Component.text("用法：").color(LABEL))
                    .append(Component.text("/fpp info " + sub + " <name>").color(ACCENT)));
            return true;
        }

        String name = args.length > 1 ? args[1] : args[0];

        FakePlayer live = manager.getActivePlayers().stream()
                .filter(fp -> fp.getName().equalsIgnoreCase(args[0]))
                .findFirst()
                .orElse(null);

        if (live != null && !sub.equals("bot") && !sub.equals("spawner")) {
            showAdminBotInfo(sender, live);
            return true;
        }

        if (db == null) {
            sender.sendMessage(Lang.get("info-db-unavailable"));
            return true;
        }

        switch (sub) {
            case "bot" -> showBotSessions(sender, name);
            case "spawner" -> showSpawnerSessions(sender, name);
            default -> showBotSessions(sender, args[0]);
        }
        return true;
    }

    private void showUserOwnBots(CommandSender sender, Player player) {
        List<FakePlayer> owned = manager.getActivePlayers().stream()
                .filter(fp -> BotAccess.canAdminister(player, fp))
                .toList();

        sender.sendMessage(header("你的假人"));
        if (owned.isEmpty()) {
            sender.sendMessage(Lang.get("list-none"));
            sender.sendMessage(divider());
            return;
        }
        for (FakePlayer fp : owned) {
            sender.sendMessage(Component.empty()
                    .append(Component.text("  ").color(MUTED))
                    .append(Component.text(fp.getDisplayName()).color(ACCENT))
                    .append(Component.text("  ⏱ ").color(MUTED))
                    .append(Component.text(formatUptime(fp.getSpawnTime())).color(VALUE))
                    .append(Component.text("  📍 ").color(MUTED))
                    .append(Component.text(manager.formatLocationForDisplay(fp)).color(VALUE)));
        }
        sender.sendMessage(divider());
    }

    private void showUserBotInfo(CommandSender sender, Player player, String input) {

        FakePlayer fp = manager.getActivePlayers().stream()
                .filter(b -> b.getName().equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);

        if (fp == null) {
            sender.sendMessage(Lang.get("info-no-records", "name", input));
            return;
        }

        if (!BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return;
        }

        sender.sendMessage(header("假人 - " + fp.getDisplayName()));
        row(sender, "世界", getWorld(fp));
        row(sender, "位置", manager.formatLocationForDisplay(fp));
        row(sender, "在线时长", formatUptime(fp.getSpawnTime()));
        row(sender, "生成者", fp.getSpawnedBy());
        sender.sendMessage(divider());
    }

    private void showAdminLiveBots(CommandSender sender) {
        Collection<FakePlayer> active = manager.getActivePlayers();

        sender.sendMessage(header("活跃假人（" + active.size() + "）"));

        if (active.isEmpty()) {
            sender.sendMessage(Component.empty()
                    .append(Component.text("  当前没有活跃的假人。").color(MUTED)));
        } else {
            for (FakePlayer fp : active) {
                sender.sendMessage(Component.empty()
                        .append(Component.text("  ").color(MUTED))
                        .append(Component.text(fp.getDisplayName()).color(ACCENT))
                        .append(Component.text("  ⏱ ").color(MUTED))
                        .append(Component.text(formatUptime(fp.getSpawnTime())).color(VALUE))
                        .append(Component.text("  📍 ").color(MUTED))
                        .append(Component.text(manager.formatLocationForDisplay(fp))
                                .color(VALUE))
                        .append(Component.text("  由 ").color(MUTED))
                        .append(Component.text(fp.getSpawnedBy()).color(LABEL)));
            }
        }

        if (db != null) {
            DatabaseManager.DbStats stats = db.getStats();
            sender.sendMessage(divider());
            sender.sendMessage(header("数据库统计（" + stats.backend() + "）"));
            row(sender, "模式", Config.databaseMode());
            row(sender, "服务器 ID", Config.serverId());
            row(sender, "总会话数", String.valueOf(stats.totalSessions()));
            row(sender, "独立假人数", String.valueOf(stats.uniqueBots()));
            row(sender, "独立生成者数", String.valueOf(stats.uniqueSpawners()));
            row(sender, "总在线时长", stats.formattedUptime());

            Map<String, Integer> top = db.getTopSpawners(3);
            if (!top.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                top.forEach((p, c) -> {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(p).append(" (").append(c).append(")");
                });
                row(sender, "生成最多者", sb.toString());
            }

            sender.sendMessage(Component.empty()
                    .append(Component.text("  使用 /fpp info <name> 或 /fpp info spawner" + " <name> 查看历史。")
                            .color(MUTED)));
        }
        sender.sendMessage(divider());
    }

    private void showAdminBotInfo(CommandSender sender, FakePlayer fp) {
        sender.sendMessage(header("假人 - " + fp.getDisplayName()));
        row(sender, "名称", fp.getDisplayName());
        row(sender, "内部名", fp.getName());
        row(sender, "UUID", fp.getUuid().toString());
        row(sender, "世界", getWorld(fp));
        row(sender, "位置", formatLoc(fp));
        row(sender, "在线时长", formatUptime(fp.getSpawnTime()));
        row(sender, "生成者", fp.getSpawnedBy());
        sender.sendMessage(divider());
    }

    private void showBotSessions(CommandSender sender, String botName) {

        FakePlayer live = manager.getActivePlayers().stream()
                .filter(fp -> fp.getName().equalsIgnoreCase(botName))
                .findFirst()
                .orElse(null);

        if (live != null) {
            showAdminBotInfo(sender, live);
        }

        String internalName = live != null ? live.getName() : botName;
        int limit = Config.dbMaxHistoryRows();
        List<BotRecord> records = db.getSessionsByBot(internalName, limit);
        if (records.isEmpty() && live == null) {
            sender.sendMessage(Lang.get("info-no-records", "name", botName));
            return;
        }
        if (records.isEmpty()) return;

        sender.sendMessage(header("会话历史 - " + botName + "（最近 " + records.size() + " 条）"));
        for (BotRecord r : records) {
            sender.sendMessage(sessionRow(r));
        }
        sender.sendMessage(divider());
    }

    private void showSpawnerSessions(CommandSender sender, String playerName) {
        int limit = Config.dbMaxHistoryRows();
        List<BotRecord> records = db.getSessionsBySpawner(playerName, limit);
        if (records.isEmpty()) {
            sender.sendMessage(Lang.get("info-no-records", "name", playerName));
            return;
        }
        sender.sendMessage(header("生成者历史 - " + playerName + "（最近 " + records.size() + " 条）"));
        for (BotRecord r : records) {
            sender.sendMessage(sessionRow(r));
        }
        sender.sendMessage(divider());
    }

    private Component header(String title) {
        return TextUtil.colorize("<dark_gray><st>━━━</st> <#A78BFA>" + title + "</#A78BFA> <dark_gray><st>━━━</st>");
    }

    private Component divider() {
        return TextUtil.colorize("<dark_gray><st>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</st>");
    }

    private void row(CommandSender s, String label, String value) {
        s.sendMessage(Component.empty()
                .append(Component.text("  " + label + ": ").color(LABEL))
                .append(Component.text(value).color(VALUE)));
    }

    private Component sessionRow(BotRecord r) {
        TextColor statusColor = r.isActive() ? OK : ERR;
        String status = r.isActive() ? "活跃" : (r.getRemoveReason() != null ? r.getRemoveReason() : "已移除");
        String spawned = FMT.format(r.getSpawnedAt());
        String loc = r.getWorldName() + " " + fmt(r.getSpawnX()) + "," + fmt(r.getSpawnY()) + "," + fmt(r.getSpawnZ());

        return Component.empty()
                .append(Component.text("  #" + r.getId() + " ").color(MUTED))
                .append(Component.text(r.getBotName() != null ? r.getBotName() : "?")
                        .color(ACCENT))
                .append(Component.text(" - ").color(MUTED))
                .append(Component.text(status).color(statusColor))
                .append(Component.text("  " + spawned + "  " + loc).color(LABEL));
    }

    private static String fmt(double v) {
        return String.format("%.0f", v);
    }

    private static String getWorld(FakePlayer fp) {
        var body = fp.getPhysicsEntity();
        if (body != null && body.isValid() && body.getLocation().getWorld() != null)
            return body.getLocation().getWorld().getName();
        var sl = fp.getSpawnLocation();
        if (sl != null && sl.getWorld() != null) return sl.getWorld().getName();
        return "未知";
    }

    private static String formatUptime(Instant t) {
        if (t == null) return "?";
        long s = Duration.between(t, Instant.now()).getSeconds();
        if (s < 60) return s + "秒";
        if (s < 3600) return (s / 60) + "分 " + (s % 60) + "秒";
        return (s / 3600) + "小时 " + ((s % 3600) / 60) + "分";
    }

    private static String formatLoc(FakePlayer fp) {
        var body = fp.getPhysicsEntity();
        if (body != null && body.isValid()) {
            var l = body.getLocation();
            return (l.getWorld() != null ? l.getWorld().getName() : "?")
                    + " "
                    + l.getBlockX()
                    + ","
                    + l.getBlockY()
                    + ","
                    + l.getBlockZ();
        }
        var sl = fp.getSpawnLocation();
        if (sl != null)
            return (sl.getWorld() != null ? sl.getWorld().getName() : "?")
                    + " "
                    + sl.getBlockX()
                    + ","
                    + sl.getBlockY()
                    + ","
                    + sl.getBlockZ();
        return "未知";
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        boolean isAdmin = Perm.has(sender, Perm.INFO);
        boolean isUser = Perm.has(sender, Perm.USER_INFO);
        if (!isAdmin && !isUser) return List.of();

        String current = args.length > 0 ? args[0] : "";
        String lower = current.toLowerCase();

        if (args.length <= 1) {
            List<String> suggestions = new ArrayList<>();

            if (isAdmin) {

                if ("bot".startsWith(lower)) suggestions.add("bot");
                if ("spawner".startsWith(lower)) suggestions.add("spawner");
                manager.getActivePlayers().stream()
                        .map(FakePlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(lower))
                        .forEach(suggestions::add);
            } else if (sender instanceof Player player) {

                manager.getActivePlayers().stream()
                        .filter(fp -> BotAccess.canAdminister(player, fp))
                        .map(FakePlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(lower))
                        .forEach(suggestions::add);
            }
            return suggestions;
        }

        if (args.length == 2 && isAdmin) {
            String sub = args[0].toLowerCase();
            String name = args[1].toLowerCase();
            if (sub.equals("bot")) {
                return manager.getActivePlayers().stream()
                        .map(FakePlayer::getName)
                        .filter(n -> n.toLowerCase().startsWith(name))
                        .toList();
            }
        }
        return List.of();
    }
}