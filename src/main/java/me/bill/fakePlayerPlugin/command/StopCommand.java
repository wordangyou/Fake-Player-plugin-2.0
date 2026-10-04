package me.bill.fakePlayerPlugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;

/**
 * /fpp stop [&lt;bot&gt;|--all]
 * <p>
 * 立即取消一个假人或所有假人的全部活跃核心任务。
 */
public final class StopCommand implements FppCommand {

    private final FakePlayerManager manager;

    // 所有引用均可为空 - 在构造之后注入。
    @Nullable
    private MoveCommand moveCommand;

    @Nullable
    private LeftClickCommand leftClickCommand;

    @Nullable
    private RightClickCommand rightClickCommand;

    @Nullable
    private AttackCommand attackCommand;

    @Nullable
    private FindCommand findCommand;

    // ── 构造函数 ──────────────────────────────────────────────────────────────

    public StopCommand(@NotNull FakePlayerManager manager) {
        this.manager = manager;
    }

    // ── 依赖注入 ─────────────────────────────────────────────────────────────

    public void setMoveCommand(@Nullable MoveCommand cmd) {
        this.moveCommand = cmd;
    }

    public void setLeftClickCommand(@Nullable LeftClickCommand cmd) {
        this.leftClickCommand = cmd;
    }

    public void setRightClickCommand(@Nullable RightClickCommand cmd) {
        this.rightClickCommand = cmd;
    }

    public void setAttackCommand(@Nullable AttackCommand cmd) {
        this.attackCommand = cmd;
    }

    public void setFindCommand(@Nullable FindCommand cmd) {
        this.findCommand = cmd;
    }

    // ── FppCommand 元数据 ─────────────────────────────────────────────────────

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    public String getPermission() {
        return Perm.STOP;
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.has(sender, Perm.STOP);
    }

    @Override
    public String getUsage() {
        return "[<bot>|--all]";
    }

    @Override
    public String getDescription() {
        return "停止一个假人或所有假人的全部活跃任务。";
    }

    // ── 命令执行 ─────────────────────────────────────────────────────────────

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // 默认：不提供参数时停止全部。
        if (args.length == 0 || args[0].equalsIgnoreCase("--all")) {
            int stopped = stopAllBots();
            if (stopped == 0) {
                sender.sendMessage(Lang.get("stop-all-nothing"));
            } else {
                sender.sendMessage(Lang.get("stop-all-done", "count", String.valueOf(stopped)));
            }
            return true;
        }

        FakePlayer fp = manager.getByName(args[0]);
        if (fp == null) {
            sender.sendMessage(Lang.get("stop-not-found", "name", args[0]));
            return true;
        }

        boolean didAnything = stopBot(fp.getUuid());
        if (didAnything) {
            sender.sendMessage(Lang.get("stop-done", "name", fp.getDisplayName()));
        } else {
            sender.sendMessage(Lang.get("stop-nothing", "name", fp.getDisplayName()));
        }
        return true;
    }

    // ── 停止辅助方法 ─────────────────────────────────────────────────────────

    /**
     * 停止单个假人的所有正在运行的任务。
     *
     * @return 如果至少取消了一个任务则为 true。
     */
    private boolean stopBot(@NotNull UUID uuid) {
        boolean did = false;

        if (moveCommand != null) {
            moveCommand.cleanupBot(uuid);
            did = true;
        }
        if (leftClickCommand != null && leftClickCommand.isClicking(uuid)) {
            leftClickCommand.stopClicking(uuid);
            did = true;
        }
        if (rightClickCommand != null && rightClickCommand.isClicking(uuid)) {
            rightClickCommand.stopClicking(uuid);
            did = true;
        }
        if (attackCommand != null && attackCommand.isAttacking(uuid)) {
            attackCommand.stopAttacking(uuid);
            did = true;
        }
        if (findCommand != null && findCommand.isFinding(uuid)) {
            findCommand.cleanupBot(uuid);
            did = true;
        }

        return did;
    }

    /**
     * 停止所有活跃假人的全部正在运行的任务。
     *
     * @return 至少停止了一个任务的假人总数。
     */
    private int stopAllBots() {
        int count = 0;
        for (FakePlayer fp : new ArrayList<>(manager.getActivePlayers())) {
            if (stopBot(fp.getUuid())) count++;
        }
        return count;
    }

    // ── Tab 补全 ──────────────────────────────────────────────────────────────

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String in = args[0].toLowerCase();
            if ("--all".startsWith(in)) out.add("--all");
            for (FakePlayer fp : manager.getActivePlayers())
                if (fp.getName().toLowerCase().startsWith(in)) out.add(fp.getName());
        }
        return out;
    }
}