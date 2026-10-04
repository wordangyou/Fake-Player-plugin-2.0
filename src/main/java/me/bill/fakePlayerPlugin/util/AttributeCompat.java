package me.bill.fakePlayerPlugin.util;

import org.bukkit.attribute.Attribute;

/**
 * 版本兼容的属性查找辅助类。
 *
 * <p>Paper 1.21.3+ 新增了不带 {@code GENERIC_} 前缀的简写枚举常量
 * （如 {@code MAX_HEALTH}）。旧版服务端（1.21.1 及以下）只有旧名称
 * （如 {@code GENERIC_MAX_HEALTH}）。若在字节码中直接引用新常量，
 * 在不具备该常量的服务端上会在运行时触发 {@link NoSuchFieldError}。
 *
 * <p>本类会在类加载阶段通过反射确定正确的常量，这样其余代码
 * 便可直接调用 {@link #maxHealth()}，无需进行任何版本判断。
 */
public final class AttributeCompat {

    /**
     * 在类加载时解析；除非服务端严重异常，否则不会为 null。
     */
    public static final Attribute MAX_HEALTH = resolve("MAX_HEALTH", "GENERIC_MAX_HEALTH");

    private AttributeCompat() {}

    /**
     * 返回当前服务端存在的最大生命值 {@link Attribute} 常量；
     * 若两个名称均无法找到则返回 {@code null}（在任何受支持的版本上都不应发生）。
     */
    public static Attribute maxHealth() {
        return MAX_HEALTH;
    }

    // ── 内部实现 ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static <T extends Attribute> T resolve(String... names) {
        for (String name : names) {
            try {
                return (T) Attribute.class.getField(name).get(null);
            } catch (NoSuchFieldException ignored) {
                // 尝试下一个候选名称
            } catch (Exception e) {
                FppLogger.warn("AttributeCompat：解析 '" + name + "' 时出现意外错误：" + e.getMessage());
            }
        }
        FppLogger.warn("AttributeCompat：无法解析以下任一名称：" + String.join(", ", names));
        return null;
    }
}
