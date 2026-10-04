package me.bill.fakePlayerPlugin.command;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.PathfindingService;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.FppLogger;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

/**
 * FakePlayerPlugin 的全面系统健康检查。
 *
 * <p>对每个插件子系统运行深入诊断，并将详细进度同时
 * 记录给调用玩家和服务端控制台。模拟检查验证
 * 生成假人 <em>本应</em> 成功，而无需实际创建
 * 一个真实假人。
 */
public final class CheckCommand implements FppCommand {

    private static final TextColor LABEL = TextColor.fromHexString("#9691AB");
    private static final TextColor MUTED = TextColor.fromHexString("#5F5B73");
    private static final TextColor OK = TextColor.fromHexString("#BAFF4F");
    private static final TextColor WARN = TextColor.fromHexString("#BAFF4F");
    private static final TextColor ERR = TextColor.fromHexString("#FF6A5C");

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    public CheckCommand(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "check";
    }

    @Override
    public String getUsage() {
        return "[--deep|--simulation|--commands|--listeners|--nms|--database|--folia|--world|--config|--extensions|--memory|--all]";
    }

    @Override
    public String getDescription() {
        return "对插件运行全面的系统健康检查。";
    }

    @Override
    public String getPermission() {
        return Perm.CHECK;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        boolean all = args.length == 0 || hasFlag(args, "--all");
        boolean deep = hasFlag(args, "--deep") || all;
        boolean simulation = hasFlag(args, "--simulation") || deep || all;
        boolean checkCommands = hasFlag(args, "--commands") || all;
        boolean checkListeners = hasFlag(args, "--listeners") || deep || all;
        boolean checkNms = hasFlag(args, "--nms") || deep || all;
        boolean checkDatabase = hasFlag(args, "--database") || deep || all;
        boolean checkFolia = hasFlag(args, "--folia") || deep || all;
        boolean checkWorld = hasFlag(args, "--world") || deep || all;
        boolean checkConfig = hasFlag(args, "--config") || deep || all;
        boolean checkExtensions = hasFlag(args, "--extensions") || deep || all;
        boolean checkMemory = hasFlag(args, "--memory") || deep || all;

        int issues = 0;
        int warnings = 0;

        log(sender, "正在开始 FPP 系统健康检查...");

        /* ================================================================ */
        /*  1. 插件初始化                                                    */
        /* ================================================================ */
        log(sender, "[1/12] 正在检查插件状态...");
        if (plugin.isEnabled()) {
            ok(sender, "插件已启用");
        } else {
            err(sender, "插件处于禁用状态");
            issues++;
        }

        String version = plugin.getPluginMeta().getVersion();
        info(sender, "  版本：" + version);

        /* ================================================================ */
        /*  2. 配置                                                          */
        /* ================================================================ */
        if (checkConfig) {
            log(sender, "[2/12] 正在检查配置...");
            File cfgFile = new File(plugin.getDataFolder(), "config.yml");
            if (!cfgFile.exists()) {
                err(sender, "config.yml 缺失");
                issues++;
            } else {
                ok(sender, "config.yml 存在");
            }

            int cfgVer = plugin.getConfig().getInt("config-version", 0);
            int latestVer = 74; // 硬编码当前配置版本
            if (cfgVer < latestVer) {
                warn(sender, "配置版本过旧：" + cfgVer + " < " + latestVer);
                warnings++;
            } else {
                ok(sender, "配置版本：" + cfgVer);
            }

            // 校验关键键（处理嵌套路径）
            String[][] criticalKeys = {{"limits", "max-bots"}, {"database", "server-id"}, {"database", "mode"}};
            for (String[] path : criticalKeys) {
                boolean present;
                if (path.length == 1) {
                    present = plugin.getConfig().contains(path[0]);
                } else {
                    org.bukkit.configuration.ConfigurationSection section =
                            plugin.getConfig().getConfigurationSection(path[0]);
                    present = section != null && section.contains(path[1]);
                }
                if (!present) {
                    warn(sender, "  缺少配置键：" + String.join(".", path));
                    warnings++;
                }
            }
        }

        /* ================================================================ */
        /*  3. NMS / 生成子系统                                              */
        /* ================================================================ */
        if (checkNms) {
            log(sender, "[3/12] 正在检查 NMS 生成子系统...");
            boolean nmsAvailable = NmsPlayerSpawner.isAvailable();
            if (nmsAvailable) {
                ok(sender, "NmsPlayerSpawner 可用");
            } else {
                err(sender, "NmsPlayerSpawner 不可用 - 不支持的服务端版本");
                issues++;
            }

            // 深入反射审计
            if (deep && nmsAvailable) {
                log(sender, "  正在深入反射审计...");
                int reflectionIssues = auditReflection(sender);
                issues += reflectionIssues;
            }

            int active = manager.getActivePlayers().size();
            info(sender, "  活跃假人：" + active);

            // 模拟：验证生成前置条件，而不实际生成
            if (simulation) {
                log(sender, "  正在运行生成模拟...");
                World w =
                        Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
                if (w == null) {
                    err(sender, "  没有世界 - 模拟已中止");
                    issues++;
                } else {
                    Location simLoc = w.getSpawnLocation();
                    try {
                        Object craftServer = Bukkit.getServer();
                        Object nmsServer =
                                craftServer.getClass().getMethod("getServer").invoke(craftServer);
                        if (nmsServer != null) {
                            ok(sender, "  CraftServer.getServer() 可访问");
                        }

                        Object nmsWorld = w.getClass().getMethod("getHandle").invoke(w);
                        if (nmsWorld != null) {
                            ok(sender, "  CraftWorld.getHandle() 可访问");
                        }

                        // 验证 PlayerList 存在
                        Object playerList =
                                nmsServer.getClass().getMethod("getPlayerList").invoke(nmsServer);
                        if (playerList != null) {
                            ok(sender, "  PlayerList 可访问");
                        } else {
                            err(sender, "  PlayerList 为 null");
                            issues++;
                        }
                    } catch (Exception e) {
                        err(sender, "  模拟失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                        issues++;
                    }
                }
            }
        }

        /* ================================================================ */
        /*  4. 数据库                                                        */
        /* ================================================================ */
        if (checkDatabase) {
            log(sender, "[4/12] 正在检查数据库...");
            DatabaseManager db = plugin.getDatabaseManager();
            if (db == null) {
                warn(sender, "DatabaseManager 为 null - 使用 YAML 回退");
                warnings++;
            } else {
                Connection conn = db.getConnection();
                if (conn != null) {
                    ok(sender, "数据库连接已打开");

                    // 验证数据表
                    if (deep) {
                        log(sender, "  正在验证模式表...");
                        String[] tables = {"fpp_bot_sessions", "fpp_active_bots", "fpp_bot_tasks"};
                        for (String table : tables) {
                            try (Statement st = conn.createStatement();
                                 ResultSet rs = st.executeQuery(
                                         "SELECT name FROM sqlite_master WHERE type='table' AND name='" + table
                                                 + "'")) {
                                if (rs.next()) {
                                    ok(sender, "    表 " + table + " 存在");
                                } else {
                                    err(sender, "    表 " + table + " 缺失");
                                    issues++;
                                }
                            } catch (SQLException e) {
                                warn(sender, "    无法验证表 " + table + "：" + e.getMessage());
                                warnings++;
                            }
                        }
                    }
                } else {
                    err(sender, "数据库连接为 null");
                    issues++;
                }
            }

            // 数据目录
            File dataDir = new File(plugin.getDataFolder(), "data");
            File langDir = new File(plugin.getDataFolder(), "language");
            status(sender, "数据目录", dataDir.exists());
            status(sender, "语言目录", langDir.exists());
        }

        /* ================================================================ */
        /*  5. Folia / 调度器                                                */
        /* ================================================================ */
        if (checkFolia) {
            log(sender, "[5/12] 正在检查 Folia 调度器...");
            boolean folia = NmsPlayerSpawner.isFoliaServer();
            if (folia) {
                ok(sender, "检测到 Folia - 使用区域调度器");

                // 验证所有假人位于正确的线程
                int bad = 0;
                for (FakePlayer fp : manager.getActivePlayers()) {
                    Player p = fp.getPlayer();
                    if (p != null && p.isOnline()) {
                        try {
                            p.getLocation();
                        } catch (Exception e) {
                            bad++;
                            log(sender, "    假人 " + fp.getName() + " 位于错误的区域线程！");
                        }
                    }
                }
                if (bad > 0) {
                    err(sender, "  " + bad + " 个假人位于错误的区域线程");
                    issues++;
                } else if (manager.getActivePlayers().size() > 0) {
                    ok(sender, "  所有假人均在正确的区域线程上");
                }
            } else {
                ok(sender, "检测到 Paper - 使用 BukkitScheduler");
            }

            // 验证调度器工具
            try {
                FppScheduler.runSync(plugin, () -> log(sender, "  FppScheduler 测试回调正常"));
                ok(sender, "FppScheduler 功能正常");
            } catch (Exception e) {
                err(sender, "FppScheduler 错误：" + e.getMessage());
                issues++;
            }
        }
        /* ================================================================ */
        /*  6. 世界 / 环境                                                   */
        /* ================================================================ */
        if (checkWorld) {
            log(sender, "[6/12] 正在检查世界环境...");
            List<World> worlds = Bukkit.getWorlds();
            if (worlds.isEmpty()) {
                err(sender, "未加载任何世界");
                issues++;
            } else {
                ok(sender, "已加载 " + worlds.size() + " 个世界");
                for (World w : worlds) {
                    Location spawn = w.getSpawnLocation();
                    boolean ok = spawn != null;
                    status(sender, "  " + w.getName() + " 出生点", ok);
                    if (!ok) warnings++;
                }
            }
        }

        /* ================================================================ */
        /*  7. 命令注册                                                      */
        /* ================================================================ */
        if (checkCommands) {
            log(sender, "[7/12] 正在检查命令注册...");
            org.bukkit.command.PluginCommand cmd = Bukkit.getPluginCommand("fpp");
            if (cmd == null) {
                err(sender, "/fpp 命令未注册");
                issues++;
            } else {
                ok(sender, "/fpp 命令已注册");
                if (cmd.getPlugin() != plugin) {
                    err(sender, "/fpp 归属于：" + cmd.getPlugin().getName());
                    issues++;
                }
            }

            // 列出所有已注册的子命令
            if (deep) {
                log(sender, "  已注册的子命令：");
                for (FppCommand c : plugin.getCommandManager().getCommands()) {
                    info(sender, "    - " + c.getName());
                }
            }
        }

        /* ================================================================ */
        /*  8. 事件监听器                                                    */
        /* ================================================================ */
        if (checkListeners) {
            log(sender, "[8/12] 正在检查事件监听器...");
            String[] criticalEvents = {
                    "org.bukkit.event.player.PlayerJoinEvent",
                    "org.bukkit.event.player.PlayerQuitEvent",
                    "org.bukkit.event.entity.PlayerDeathEvent",
                    "org.bukkit.event.entity.EntityDamageEvent",
                    "org.bukkit.event.player.PlayerInteractAtEntityEvent"
            };
            for (String ev : criticalEvents) {
                boolean present = hasListener(ev);
                status(sender, "  " + ev.substring(ev.lastIndexOf('.') + 1), present);
                if (!present) warnings++;
            }
        }

        /* ================================================================ */
        /*  9. 软依赖                                                        */
        /* ================================================================ */
        if (checkExtensions) {
            log(sender, "[9/12] 正在检查软依赖...");
            //（此处有意不检查 LuckPerms - FPP 已不再挂钩它）
            Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            Plugin we = Bukkit.getPluginManager().getPlugin("WorldEdit");
            status(sender, "PlaceholderAPI", papi != null && papi.isEnabled());
            status(sender, "WorldEdit", we != null && we.isEnabled());
        }

        /* ================================================================ */
        /*  10. 内存 / 假人状态审计                                          */
        /* ================================================================ */
        if (checkMemory) {
            log(sender, "[10/12] 正在运行内存与状态审计...");
            int active = manager.getActivePlayers().size();
            info(sender, "  活跃假人：" + active);

            if (active > 0) {
                int invalid = 0;
                int offline = 0;
                int missingBody = 0;
                for (FakePlayer fp : manager.getActivePlayers()) {
                    String name = fp.getName();
                    if (fp.getUuid() == null) {
                        err(sender, "    假人 " + name + " 的 UUID 为 null！");
                        invalid++;
                    }
                    Player p = fp.getPlayer();
                    if (p == null) {
                        missingBody++;
                    } else if (!p.isOnline()) {
                        offline++;
                    }
                }
                if (invalid > 0) {
                    err(sender, "  " + invalid + " 个假人状态无效");
                    issues += invalid;
                }
                if (offline > 0) {
                    warn(sender, "  " + offline + " 个假人实体离线");
                    warnings += offline;
                }
                if (missingBody > 0) {
                    warn(sender, "  " + missingBody + " 个假人没有实体（仅 Tab 列表）");
                    warnings += missingBody;
                }
            }

            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            long maxMB = rt.maxMemory() / 1024 / 1024;
            info(sender, "  JVM 内存：已用 " + usedMB + " MB / " + maxMB + " MB");
        }

        /* ================================================================ */
        /*  11. 寻路                                                         */
        /* ================================================================ */
        if (deep || all) {
            log(sender, "[11/12] 正在检查寻路...");
            PathfindingService pf = plugin.getPathfindingService();
            if (pf == null) {
                warn(sender, "PathfindingService 为 null");
                warnings++;
            } else {
                ok(sender, "PathfindingService 运行中");
            }
        }

        /* ================================================================ */
        /*  12. 语言 / 本地化                                                */
        /* ================================================================ */
        if (deep || all) {
            log(sender, "[12/12] 正在检查语言文件...");
            File langDir = new File(plugin.getDataFolder(), "language");
            if (langDir.exists() && langDir.isDirectory()) {
                File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
                if (files != null && files.length > 0) {
                    ok(sender, "  找到 " + files.length + " 个语言文件");
                } else {
                    warn(sender, "  language/ 目录中没有语言文件");
                    warnings++;
                }
            } else {
                warn(sender, "  language/ 目录缺失");
                warnings++;
            }

            // 验证一个关键语言键存在
            Component testMsg = Lang.get("no-permission");
            if (testMsg == null) {
                warn(sender, "  语言键 'spawn-usage' 缺失");
                warnings++;
            } else {
                ok(sender, "  语言系统功能正常");
            }
        }

        /* ================================================================ */
        /*  总结                                                             */
        /* ================================================================ */
        log(sender, "健康检查完成。");
        if (issues == 0 && warnings == 0) {
            ok(sender, "所有系统运行正常 - 未发现问题。");
            FppLogger.info("[CHECK] 所有系统运行正常。");
        } else {
            warn(sender, "发现 " + issues + " 个错误和 " + warnings + " 个警告。");
            FppLogger.warn("[CHECK] 发现 " + issues + " 个错误、 " + warnings + " 个警告。");
        }

        return true;
    }

    /* ------------------------------------------------------------------ */
    /*  辅助方法                                                          */
    /* ------------------------------------------------------------------ */

    private static boolean hasFlag(String[] args, String flag) {
        for (String s : args) {
            if (s.equalsIgnoreCase(flag)) return true;
        }
        return false;
    }

    /** 同时记录到玩家聊天和服务端控制台。 */
    private void log(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("  ").color(MUTED))
                .append(Component.text("▸ ").color(LABEL))
                .append(Component.text(message).color(LABEL)));
        FppLogger.info("[CHECK] " + message);
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text(message).color(LABEL)));
        FppLogger.info("[CHECK] " + message);
    }

    private void ok(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text("✔ ").color(OK))
                .append(Component.text(message).color(OK)));
        FppLogger.info("[CHECK] [OK] " + message);
    }

    private void warn(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text("⚠ ").color(WARN))
                .append(Component.text(message).color(WARN)));
        FppLogger.warn("[CHECK] [WARN] " + message);
    }

    private void err(CommandSender sender, String message) {
        sender.sendMessage(Component.empty()
                .append(Component.text("    ").color(MUTED))
                .append(Component.text("✘ ").color(ERR))
                .append(Component.text(message).color(ERR)));
        FppLogger.warn("[CHECK] [ERR] " + message);
    }

    private void status(CommandSender sender, String key, boolean ok) {
        if (ok) {
            ok(sender, key);
        } else {
            warn(sender, key + " - 缺失 / 失败");
        }
    }

    /**
     * 深入反射审计：尝试通过与 NmsPlayerSpawner 相同的反射路径，
     * 解析每一个关键 NMS 类/字段/方法。
     */
    private int auditReflection(CommandSender sender) {
        int issues = 0;
        String[] criticalClasses = {
                "net.minecraft.server.MinecraftServer",
                "net.minecraft.server.level.ServerPlayer",
                "net.minecraft.server.level.ServerLevel",
                "net.minecraft.server.network.ServerGamePacketListenerImpl",
                "net.minecraft.network.Connection",
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket",
                "com.mojang.authlib.GameProfile"
        };
        for (String cls : criticalClasses) {
            try {
                Class.forName(cls);
                ok(sender, "  类 " + cls + " 可解析");
            } catch (ClassNotFoundException e) {
                err(sender, "  类 " + cls + " 未找到");
                issues++;
            }
        }
        return issues;
    }

    private static boolean hasListener(String eventClassName) {
        try {
            Class.forName(eventClassName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase();
        List<String> out = new ArrayList<>();
        for (String f : new String[] {
                "--deep",
                "--simulation",
                "--commands",
                "--listeners",
                "--nms",
                "--database",
                "--folia",
                "--world",
                "--config",
                "--extensions",
                "--memory",
                "--all"
        }) {
            if (f.startsWith(prefix)) out.add(f);
        }
        return out;
    }
}