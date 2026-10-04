package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.ArrayList;
import java.util.List;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;

/**
 * 通过直接查询每个任务命令自身的“该假人是否正处于活动状态”来实时计算假人当前正在做什么的
 * 人类可读标签 - 没有单独的跟踪状态会失步，因为它在渲染时始终反映当前事实。
 */
public final class BotActivity {

    private BotActivity() {}

    public static String currentLabel(FakePlayer fp) {
        FakePlayerPlugin plugin = FakePlayerPlugin.getInstance();
        if (plugin == null || fp == null) return "空闲";

        java.util.UUID uuid = fp.getUuid();
        List<String> labels = new ArrayList<>();

        if (fp.isAuthPending()) {
            labels.add("认证中");
        } else if (fp.isFrozen()) {
            labels.add("已冻结");
        }

        PathfindingService pathfinding = plugin.getPathfindingService();
        if (pathfinding != null && pathfinding.isNavigating(uuid)) {
            PathfindingService.Owner owner = pathfinding.getOwner(uuid);
            labels.add(navLabel(owner));
        }

        var autoEat = plugin.getAutoEatController();
        if (autoEat != null && autoEat.isEating(uuid)) labels.add("进食中");

        var pve = plugin.getPveController();
        if (pve != null && pve.isEngaged(uuid)) labels.add("战斗中");

        var leftClick = plugin.getLeftClickCommand();
        if (leftClick != null && leftClick.isClicking(uuid)) labels.add("挖掘/攻击");

        var rightClick = plugin.getRightClickCommand();
        if (rightClick != null && rightClick.isClicking(uuid)) labels.add("使用物品");

        var attack = plugin.getAttackCommand();
        if (attack != null && attack.isAttacking(uuid)) labels.add("攻击中");

        var find = plugin.getFindCommand();
        if (find != null && find.isFinding(uuid)) labels.add("搜索中");

        var body = fp.getPlayer();
        if (body != null && body.isOnline() && body.isSneaking()) labels.add("潜行中");

        if (labels.isEmpty()) return "空闲";
        return String.join(" · ", new java.util.LinkedHashSet<>(labels));
    }

    private static String navLabel(PathfindingService.Owner owner) {
        if (owner == null) return "移动中";
        return switch (owner) {
            case MOVE -> "移动中";
            case MINE -> "挖掘中";
            case PLACE -> "建造中";
            case USE -> "使用物品";
            case ATTACK -> "追击目标";
            case FIND -> "搜索中";
            case SYSTEM -> "移动中";
        };
    }
}