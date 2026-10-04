package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.economy.EconomyManager;
import me.bill.fakePlayerPlugin.economy.RentalPurchases;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;

/**
 * 假人租赁：用真实经济货币购买假人槽位和/或运行小时数，付费时间用完后
 * 假人会自动消失（这一点由 {@link me.bill.fakePlayerPlugin.fakeplayer.RentalService}
 * 单独强制执行）。
 *
 * <p>{@code buy}/{@code extend} 是自助、从经济系统扣费的路径。{@code give} 是
 * <em>动态</em> 入口：它完全不接触经济插件，因此服务器自家的商店插件
 * （ShopGUIPlus、EconomyShopGUI、zShop……）可自行向玩家收费，只需把
 * {@code fpp rent give <player> --new <hours>} 作为购买奖励命令执行 -
 * 从控制台运行，服务端完全无需接入 FPP 经济系统。
 */
public final class RentCommand implements FppCommand {

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public RentCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "rent";
    }

    @Override
    public String getUsage() {
        return "buy <hours>  |  extend <bot> <hours>  |  info [bot]  |  give <player> <bot|--new> <hours>";
    }

    @Override
    public String getDescription() {
        return "用真实经济货币租赁假人，按小时计费。";
    }

    @Override
    public String getPermission() {
        return Perm.RENT;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.hasAny(sender, Perm.RENT, Perm.RENT_INFO, Perm.RENT_GIVE);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.get("rent-usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "buy" -> buy(sender, rest);
            case "extend" -> extend(sender, rest);
            case "info" -> info(sender, rest);
            case "give" -> give(sender, rest);
            case "clear" -> clear(sender, rest);
            default -> {
                sender.sendMessage(Lang.get("rent-usage"));
                yield true;
            }
        };
    }

    // ── buy ──────────────────────────────────────────────────────────────────────────────────

    private boolean buy(CommandSender sender, String[] args) {
        if (!Perm.has(sender, Perm.RENT)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Lang.get("rent-usage"));
            return true;
        }

        Integer hours = parseHours(sender, args[0]);
        if (hours == null) return true;

        if (!player.hasPermission(Perm.RENT_UNLIMITED)) {
            int rentedCount = (int) manager.getBotsOwnedBy(player.getUniqueId()).stream()
                    .filter(FakePlayer::isRented)
                    .count();
            int cap = Config.rentalMaxBotsPerPlayer();
            if (rentedCount >= cap) {
                sender.sendMessage(Lang.get("rent-limit-reached", "limit", String.valueOf(cap)));
                return true;
            }
        }

        EconomyManager economy = plugin.getEconomyManager();
        if (economy == null || !economy.isAvailable()) {
            sender.sendMessage(Lang.get("rent-economy-unavailable"));
            return true;
        }

        double cost = Config.rentalPricePerBotSlot() + Config.rentalPricePerHour() * hours;
        if (!economy.has(player, cost)) {
            sender.sendMessage(Lang.get(
                    "rent-insufficient-funds",
                    "cost",
                    economy.format(cost),
                    "balance",
                    economy.format(economy.getBalance(player))));
            return true;
        }
        if (!economy.withdraw(player, cost)) {
            sender.sendMessage(Lang.get("rent-transaction-failed"));
            return true;
        }

        Set<UUID> before = ownedUuids(player);
        Location location = player.getLocation().clone();
        int result = manager.spawn(location, 1, player, null, false);
        if (result <= 0) {
            economy.deposit(player, cost); // 退款 - 实际上什么都没生成
            sender.sendMessage(Lang.get("rent-spawn-failed"));
            return true;
        }

        FakePlayer fp = findNewBot(player, before);
        if (fp == null) {
            // 极不可能发生（生成报告成功但找不到假人）- 退款，
            // 而不是白白扣费。
            economy.deposit(player, cost);
            sender.sendMessage(Lang.get("rent-spawn-failed"));
            return true;
        }

        long expiresAt = System.currentTimeMillis() + hours * 3_600_000L;
        RentalPurchases.setRentalExpiry(plugin, fp, expiresAt);
        Config.debugRental("为 " + player.getName() + " 购买了租赁假人 '" + fp.getName() + "'（" + hours
                + " 小时，费用=" + economy.format(cost) + "）");
        sender.sendMessage(Lang.get(
                "rent-bought",
                "name",
                fp.getDisplayName(),
                "hours",
                String.valueOf(hours),
                "cost",
                economy.format(cost)));
        return true;
    }

    // ── extend ───────────────────────────────────────────────────────────────────────────────

    private boolean extend(CommandSender sender, String[] args) {
        if (!Perm.has(sender, Perm.RENT)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Lang.get("rent-usage"));
            return true;
        }

        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("rent-bot-not-found", "name", args[0]));
            return true;
        }
        if (!Perm.has(sender, Perm.ADMIN) && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        Integer hours = parseHours(sender, args[1]);
        if (hours == null) return true;

        RentalPurchases.Result result = RentalPurchases.extend(plugin, player, fp, hours);
        sender.sendMessage(result.message());
        return true;
    }

    // ── info ─────────────────────────────────────────────────────────────────────────────────

    private boolean info(CommandSender sender, String[] args) {
        if (!Perm.hasAny(sender, Perm.RENT_INFO, Perm.RENT, Perm.ADMIN)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        if (args.length >= 1) {
            FakePlayer fp = manager.getByName(args[0]);
            if (fp == null) {
                sender.sendMessage(Lang.get("rent-bot-not-found", "name", args[0]));
                return true;
            }
            if (sender instanceof Player player
                    && !Perm.has(sender, Perm.ADMIN)
                    && !BotAccess.canAdminister(player, fp)) {
                sender.sendMessage(Lang.get("no-permission"));
                return true;
            }
            sendInfoLine(sender, fp);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.get("player-only"));
            return true;
        }
        List<FakePlayer> owned = manager.getBotsOwnedBy(player.getUniqueId());
        boolean any = false;
        for (FakePlayer fp : owned) {
            if (!fp.isRented()) continue;
            sendInfoLine(sender, fp);
            any = true;
        }
        if (!any) sender.sendMessage(Lang.get("rent-info-none"));
        return true;
    }

    private void sendInfoLine(CommandSender sender, FakePlayer fp) {
        if (!fp.isRented()) {
            sender.sendMessage(Lang.get("rent-info-permanent", "name", fp.getDisplayName()));
            return;
        }
        long remaining = RentalPurchases.currentExpiry(fp) - System.currentTimeMillis();
        sender.sendMessage(Lang.get(
                "rent-info-line",
                "name",
                fp.getDisplayName(),
                "remaining",
                RentalPurchases.formatRemaining(remaining)));
    }

    // ── give（动态 / 商店插件 / 管理员入口） ────────────────────────────────────────

    private boolean give(CommandSender sender, String[] args) {
        if (!Perm.has(sender, Perm.RENT_GIVE)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Lang.get("rent-usage"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(args[0]);
        if (target == null || (target.getName() == null && !target.hasPlayedBefore())) {
            sender.sendMessage(Lang.get("rent-player-not-found", "name", args[0]));
            return true;
        }

        Integer hours = parseHours(sender, args[2]);
        if (hours == null) return true;

        FakePlayer fp;
        if ("--new".equalsIgnoreCase(args[1])) {
            Player online = target.getPlayer();
            if (online == null || !online.isOnline()) {
                sender.sendMessage(Lang.get("rent-give-needs-online", "name", args[0]));
                return true;
            }
            Set<UUID> before = ownedUuids(online);
            int result = manager.spawn(online.getLocation().clone(), 1, online, null, false);
            if (result <= 0) {
                sender.sendMessage(Lang.get("rent-spawn-failed"));
                return true;
            }
            fp = findNewBot(online, before);
            if (fp == null) {
                sender.sendMessage(Lang.get("rent-spawn-failed"));
                return true;
            }
        } else {
            fp = manager.getByName(args[1]);
            if (fp == null) {
                sender.sendMessage(Lang.get("rent-bot-not-found", "name", args[1]));
                return true;
            }
        }

        long base = Math.max(System.currentTimeMillis(), RentalPurchases.currentExpiry(fp));
        long cap = System.currentTimeMillis() + Config.rentalMaxBankedHours() * 3_600_000L;
        long finalExpiry = Math.min(base + hours * 3_600_000L, cap);
        RentalPurchases.setRentalExpiry(plugin, fp, finalExpiry);

        Config.debugRental("通过 /fpp rent give 向 " + target.getName() + " 授予了 '" + fp.getName() + "' 的 " + hours
                + " 小时租赁（发送者=" + sender.getName() + "）");
        sender.sendMessage(Lang.get(
                "rent-given",
                "name",
                fp.getDisplayName(),
                "player",
                String.valueOf(target.getName()),
                "hours",
                String.valueOf(hours)));

        Player online = target.getPlayer();
        if (online != null && online.isOnline()) {
            online.sendMessage(Lang.get(
                    "rent-received",
                    "name",
                    fp.getDisplayName(),
                    "hours",
                    String.valueOf(hours),
                    "remaining",
                    RentalPurchases.formatRemaining(finalExpiry - System.currentTimeMillis())));
        }
        return true;
    }

    // ── clear（管理员：把租赁假人恢复为永久） ────────────────────────────────────────

    private boolean clear(CommandSender sender, String[] args) {
        if (!Perm.has(sender, Perm.RENT_GIVE)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Lang.get("rent-usage"));
            return true;
        }
        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("rent-bot-not-found", "name", args[0]));
            return true;
        }
        RentalPurchases.setRentalExpiry(plugin, fp, null);
        sender.sendMessage(Lang.get("rent-cleared", "name", fp.getDisplayName()));
        return true;
    }

    // ── 共享辅助方法 ───────────────────────────────────────────────────────────────────────

    @org.jetbrains.annotations.Nullable
    private Integer parseHours(CommandSender sender, String raw) {
        int hours;
        try {
            hours = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            sender.sendMessage(Lang.get("rent-invalid-hours", "value", raw));
            return null;
        }
        int min = Config.rentalMinHours();
        int max = Config.rentalMaxHours();
        if (hours < min || hours > max) {
            sender.sendMessage(
                    Lang.get("rent-hours-out-of-range", "min", String.valueOf(min), "max", String.valueOf(max)));
            return null;
        }
        return hours;
    }

    private Set<UUID> ownedUuids(Player player) {
        Set<UUID> set = new HashSet<>();
        for (FakePlayer fp : manager.getBotsOwnedBy(player.getUniqueId())) set.add(fp.getUuid());
        return set;
    }

    /** {@code owner} 列表中不在 {@code before} 里的那个假人 - 即刚刚生成的那个。 */
    @org.jetbrains.annotations.Nullable
    private FakePlayer findNewBot(Player owner, Set<UUID> before) {
        for (FakePlayer fp : manager.getBotsOwnedBy(owner.getUniqueId())) {
            if (!before.contains(fp.getUuid())) return fp;
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private OfflinePlayer resolveOfflinePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : List.of("buy", "extend", "info", "give", "clear")) {
                if (s.startsWith(prefix)) out.add(s);
            }
            return out;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("extend") || sub.equals("info") || sub.equals("clear")) && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (FakePlayer fp : manager.getActivePlayers()) {
                if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
            }
            return out;
        }

        if (sub.equals("give")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
                }
                return out;
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                if ("--new".startsWith(prefix)) out.add("--new");
                for (FakePlayer fp : manager.getActivePlayers()) {
                    if (fp.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(fp.getName());
                }
                return out;
            }
        }

        return List.of();
    }
}
