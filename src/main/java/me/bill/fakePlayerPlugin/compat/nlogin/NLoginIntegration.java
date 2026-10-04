package me.bill.fakePlayerPlugin.compat.nlogin;

import java.lang.reflect.Method;

import me.bill.fakePlayerPlugin.util.FppLogger;

/**
 * 通过 nLogin（nickuc.com 的闭源认证插件）真正的公共 API
 * （{@code com.nickuc.login.api.nLoginAPI}）把假人接入其中（若已安装）- 完全可选的软依赖，
 * nLogin 不存在时完全惰性。全部通过反射解析（见 {@link #resolveMethods()}），而不是编译期
 * 依赖：nLogin 是付费插件、没有公开的 Maven 构件，因此它的 API 包从不出现在本项目的构建
 * classpath 上 - 硬性 {@code import} 会让任何没有私有 {@code nLogin.jar} 副本的人无法编译
 * 整个插件。
 *
 * <p><b>既然其他所有测试过的登录插件（OpeNLogin、AuthMe 系的）都能通过
 * {@code BotAuthManager} 的常规命令模拟路径工作，这里为什么还需要它：</b> nLogin 自身的
 * 实时“此连接是否已认证”状态保存在一个按连接创建的会话对象上，而该对象只有在其捆绑的
 * PacketEvents 处理客户端的<i>真实</i>登录/配置数据包时才会创建。假人从不经过那里 -
 * {@code NmsPlayerSpawner} 把一个已经完整构建的实体直接放进世界，而不是模拟的网络
 * 握手 - 所以模拟聊天命令（对其他每个测试过的插件都与真实玩家无异）每次都会命中
 * {@code IllegalStateException: Player session not set}，已对真实的 nLogin 安装实机确认。
 * 连 nLogin 自己的管理命令（{@code /nlogin register}、{@code /nlogin forcelogin}）也撞上
 * 同样的墙 - {@code register} 单独可以在没有会话的情况下创建数据库行，但不会把实时连接
 * 切换为已认证，而 {@code forcelogin} 抛出完全相同的异常。
 *
 * <p>{@code nLoginAPI#forceLogin} 则不同：它是 nLogin 官方认可并有文档记载的、
 * 以编程方式将玩家标记为已认证的方式 - 正是为这种集成形态而构建（代理会话同步、
 * 正版自动登录等），而不是叠加在这里其他一切都会遇到的同一会话要求之上。
 * 实机确认：先通过 {@code nLoginAPI#performRegister} 注册再
 * {@code nLoginAPI#forceLogin}，确实能解除假人的伤害取消，这一点与那些管理命令不同。
 */
public final class NLoginIntegration {

    private static final String API_CLASS = "com.nickuc.login.api.nLoginAPI";

    /** API 类/方法成功解析后非空；反射查找缓存起来永久使用代价很低。 */
    private static volatile Handles handles;

    private final Object api;
    private final Handles handlesForApi;

    private NLoginIntegration(Object api, Handles handlesForApi) {
        this.api = api;
        this.handlesForApi = handlesForApi;
    }

    private record Handles(
            Method getApi,
            Method isAvailable,
            Method isRegistered,
            Method isAuthenticated,
            Method performRegister,
            Method forceLogin) {}

    /**
     * 在 nLogin 的类首次真正可加载时通过反射解析 API 的方法句柄，然后永久缓存。
     * 在首次成功之前，每次调用都会刻意重试 - 不会把失败记为永久失败 - 这样在本插件之后
     * 才启用（加载顺序）或在 {@code /reload} 期间安装的 nLogin 也能被识别，无需重启。
     */
    private static Handles resolveMethods() {
        Handles cached = handles;
        if (cached != null) return cached;
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Handles resolved = new Handles(
                    apiClass.getMethod("getApi"),
                    apiClass.getMethod("isAvailable"),
                    apiClass.getMethod("isRegistered", String.class),
                    apiClass.getMethod("isAuthenticated", String.class),
                    apiClass.getMethod("performRegister", String.class, String.class),
                    apiClass.getMethod("forceLogin", String.class));
            handles = resolved;
            return resolved;
        } catch (Throwable t) {
            // nLogin 未安装，或其 API 形态与我们预期不符 - 保持完全惰性。
            return null;
        }
    }

    /**
     * 通过 nLogin 自己的静态持有者查找其 API。若 nLogin 未安装或其 API 尚未就绪，
     * 返回 {@code null}（不做其他事）- 可无条件调用，与本代码库中其他软依赖集成一致。
     * 可用性检查本身从不缓存 - 只缓存反射方法句柄 - 所以每次重新调用可以完全绕开
     * 插件加载顺序的时机问题（nLogin 在本插件之后启用、{@code /reload} 等），
     * 而无需延迟检查之类的把戏。
     *
     * <p>用 {@code catch (Throwable} 而不是 {@code catch (Exception} 包裹：反射调用会把
     * 目标侧的 throwable 包装进 {@link java.lang.reflect.InvocationTargetException}，
     * 而缺失/不兼容的 nLogin 构建仍可能在其下抛出未检查异常。
     */
    public static NLoginIntegration tryInstall() {
        Handles h = resolveMethods();
        if (h == null) return null;
        try {
            Object api = h.getApi().invoke(null);
            if (api == null || !(Boolean) h.isAvailable().invoke(api)) return null;
            return new NLoginIntegration(api, h);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 从不抛出异常。 */
    public boolean isRegistered(String name) {
        try {
            return (Boolean) handlesForApi.isRegistered().invoke(api, name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 从不抛出异常。 */
    public boolean isAuthenticated(String name) {
        try {
            return (Boolean) handlesForApi.isAuthenticated().invoke(api, name);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 通过 nLogin 自己的 API 创建账号 - 本身不会把实时会话切换为已认证，见类文档；务必接着调用 {@link #forceLogin}。从不抛出异常。 */
    public boolean performRegister(String name, String password) {
        try {
            return (Boolean) handlesForApi.performRegister().invoke(api, name, password);
        } catch (Throwable t) {
            FppLogger.warn("nLogin API: performRegister 对 '" + name + "' 抛出异常：" + t.getMessage());
            return false;
        }
    }

    /** nLogin 官方认可的、无需真实登录握手即可将连接标记为已认证的方式 - 见类文档。从不抛出异常。 */
    public boolean forceLogin(String name) {
        try {
            return (Boolean) handlesForApi.forceLogin().invoke(api, name);
        } catch (Throwable t) {
            FppLogger.warn("nLogin API: forceLogin 对 '" + name + "' 抛出异常：" + t.getMessage());
            return false;
        }
    }
}