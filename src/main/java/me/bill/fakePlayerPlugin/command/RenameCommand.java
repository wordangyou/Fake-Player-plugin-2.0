package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.TextUtil;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * 重命名假人的 <em>显示名称</em> - 即头顶、Tab 列表和
 * 命令输出中显示的名字。登录名和 fb07 UUID（身份标识）永不改变，且
 * 名牌上强制性的 "ʙᴏᴛ ʙʏ {owner}" 披露行会保留。
 */
public final class RenameCommand implements FppCommand {

    /** 允许的最长显示名称（可见字符，已剥离颜色代码）- 保持名牌可读。 */
    private static final int MAX_NAME_LENGTH = 32;

    private final FakePlayerManager manager;

    public RenameCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "rename";
    }

    @Override
    public String getUsage() {
        return "<bot> <new name>";
    }

    @Override
    public String getDescription() {
        return "重命名假人的显示名称（身份标识不变）。";
    }

    @Override
    public String getPermission() {
        return Perm.RENAME;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Lang.get("rename-usage"));
            return true;
        }

        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("rename-not-found", "name", args[0]));
            return true;
        }

        if (sender instanceof Player player && !Perm.has(sender, Perm.ADMIN) && !BotAccess.canAdminister(player, fp)) {
            sender.sendMessage(Lang.get("no-permission"));
            return true;
        }

        String newName =
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        String plain = PlainTextComponentSerializer.plainText()
                .serialize(TextUtil.colorize(newName))
                .trim();
        if (plain.isEmpty()) {
            sender.sendMessage(Lang.get("rename-invalid"));
            return true;
        }
        if (plain.length() > MAX_NAME_LENGTH) {
            sender.sendMessage(Lang.get("rename-too-long", "max", String.valueOf(MAX_NAME_LENGTH)));
            return true;
        }

        String oldName = fp.getName();
        manager.renameBot(fp, newName);
        sender.sendMessage(Lang.get("rename-success", "name", oldName, "display", plain));
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!canUse(sender)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return manager.getActiveNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return List.of();
    }
}
