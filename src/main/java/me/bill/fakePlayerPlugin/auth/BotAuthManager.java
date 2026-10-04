package me.bill.fakePlayerPlugin.auth;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import me.bill.fakePlayerPlugin.compat.nlogin.NLoginIntegration;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.database.DatabaseManager;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.network.FakeServerGamePacketListenerImpl;
import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * 让假人配合服务端的登录验证插件（nLogin、AuthMe、LoginSecurity，或其他基于相同
 * {@code /register <password> <password>} / {@code /login <password>} 命令形式的插件 -
 * 见 {@link AuthPluginDetector}），而不是像没输入过密码的真实玩家那样，干坐在那里
 * 未认证、无法移动/查看世界。
 *
 * <p><b>整个流程由 {@code FakePlayerManager} 调用</b>，就在假人注册进其
 * {@code activePlayers} 映射之后 - 特意不用 {@code PlayerJoinEvent} 监听器：假人生成时
 * 自身触发的 join（通过 NMS 放置）发生在该注册之前，所以监听器此时检查
 * {@code FakePlayerManager#getByName} 仍会把假人当成未知。每次真正加入时都会调用 -
 * 无论是新的 {@code /fpp spawn}，还是重启后由持久化静默恢复的假人：
 * <ol>
 *   <li>将假人标记为 {@link FakePlayer#setAuthPending 待认证} - tick 循环会像对待
 *       尚未认证的真实玩家一样将其冻结（不移动、不转头），直到本次认证以某种方式
 *       有了结果（见 {@link #armOutcomeDetection}）。
 *   <li>查询该假人名字对应的 {@code fpp_bot_auth} 记录。
 *   <li>没有记录 -&gt; 这是假人第一次加入。生成新密码
 *       （{@link SmartPasswordGenerator}），<b>在发送任何内容之前先加密持久化</b>
 *       （这样即使注册命令刚发出就崩溃，密码仍可恢复），然后在随机的人性化延迟后，
 *       以假人身份派发 {@code auth.register-command}。
 *   <li>已存在记录 -&gt; 解密并派发 {@code auth.login-command}，同样的延迟。
 *       这就是“记住密码直接登录而不是重新注册”的行为。
 * </ol>
 *
 * <p>命令的发送方式是完全重放真实客户端输入聊天命令的两步流程 - 见
 * {@link #runAsRealCommand} - 而不是裸调用
 * {@link Bukkit#dispatchCommand(org.bukkit.command.CommandSender, String)}。这个区别很关键：
 * 大多数登录插件（包括 nLogin）直接挂钩 {@link PlayerCommandPreprocessEvent}，
 * 而不是把 "register"/"login" 注册为正式的 Bukkit 命令 - 部分原因是为了拦截未认证玩家
 * 尝试执行的所有<i>其他</i>命令。仅调用 {@code dispatchCommand} 会完全跳过该事件，
 * 导致假人的登录尝试根本不会到达这类插件：它会永远处于未认证状态（无法受伤、无法真正
 * 移动等 - 取决于该插件限制什么），而本插件却以为命令已“成功”执行。先触发事件，
 * 就像真实客户端的聊天数据包一样，意味着无论登录插件使用两种集成方式中的哪一种，
 * 这都能生效 - 而且完全不依赖该插件的编译期依赖。
 *
 * <p><b>读取插件自身的回复</b>（{@link #armOutcomeDetection}）：假人玩家的连接会静默
 * 丢弃真实客户端通常会渲染的所有数据包，包括插件通过 {@code Player#sendMessage} 发回的
 * 任何聊天消息 - 所以没有辅助手段的话，本插件无从得知返回的是“密码错误”还是
 * “欢迎回来！”。{@link FakeServerGamePacketListenerImpl#listen} 在该类数据包被丢弃前
 * 截获它。每条捕获的消息都会记录到日志（这是真正有用的排查信号 - 当认证似乎不生效时
 * 查阅它），并对其做一个很小、刻意保守的关键词匹配，一旦出现明确的成功信号就提前解除
 * 假人冻结。这本质上是尽力而为 - 具体措辞因插件和语言环境而异 - 所以
 * {@code auth.pending-timeout-ticks} 才是真正的兜底：时间一到，无论结果如何都会解除
 * 冻结，因此本插件未能识别的消息（或完全没有响应）也绝不会让它永远冻结。
 *
 * <p><b>以上所有内容有一个例外：nLogin。</b> {@link #scheduleDispatch} 会先检查
 * {@link NLoginIntegration#tryInstall}，如果安装了 nLogin，就直接调用它自己的公共 API
 * （{@link NLoginIntegration#performRegister}/{@link NLoginIntegration#forceLogin}），
 * 而不再模拟命令。这不是可选的润色 - 实机确认过，nLogin 的命令路径对假人每次都会抛出
 * {@code IllegalStateException: Player session not set}，因为它实时的“已认证”状态保存在
 * 一个按连接创建的会话对象上，而该对象只有在其捆绑的 PacketEvents 处理<i>真实</i>客户端的
 * 登录/配置数据包时才会创建，假人的生成从不经过那里。它自己 API 的 {@code forceLogin}
 * 是 nLogin 官方认可的绕过该缺口的途径 - 完整背景见 {@link NLoginIntegration} 的类文档。
 */
public final class BotAuthManager {

    // 刻意使用保守的多词短语，而不是 "welcome" 或 "success" 之类的单个词 -
    // 登录插件对未认证玩家自己的提示（"please register"、"welcome, log
    // in to continue"）很容易单独包含这些词。永远不可能穷尽 - 原因见类文档：
    // 真正的保证是硬超时，而不是这个列表。
    private static final List<String> POSITIVE_HINTS = List.of(
            "logged in successfully",
            "successfully logged in",
            "you are now logged in",
            "successfully registered",
            "registration successful",
            "successfully register",
            "authentication successful",
            "welcome back");
    private static final List<String> NEGATIVE_HINTS = List.of(
            "wrong password",
            "incorrect password",
            "invalid password",
            "already registered",
            "not registered",
            "too short",
            "too long",
            "too weak",
            "please register",
            "please login",
            "please log in");

    private final Plugin plugin;
    private final DatabaseManager database;
    private final AuthCipher cipher;

    public BotAuthManager(Plugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.cipher = new AuthCipher(plugin);
        if (Config.authEnabled()) {
            // 延迟一个 tick，而不是在构造函数里立即检查 - 这里运行于插件启动期间，
            // 此时其他插件可能尚未完成各自的 onEnable（无关插件之间的加载顺序没有保证），
            // 所以立即检测很容易漏掉一个只是还没加载完的登录插件。一个 tick 之后，
            // Bukkit 已完成所有插件启用。
            Bukkit.getScheduler().runTask(plugin, () -> {
                String detected = AuthPluginDetector.detect();
                FppLogger.success("Auth: 已启用 - "
                        + (detected != null
                                ? "检测到 " + detected + "。"
                                : "尚未检测到登录插件；无论是否检测到，都将按配置使用 "
                                        + "auth.register-command/auth.login-command。"));
            });
        }
    }

    /** {@link AuthPluginDetector} 当前认为已安装的登录插件，没有则为 {@code null} - 每次调用都重新计算、不缓存，因为插件可能在 {@code /fpp reload} 过程中（卸载/重）出现。 */
    public String detectedPlugin() {
        return AuthPluginDetector.detect();
    }

    /**
     * 假人真正加入时的入口 - 完整流程见类文档。若 {@code auth.enabled} 关闭或数据库不可用，
     * 则不执行任何操作（没有数据库就无法记住凭据，而每次重启/重连都重新注册一个新密码
     * 比什么都不做更糟）。
     */
    public void handleBotJoin(FakePlayer bot) {
        if (!Config.authEnabled()) return;
        bot.setAuthPending(true);
        if (database == null || !database.isConnectionValid()) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: 已启用，但数据库不可用 - 无法记住假人 "
                    + "凭据，因此跳过 '" + bot.getName() + "' 的自动登录。请在 config.yml 中启用 database.enabled "
                    + "以使用该功能。");
            return;
        }

        String name = bot.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager.BotAuthRow row = database.getBotAuth(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (row != null) {
                    loginExisting(bot, row);
                } else {
                    registerNew(bot);
                }
            });
        });
    }

    private void loginExisting(FakePlayer bot, DatabaseManager.BotAuthRow row) {
        String password;
        try {
            password = cipher.decrypt(row.passwordEncrypted());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: 无法解密 '" + bot.getName() + "' 存储的密码 ("
                    + e.getMessage() + ") - 跳过登录。请执行 /fpp auth reset " + bot.getName()
                    + " 清除它，这样下次加入时会重新注册一个新密码。");
            return;
        }
        FppLogger.debug("AUTH", Config.debugAuth(), "'" + bot.getName() + "' 存在已存储的密码 - 正在登录。");
        scheduleDispatch(bot, AuthAction.LOGIN, Config.authLoginCommand(), password);
    }

    private void registerNew(FakePlayer bot) {
        String password = SmartPasswordGenerator.generate();
        String encrypted;
        try {
            encrypted = cipher.encrypt(password);
        } catch (GeneralSecurityException e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: 无法为 '" + bot.getName() + "' 加密新密码 (" + e.getMessage()
                    + ") - 跳过注册。");
            return;
        }
        // 刻意在注册命令派发之前写入 - 如果服务器在这两步之间崩溃或假人被踢出，
        // 告诉假人的密码仍在记录中，供其下次加入时重试（从它的角度看，两种方式相同）
        // 注册调用，而不是丢失并被迫手动执行 /fpp auth setpassword。
        database.upsertBotAuth(bot.getName(), encrypted, () -> {
            FppLogger.debug(
                    "AUTH",
                    Config.debugAuth(),
                    "'" + bot.getName() + "' 没有已存储的密码 - 正在注册新密码。");
            scheduleDispatch(bot, AuthAction.REGISTER, Config.authRegisterCommand(), password);
        });
    }

    private enum AuthAction {
        REGISTER,
        LOGIN
    }

    private void scheduleDispatch(FakePlayer bot, AuthAction action, String commandTemplate, String password) {
        int minTicks = Config.authDelayMinTicks();
        int maxTicks = Config.authDelayMaxTicks();
        long delay = minTicks >= maxTicks
                ? minTicks
                : minTicks + ThreadLocalRandom.current().nextInt(maxTicks - minTicks + 1);

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            Player player = bot.getPlayer();
                            if (player == null || !player.isOnline()) {
                                bot.setAuthPending(false);
                                return;
                            }

                            // 见类文档 - nLogin 自己的 API 绕过了下方通用命令模拟路径中的硬性阻塞，
                            // 所以安装了 nLogin 时它始终优先。
                            NLoginIntegration nLogin = NLoginIntegration.tryInstall();
                            if (nLogin != null) {
                                runViaNLoginApi(bot, nLogin, action, password);
                                return;
                            }

                            String command = commandTemplate.replace("%password%", password);
                            if (command.startsWith("/")) command = command.substring(1);
                            String finalCommand = command;
                            FppLogger.debug(
                                    "AUTH",
                                    Config.debugAuth(),
                                    "'" + bot.getName() + "' 正在执行认证命令："
                                            + finalCommand.replace(password, "*".repeat(password.length())));

                            runAsRealCommand(bot, player, command);
                        },
                        delay);
    }

    // 在 runViaNLoginApi 首次真正失败时置位，这样后续每个假人不会重复打印
    // 同样长篇的解释 - 见该方法自身的文档。
    private volatile boolean nLoginLimitationExplained = false;

    /**
     * 通过 nLogin 自己的 API 认证 {@code bot}，而不是模拟命令 - 命令路径为何对 nLogin
     * 完全无效见类文档。注册时会在 {@code performRegister} 之后额外调用 {@code forceLogin}，
     * 因为（实机确认）仅注册只创建账号，本身并不会把实时连接切换为已认证。
     *
     * <p>在某些 nLogin 构建/配置上这仍会直接失败（实机确认：甚至连 {@code forceLogin}
     * 本身也可能抛出与其他所有入口相同的 {@code Player session not set} - nLogin 的整个
     * 认证面，包括其 API，都汇聚到一个只有它自己处理真实登录数据包时才会创建的会话对象，
     * 而假人的生成从不经过那里）。此后没有更多回退 - 假人的冻结仍会解除
     * （见 {@link FakePlayer#setAuthPending}），以免它永远僵在那里，但 nLogin <i>自身</i>
     * 的限制（无法受伤、最终“超时”踢出）仍会对其生效，因为这些完全由 nLogin 一侧
     * 强制执行，与我们无关。
     */
    private void runViaNLoginApi(FakePlayer bot, NLoginIntegration nLogin, AuthAction action, String password) {
        String name = bot.getName();
        boolean ok = action == AuthAction.REGISTER
                ? nLogin.performRegister(name, password) && nLogin.forceLogin(name)
                : nLogin.forceLogin(name);
        if (ok) {
            FppLogger.debug(
                    "AUTH",
                    Config.debugAuth(),
                    "'" + name + "' 已直接通过 nLogin 的 API 完成认证 (" + action + ")。");
        } else if (!nLoginLimitationExplained) {
            nLoginLimitationExplained = true;
            FppLogger.warn("Auth: nLogin 的 API 拒绝了 '" + name + "' 的 " + action + " - 而且很可能对"
                    + "其他所有假人也一样：在这个 nLogin 构建/配置下，它的整个"
                    + "认证面（命令和它自己的 API）都需要一个按连接创建的"
                    + "会话，而该会话只在处理真实客户端的实际登录数据包时创建，"
                    + "假人从不发送这类数据包。从这里没有受支持的绕过方式 - 这个假人（以及"
                    + "未来的假人）会保持先冻结后释放，但仍受 nLogin 自身限制"
                    + "（无法受伤、最终登录超时踢出）。如果你想"
                    + "让假人专门豁免于它的登录墙，nLogin 自己的配置有一个 "
                    + "'bypass authentication for these nicknames' 列表（Security 部分）。");
        } else {
            FppLogger.warn("Auth: nLogin 的 API 拒绝了 '" + name
                    + "' 的 " + action + "（已知的同一限制 - 见之前的警告）。");
        }
        bot.setAuthPending(false);
    }

    /**
     * 发送 {@code command}（不带前导斜杠），方式与真实客户端输入的 "/command"
     * 聊天行到达服务端完全相同 - 为什么裸调用 {@link Bukkit#dispatchCommand}
     * 不够见类文档。第 1 步：以假人为发送者构建并触发
     * {@link PlayerCommandPreprocessEvent}，与原版处理真实的聊天命令数据包一致。
     * 如果任何监听器取消了它 - 直接挂钩该事件的登录插件的预期行为 - 就视为已处理，
     * 并启动结果检测。第 2 步：只有无人取消时，才真正派发（可能已被监听器修改的）
     * 命令，与原版接下来的行为一致。
     */
    private void runAsRealCommand(FakePlayer bot, Player player, String command) {
        PlayerCommandPreprocessEvent preEvent = new PlayerCommandPreprocessEvent(player, "/" + command);
        try {
            Bukkit.getPluginManager().callEvent(preEvent);
        } catch (Exception e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: PlayerCommandPreprocessEvent 对 '" + bot.getName() + "' 抛出异常：" + e.getMessage());
            return;
        }
        if (preEvent.isCancelled()) {
            FppLogger.debug(
                    "AUTH",
                    Config.debugAuth(),
                    "'" + bot.getName()
                            + "' 的认证命令被 PlayerCommandPreprocessEvent 监听器拦截了 "
                            + "（几乎可以肯定是登录插件本身）- 正在等待其响应。");
            armOutcomeDetection(bot);
            return;
        }

        String dispatchedCommand = preEvent.getMessage();
        if (dispatchedCommand.startsWith("/")) dispatchedCommand = dispatchedCommand.substring(1);

        boolean dispatched;
        try {
            dispatched = Bukkit.dispatchCommand(player, dispatchedCommand);
        } catch (Exception e) {
            bot.setAuthPending(false);
            FppLogger.warn("Auth: 命令派发对 '" + bot.getName() + "' 抛出异常：" + e.getMessage());
            return;
        }
        if (!dispatched) {
            bot.setAuthPending(false);
            String firstWord =
                    dispatchedCommand.isBlank() ? "" : dispatchedCommand.trim().split("\\s+", 2)[0];
            FppLogger.warn("Auth: 没有命令处理器响应 '" + bot.getName()
                    + "' 的 '/" + firstWord + "' - 请检查 config.yml 中的 auth.register-command/auth.login-command，并确认登录"
                    + "插件确实已安装并启用。");
            return;
        }
        armOutcomeDetection(bot);
    }

    /**
     * 监听登录插件自身的回复（见类文档），以便响应看起来明确成功时立即解除假人冻结；
     * 而无论结果如何，{@code auth.pending-timeout-ticks} 一到就必定解除冻结并
     * 停止监听，这样本插件读不懂的响应也绝不会让假人卡住。
     */
    private void armOutcomeDetection(FakePlayer bot) {
        UUID uuid = bot.getUuid();
        FakeServerGamePacketListenerImpl.listen(
                uuid, text -> Bukkit.getScheduler().runTask(plugin, () -> {
                    FppLogger.info("Auth: '" + bot.getName() + "' 收到：\"" + text + "\"");
                    String lower = text.toLowerCase(Locale.ROOT);
                    if (containsAny(lower, POSITIVE_HINTS)) {
                        FakeServerGamePacketListenerImpl.stopListening(uuid);
                        bot.setAuthPending(false);
                    } else if (containsAny(lower, NEGATIVE_HINTS)) {
                        FppLogger.warn("Auth: '" + bot.getName() + "' 似乎认证失败了（见上方"
                                + "消息）。如果存储的密码已失效，请执行 /fpp auth reset " + bot.getName()
                                + " 或 /fpp auth setpassword " + bot.getName() + " <password>。");
                    }
                }));

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            FakeServerGamePacketListenerImpl.stopListening(uuid);
                            bot.setAuthPending(false);
                        },
                        Config.authPendingTimeoutTicks());
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }

    // ── 管理操作（/fpp auth ...） ────────────────────────────────────────────────────────────────

    /** 通过 {@code callback} 在主线程交付结果 - 底层数据库读取是阻塞的，见 {@link DatabaseManager#getBotAuth}。 */
    public void lookup(String botName, Consumer<DatabaseManager.BotAuthRow> callback) {
        if (database == null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager.BotAuthRow row = database.getBotAuth(botName);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(row));
        });
    }

    /** 忘记本插件为 {@code botName} 记录的任何密码 - 不会在认证插件一侧注销它，见类文档。 */
    public void reset(String botName) {
        if (database != null) database.deleteBotAuth(botName);
    }

    /** 手动告诉本插件 {@code botName} 当前的真实密码（例如管理员直接在认证插件一侧重置之后），以便下次加入时用它登录而不是注册。 */
    public boolean setPassword(String botName, String rawPassword) {
        if (database == null) return false;
        try {
            database.upsertBotAuth(botName, cipher.encrypt(rawPassword), null);
            return true;
        } catch (GeneralSecurityException e) {
            FppLogger.warn("Auth: 无法加密为 '" + botName + "' 提供的密码：" + e.getMessage());
            return false;
        }
    }
}