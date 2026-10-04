package me.bill.fakePlayerPlugin.fakeplayer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;

/**
 * Single source of truth for the foods the auto-eat system understands. Powers both the eating math
 * (nutrition + saturation) and the per-bot food-selector GUI, so the two can never drift apart.
 *
 * <p>Only beneficial / neutral foods are listed - items with harmful side effects (rotten flesh,
 * spider eye, pufferfish, poisonous potato, chorus fruit, etc.) are intentionally excluded so a bot
 * left on the default "eat anything" filter never poisons or teleports itself.
 */
public final class BotFoods {

    /** A single edible item: hunger restored, saturation modifier and a small-caps display name. */
    public record FoodDef(Material material, int nutrition, float saturationModifier, String display) {

        /** Saturation granted on eating, using the vanilla formula (capped at the new food level). */
        public float saturationGain() {
            return nutrition * saturationModifier * 2f;
        }
    }

    private static final Map<Material, FoodDef> FOODS;

    static {
        Map<Material, FoodDef> m = new LinkedHashMap<>();
        add(m, Material.COOKED_BEEF, 8, 0.8f, "牛排");
        add(m, Material.COOKED_PORKCHOP, 8, 0.8f, "熟猪排");
        add(m, Material.GOLDEN_CARROT, 6, 1.2f, "金胡萝卜");
        add(m, Material.GOLDEN_APPLE, 4, 1.2f, "金苹果");
        add(m, Material.ENCHANTED_GOLDEN_APPLE, 4, 1.2f, "附魔金苹果");
        add(m, Material.COOKED_MUTTON, 6, 0.8f, "熟羊肉");
        add(m, Material.COOKED_SALMON, 6, 0.8f, "熟鲑鱼");
        add(m, Material.COOKED_CHICKEN, 6, 0.6f, "熟鸡肉");
        add(m, Material.BREAD, 5, 0.6f, "面包");
        add(m, Material.COOKED_COD, 5, 0.6f, "熟鳕鱼");
        add(m, Material.COOKED_RABBIT, 5, 0.6f, "熟兔肉");
        add(m, Material.BAKED_POTATO, 5, 0.6f, "烤马铃薯");
        add(m, Material.PUMPKIN_PIE, 8, 0.3f, "南瓜派");
        add(m, Material.MUSHROOM_STEW, 6, 0.6f, "蘑菇煲");
        add(m, Material.BEETROOT_SOUP, 6, 0.6f, "甜菜汤");
        add(m, Material.RABBIT_STEW, 10, 0.6f, "兔肉煲");
        add(m, Material.APPLE, 4, 0.3f, "苹果");
        add(m, Material.CARROT, 3, 0.6f, "胡萝卜");
        add(m, Material.BEEF, 3, 0.3f, "生牛肉");
        add(m, Material.PORKCHOP, 3, 0.3f, "生猪排");
        add(m, Material.MUTTON, 2, 0.3f, "生羊肉");
        add(m, Material.CHICKEN, 2, 0.3f, "生鸡肉");
        add(m, Material.RABBIT, 3, 0.3f, "生兔肉");
        add(m, Material.COD, 2, 0.1f, "生鳕鱼");
        add(m, Material.SALMON, 2, 0.1f, "生鲑鱼");
        add(m, Material.TROPICAL_FISH, 1, 0.1f, "热带鱼");
        add(m, Material.MELON_SLICE, 2, 0.3f, "西瓜片");
        add(m, Material.SWEET_BERRIES, 2, 0.1f, "甜浆果");
        add(m, Material.GLOW_BERRIES, 2, 0.1f, "发光浆果");
        add(m, Material.POTATO, 1, 0.3f, "马铃薯");
        add(m, Material.BEETROOT, 1, 0.6f, "甜菜根");
        add(m, Material.DRIED_KELP, 1, 0.3f, "干海带");
        add(m, Material.COOKIE, 2, 0.1f, "曲奇");
        add(m, Material.HONEY_BOTTLE, 6, 0.1f, "蜂蜜瓶");

        FOODS = Collections.unmodifiableMap(m);
    }

    private BotFoods() {}

    private static void add(Map<Material, FoodDef> m, Material mat, int nutrition, float sat, String display) {
        if (mat != null) m.put(mat, new FoodDef(mat, nutrition, sat, display));
    }

    /** All auto-eat-eligible foods, in a sensible high-to-low nutrition display order. */
    public static List<FoodDef> all() {
        return List.copyOf(FOODS.values());
    }

    public static boolean isFood(Material type) {
        return type != null && FOODS.containsKey(type);
    }

    public static FoodDef get(Material type) {
        return type == null ? null : FOODS.get(type);
    }

    /** Parses a persisted comma-separated material list into a validated allowed-food set. */
    public static Set<Material> parse(String csv) {
        if (csv == null || csv.isBlank()) return new java.util.LinkedHashSet<>();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try {
                        return Material.valueOf(s.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(BotFoods::isFood)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    /** Serialises an allowed-food set back to a comma-separated material list for persistence. */
    public static String serialize(Set<Material> foods) {
        if (foods == null || foods.isEmpty()) return "";
        return foods.stream().map(Material::name).collect(Collectors.joining(","));
    }
}
