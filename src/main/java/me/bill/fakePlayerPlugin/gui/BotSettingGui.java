package me.bill.fakePlayerPlugin.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.api.event.FppBotDespawnEvent;
import me.bill.fakePlayerPlugin.api.event.FppBotSettingChangeEvent;
import me.bill.fakePlayerPlugin.api.impl.FppBotImpl;
import me.bill.fakePlayerPlugin.command.LeftClickCommand;
import me.bill.fakePlayerPlugin.command.RightClickCommand;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.economy.RentalPurchases;
import me.bill.fakePlayerPlugin.fakeplayer.BotFoods;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.SkinManager;
import me.bill.fakePlayerPlugin.fakeplayer.SkinModelDetector;
import me.bill.fakePlayerPlugin.fakeplayer.SkinProfile;
import me.bill.fakePlayerPlugin.fakeplayer.pathfinding.PathfindingDebugManager;
import me.bill.fakePlayerPlugin.lang.Lang;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.BotAccess;
import me.bill.fakePlayerPlugin.util.FppScheduler;
import me.bill.fakePlayerPlugin.util.TextUtil;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class BotSettingGui implements Listener {

    private static final TextColor ACCENT = GuiKit.ACCENT;
    private static final TextColor ON_GREEN = GuiKit.ON_GREEN;
    private static final TextColor OFF_RED = GuiKit.OFF_RED;
    private static final TextColor VALUE_YELLOW = GuiKit.VALUE_YELLOW;
    private static final TextColor YELLOW = GuiKit.YELLOW;
    private static final TextColor GRAY = GuiKit.GRAY;
    private static final TextColor DARK_GRAY = GuiKit.DARK_GRAY;
    private static final TextColor WHITE = GuiKit.WHITE;
    private static final TextColor DANGER_RED = GuiKit.DANGER_RED;
    private static final TextColor COMING_SOON_COLOR = GuiKit.COMING_SOON_COLOR;
    private static final TextColor SELECTED_GREEN = GuiKit.SELECTED_GREEN;

    private static final int SIZE = 54;
    private static final int SETTINGS_PER_PAGE = 45;
    /** Longest allowed bot display name (visible characters) - keeps the floating name-tag readable. */
    private static final int RENAME_MAX_LENGTH = 32;

    private static final int SLOT_RESET = 45;
    private static final int SLOT_CAT_PREV = 46;
    private static final int SLOT_CAT_NEXT = 52;
    private static final int SLOT_CLOSE = 53;
    private static final int CAT_WINDOW = 5;
    private static final int CAT_WINDOW_START = 47;

    private static final int MOB_GUI_SIZE = 54;
    private static final int MOB_SLOTS = 45;
    private static final int MOB_SLOT_BACK = 45;
    private static final int MOB_SLOT_PREV_PAGE = 46;
    private static final int MOB_SLOT_CLEAR = 49;
    private static final int MOB_SLOT_NEXT_PAGE = 52;
    private static final int MOB_SLOT_CLOSE = 53;

    private static final List<MobDisplay> MOB_LIST;

    static {
        List<MobDisplay> list = new ArrayList<>();

        list.add(new MobDisplay(EntityType.ZOMBIE, Material.ZOMBIE_HEAD, "僵尸", "敌对"));
        list.add(new MobDisplay(EntityType.SKELETON, Material.SKELETON_SKULL, "骷髅", "敌对"));
        list.add(new MobDisplay(EntityType.CREEPER, Material.CREEPER_HEAD, "苦力怕", "敌对"));
        list.add(new MobDisplay(EntityType.SPIDER, Material.SPIDER_EYE, "蜘蛛", "敌对"));
        list.add(new MobDisplay(EntityType.CAVE_SPIDER, Material.FERMENTED_SPIDER_EYE, "洞穴蜘蛛", "敌对"));
        list.add(new MobDisplay(EntityType.ENDERMAN, Material.ENDER_PEARL, "末影人", "中立"));
        list.add(new MobDisplay(EntityType.WITCH, Material.SPLASH_POTION, "女巫", "敌对"));
        list.add(new MobDisplay(EntityType.PILLAGER, Material.CROSSBOW, "掠夺者", "敌对"));
        list.add(new MobDisplay(EntityType.VINDICATOR, Material.IRON_AXE, "卫道士", "敌对"));
        list.add(new MobDisplay(EntityType.EVOKER, Material.TOTEM_OF_UNDYING, "唤魔者", "敌对"));
        list.add(new MobDisplay(EntityType.RAVAGER, Material.SADDLE, "劫掠兽", "敌对"));
        list.add(new MobDisplay(EntityType.VEX, Material.IRON_SWORD, "恼鬼", "敌对"));
        list.add(new MobDisplay(EntityType.PHANTOM, Material.PHANTOM_MEMBRANE, "幻翼", "敌对"));
        list.add(new MobDisplay(EntityType.DROWNED, Material.TRIDENT, "溺尸", "敌对"));
        list.add(new MobDisplay(EntityType.HUSK, Material.SAND, "尸壳", "敌对"));
        list.add(new MobDisplay(EntityType.STRAY, Material.ARROW, "流浪者", "敌对"));
        list.add(new MobDisplay(EntityType.BLAZE, Material.BLAZE_ROD, "烈焰人", "敌对"));
        list.add(new MobDisplay(EntityType.GHAST, Material.GHAST_TEAR, "恶魂", "敌对"));
        list.add(new MobDisplay(EntityType.MAGMA_CUBE, Material.MAGMA_CREAM, "岩浆怪", "敌对"));
        list.add(new MobDisplay(EntityType.SLIME, Material.SLIME_BALL, "史莱姆", "敌对"));
        list.add(new MobDisplay(EntityType.HOGLIN, Material.COOKED_PORKCHOP, "疣猪兽", "敌对"));
        list.add(new MobDisplay(EntityType.PIGLIN_BRUTE, Material.GOLDEN_AXE, "猪灵蛮兵", "敌对"));
        list.add(new MobDisplay(EntityType.WARDEN, Material.SCULK_SHRIEKER, "监守者", "敌对"));

        list.add(new MobDisplay(EntityType.ENDER_DRAGON, Material.DRAGON_HEAD, "末影龙", "BOSS"));
        list.add(new MobDisplay(EntityType.WITHER, Material.NETHER_STAR, "凋灵", "BOSS"));

        list.add(new MobDisplay(EntityType.COW, Material.BEEF, "牛", "被动"));
        list.add(new MobDisplay(EntityType.PIG, Material.PORKCHOP, "猪", "被动"));
        list.add(new MobDisplay(EntityType.SHEEP, Material.WHITE_WOOL, "绵羊", "被动"));
        list.add(new MobDisplay(EntityType.CHICKEN, Material.FEATHER, "鸡", "被动"));
        list.add(new MobDisplay(EntityType.RABBIT, Material.RABBIT_FOOT, "兔子", "被动"));
        list.add(new MobDisplay(EntityType.SQUID, Material.INK_SAC, "鱿鱼", "被动"));
        list.add(new MobDisplay(EntityType.GLOW_SQUID, Material.GLOW_INK_SAC, "发光鱿鱼", "被动"));
        list.add(new MobDisplay(EntityType.TURTLE, Material.TURTLE_EGG, "海龟", "被动"));
        list.add(new MobDisplay(EntityType.COD, Material.COD, "鳕鱼", "被动"));
        list.add(new MobDisplay(EntityType.SALMON, Material.SALMON, "鲑鱼", "被动"));
        list.add(new MobDisplay(EntityType.TROPICAL_FISH, Material.TROPICAL_FISH, "热带鱼", "被动"));
        list.add(new MobDisplay(EntityType.PUFFERFISH, Material.PUFFERFISH, "河豚", "被动"));
        list.add(new MobDisplay(EntityType.VILLAGER, Material.EMERALD, "村民", "被动"));
        list.add(new MobDisplay(EntityType.WANDERING_TRADER, Material.EMERALD_BLOCK, "流浪商人", "被动"));
        list.add(new MobDisplay(EntityType.HORSE, Material.GOLDEN_APPLE, "马", "被动"));
        list.add(new MobDisplay(EntityType.DONKEY, Material.CHEST, "驴", "被动"));
        list.add(new MobDisplay(EntityType.MULE, Material.CHEST, "骡", "被动"));
        list.add(new MobDisplay(EntityType.CAT, Material.STRING, "猫", "被动"));
        list.add(new MobDisplay(EntityType.PARROT, Material.COOKIE, "鹦鹉", "被动"));
        list.add(new MobDisplay(EntityType.FOX, Material.SWEET_BERRIES, "狐狸", "被动"));
        list.add(new MobDisplay(EntityType.OCELOT, Material.COD, "豹猫", "被动"));
        list.add(new MobDisplay(EntityType.AXOLOTL, Material.AXOLOTL_BUCKET, "美西螈", "被动"));
        list.add(new MobDisplay(EntityType.FROG, Material.SLIME_BALL, "青蛙", "被动"));
        list.add(new MobDisplay(EntityType.TADPOLE, Material.TADPOLE_BUCKET, "蝌蚪", "被动"));
        list.add(new MobDisplay(EntityType.ALLAY, Material.AMETHYST_SHARD, "悦灵", "被动"));
        list.add(new MobDisplay(EntityType.SNIFFER, Material.TORCHFLOWER_SEEDS, "嗅探兽", "被动"));
        list.add(new MobDisplay(EntityType.CAMEL, Material.CACTUS, "骆驼", "被动"));
        list.add(new MobDisplay(EntityType.ARMADILLO, Material.BRUSH, "犰狳", "被动"));
        list.add(new MobDisplay(EntityType.SNOW_GOLEM, Material.SNOW_BLOCK, "雪傀儡", "被动"));
        list.add(new MobDisplay(EntityType.STRIDER, Material.WARPED_FUNGUS, "炽足兽", "被动"));
        list.add(new MobDisplay(EntityType.BAT, Material.BLACK_DYE, "蝙蝠", "被动"));
        list.add(new MobDisplay(EntityType.MOOSHROOM, Material.RED_MUSHROOM, "哞菇", "被动"));
        list.add(new MobDisplay(EntityType.SKELETON_HORSE, Material.BONE_BLOCK, "骷髅马", "亡灵"));
        list.add(new MobDisplay(EntityType.ZOMBIE_HORSE, Material.ROTTEN_FLESH, "僵尸马", "亡灵"));
        list.add(new MobDisplay(EntityType.ZOMBIE_VILLAGER, Material.GOLDEN_APPLE, "僵尸村民", "敌对"));
        list.add(new MobDisplay(EntityType.ZOGLIN, Material.ROTTEN_FLESH, "僵尸疣猪兽", "敌对"));

        MOB_LIST = Collections.unmodifiableList(list);
    }

    private final FakePlayerPlugin plugin;
    private final FakePlayerManager manager;

    private final Map<UUID, int[]> sessions = new HashMap<>();

    private final Map<UUID, UUID> botSessions = new HashMap<>();

    private final Map<UUID, UUID> botLocks = new HashMap<>();

    private final Map<UUID, ChatInputSes> chatSessions = new HashMap<>();
    private final Set<UUID> pendingChatInput = new HashSet<>();
    private final Set<UUID> pendingRebuild = new HashSet<>();

    private final Set<UUID> pendingDelete = new HashSet<>();

    private final Map<UUID, Long> pendingResetConfirm = new HashMap<>();
    private final Map<UUID, Integer> confirmTickTaskIds = new HashMap<>();
    private static final long RESET_CONFIRM_WINDOW_MS = 5000L;

    private final Map<UUID, Integer> mobSelectorPage = new HashMap<>();

    private final Set<UUID> inMobSelector = new HashSet<>();

    private final Map<UUID, Integer> foodSelectorPage = new HashMap<>();

    private final Set<UUID> inFoodSelector = new HashSet<>();

    private final Map<UUID, Integer> editPauseCounts = new HashMap<>();

    private final List<BotCategory> categories;

    public BotSettingGui(FakePlayerPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.categories = List.of(general(), pve(), pathfinding(), skin(), autoEat(), danger());
    }

    private List<BotCategory> allCategories(Player viewer) {
        return categories;
    }

    public void open(Player player, FakePlayer bot) {
        if (!BotAccess.canAdminister(player, bot)) {
            player.sendMessage(Lang.get("no-permission"));
            return;
        }
        UUID botUuid = bot.getUuid();
        UUID uuid = player.getUniqueId();
        if (!acquireBotLock(botUuid, uuid)) {
            player.sendMessage(Lang.get("inv-busy", "name", bot.getDisplayName()));
            return;
        }
        if (botUuid.equals(botSessions.get(uuid))) {
            build(player);
            return;
        }
        pauseBotForEditing(bot);
        sessions.put(uuid, new int[] {0, 0, 0});
        botSessions.put(uuid, botUuid);
        build(player);
    }

    public @NotNull List<String> getCategoryNames() {
        List<String> names = new ArrayList<>(categories.size());
        for (BotCategory category : categories) names.add(category.label());
        return Collections.unmodifiableList(names);
    }

    public void shutdown() {
        for (UUID botUuid : new ArrayList<>(editPauseCounts.keySet())) resumeBotAfterEditing(botUuid);
        sessions.clear();
        botSessions.clear();
        botLocks.clear();
        chatSessions.forEach((uuid, ses) -> FppScheduler.cancelTask(ses.cleanupTaskId));
        chatSessions.clear();
        pendingChatInput.clear();
        pendingRebuild.clear();
        pendingDelete.clear();
        pendingResetConfirm.clear();
        confirmTickTaskIds.forEach((uuid, taskId) -> FppScheduler.cancelTask(taskId));
        confirmTickTaskIds.clear();
        mobSelectorPage.clear();
        inMobSelector.clear();
        inFoodSelector.clear();
        editPauseCounts.clear();
    }

    private void build(Player player) {
        UUID uuid = player.getUniqueId();
        int[] state = sessions.get(uuid);
        UUID botUuid = botSessions.get(uuid);
        if (state == null || botUuid == null) return;

        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) {
            cleanup(uuid);
            player.sendMessage(Lang.get("delete-not-found", "name", "?"));
            return;
        }
        if (!BotAccess.canAdminister(player, bot)) {
            cleanup(uuid);
            player.closeInventory();
            player.sendMessage(Lang.get("no-permission"));
            return;
        }

        int catIdx = state[0];
        int pageIdx = state[1];
        int catOffset = state[2];
        List<BotCategory> all = allCategories(player);
        if (catIdx >= all.size()) catIdx = all.size() - 1;
        state[0] = catIdx;
        BotCategory cat = all.get(catIdx);
        boolean isOp = isOp(player);

        GuiHolder holder = new GuiHolder(uuid);
        Component title = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("] ").color(DARK_GRAY))
                .append(Component.text(bot.getName()).color(ACCENT))
                .append(Component.text("  ·  ").color(DARK_GRAY))
                .append(Component.text(cat.label()).color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        List<BotEntry> entries = visibleEntries(cat, isOp);
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) SETTINGS_PER_PAGE));
        pageIdx = Math.min(pageIdx, Math.max(0, totalPages - 1));
        state[1] = pageIdx;

        int startIdx = pageIdx * SETTINGS_PER_PAGE;
        int endIdx = Math.min(startIdx + SETTINGS_PER_PAGE, entries.size());
        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(i - startIdx, buildEntryItem(entries.get(i), bot, player));
        }

        inv.setItem(SLOT_RESET, buildResetButton());
        inv.setItem(
                SLOT_CAT_PREV, catOffset > 0 ? buildCatArrow(false) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i < CAT_WINDOW; i++) {
            int ci = catOffset + i;
            inv.setItem(
                    CAT_WINDOW_START + i,
                    ci < all.size()
                            ? buildCategoryTab(all.get(ci), ci == catIdx)
                            : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        }
        inv.setItem(
                SLOT_CAT_NEXT,
                catOffset + CAT_WINDOW < all.size()
                        ? buildCatArrow(true)
                        : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(SLOT_CLOSE, buildCloseButton());

        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
        sessions.put(uuid, state);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {

        if (event.getInventory().getHolder() instanceof MobSelectorHolder msh) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null) return;
            if (!event.getClickedInventory().equals(event.getInventory())) return;
            handleMobSelectorClick(player, msh, event.getSlot());
            return;
        }

        if (event.getInventory().getHolder() instanceof FoodSelectorHolder fsh) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null) return;
            if (!event.getClickedInventory().equals(event.getInventory())) return;
            handleFoodSelectorClick(player, fsh, event.getSlot());
            return;
        }

        if (event.getInventory().getHolder() instanceof ShareSelectorHolder ssh) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() == null) return;
            if (!event.getClickedInventory().equals(event.getInventory())) return;
            handleShareSelectorClick(player, ssh, event.getSlot());
            return;
        }

        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;

        UUID uuid = player.getUniqueId();
        int[] state = sessions.get(holder.uuid);
        UUID botUuid = botSessions.get(uuid);
        if (state == null || botUuid == null) return;

        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) {
            player.closeInventory();
            return;
        }
        if (!BotAccess.canAdminister(player, bot)) {
            player.closeInventory();
            player.sendMessage(Lang.get("no-permission"));
            return;
        }

        boolean isOp = isOp(player);
        int slot = event.getSlot();
        int catIdx = state[0];
        int catOffset = state[2];

        if (slot == SLOT_RESET) {
            playUiClick(player, 0.6f);
            resetBot(player, bot, isOp);
            return;
        }
        if (slot == SLOT_CAT_PREV) {
            if (catOffset > 0) {
                playUiClick(player, 1.0f);
                state[2]--;
            }
            build(player);
            return;
        }
        if (slot == SLOT_CAT_NEXT) {
            if (catOffset + CAT_WINDOW < allCategories(player).size()) {
                playUiClick(player, 1.0f);
                state[2]++;
            }
            build(player);
            return;
        }
        if (slot == SLOT_CLOSE) {
            playUiClick(player, 0.8f);
            if (event.isShiftClick() && Perm.has(player, Perm.LIST)) {
                // Back to the bot list instead of closing outright.
                player.performCommand("fpp list");
                return;
            }
            player.closeInventory();
            return;
        }
        if (slot >= CAT_WINDOW_START && slot < CAT_WINDOW_START + CAT_WINDOW) {
            int ci = catOffset + (slot - CAT_WINDOW_START);
            if (ci < allCategories(player).size()) {
                if (ci != catIdx) playUiClick(player, 1.3f);
                state[0] = ci;
                state[1] = 0;
                build(player);
            }
            return;
        }
        if (slot < 45) {
            List<BotCategory> allCats = allCategories(player);
            if (catIdx >= allCats.size()) return;
            List<BotEntry> entries = visibleEntries(allCats.get(catIdx), isOp);
            int entryIdx = state[1] * SETTINGS_PER_PAGE + slot;
            if (entryIdx >= entries.size()) return;
            handleEntryClick(player, bot, entries.get(entryIdx), isOp);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (event.getInventory().getHolder() instanceof MobSelectorHolder) {

            if (pendingRebuild.contains(uuid)) return;
            inMobSelector.remove(uuid);
            mobSelectorPage.remove(uuid);

            if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT && sessions.containsKey(uuid)) {
                FppScheduler.runSync(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && sessions.containsKey(uuid)) build(p);
                });
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof FoodSelectorHolder) {
            if (pendingRebuild.contains(uuid)) return;
            inFoodSelector.remove(uuid);
            foodSelectorPage.remove(uuid);
            if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT && sessions.containsKey(uuid)) {
                FppScheduler.runSync(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && sessions.containsKey(uuid)) build(p);
                });
            }
            return;
        }

        if (event.getInventory().getHolder() instanceof ShareSelectorHolder) {
            if (pendingRebuild.contains(uuid)) return;
            if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT && sessions.containsKey(uuid)) {
                FppScheduler.runSync(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && sessions.containsKey(uuid)) build(p);
                });
            }
            return;
        }

        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
        if (pendingChatInput.contains(uuid)) return;
        if (pendingRebuild.contains(uuid)) return;
        if (pendingDelete.contains(uuid)) return;
        if (inMobSelector.contains(uuid)) return;
        if (inFoodSelector.contains(uuid)) return;
        cleanup(uuid);
        if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT && event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✔ ").color(ON_GREEN))
                    .append(Component.text("假人设置已保存 • 设置已生效")
                            .color(WHITE)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSes ses = chatSessions.remove(uuid);
        if (ses == null) return;

        event.setCancelled(true);
        FppScheduler.cancelTask(ses.cleanupTaskId);

        String raw = PlainTextComponentSerializer.plainText()
                .serialize(event.message())
                .trim();

        handleChatInput(uuid, ses, raw);
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSes ses = chatSessions.remove(uuid);
        if (ses == null) return;

        event.setCancelled(true);
        FppScheduler.cancelTask(ses.cleanupTaskId);
        handleChatInput(uuid, ses, event.getMessage().trim());
    }

    private void handleChatInput(UUID uuid, ChatInputSes ses, String raw) {
        sessions.put(uuid, ses.guiState);
        FppScheduler.runSync(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;

            if (raw.equalsIgnoreCase("cancel")) {
                p.sendActionBar(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("✦ ").color(ACCENT))
                       .append(Component.text("已取消 - 返回设置。")
                                .color(GRAY)));
                build(p);
                return;
            }

            FakePlayer bot = manager.getByUuid(ses.botUuid);
            if (bot == null) {
                p.sendActionBar(Lang.get("delete-not-found", "name", "?"));
                cleanup(uuid);
                return;
            }

            applyInput(p, bot, ses.inputType, raw);
            build(p);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSes ses = chatSessions.remove(uuid);
        if (ses != null) FppScheduler.cancelTask(ses.cleanupTaskId);
        inMobSelector.remove(uuid);
        mobSelectorPage.remove(uuid);
        inFoodSelector.remove(uuid);
        foodSelectorPage.remove(uuid);
        cleanup(uuid);
        PathfindingDebugManager.clearViewer(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotDespawn(FppBotDespawnEvent event) {
        releaseAllEditors(event.getBot().getUuid());
        PathfindingDebugManager.clearBot(event.getBot().getUuid());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBotDeath(PlayerDeathEvent event) {
        FakePlayer bot = manager.getByEntity(event.getEntity());
        if (bot != null) releaseAllEditors(bot.getUuid());
    }

    private void handleEntryClick(Player player, FakePlayer bot, BotEntry entry, boolean isOp) {
        switch (entry.type()) {
            case COMING_SOON -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.8f, 1.0f);
                player.sendActionBar(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                        .append(Component.text(entry.label() + "  ")
                                .color(WHITE)
                                .decoration(TextDecoration.BOLD, false))
                        .append(Component.text("- 即将推出")
                                .color(COMING_SOON_COLOR)
                                .decoration(TextDecoration.BOLD, true)));
            }
            case TOGGLE -> {
                boolean newVal;
                if ("show_path".equals(entry.id())) {
                    newVal = PathfindingDebugManager.toggle(player.getUniqueId(), bot.getUuid());
                } else {
                    newVal = applyToggle(bot, entry.id());

                    if (!newVal) {
                        if ("pickup_items".equals(entry.id())) {
                            dropBotInventory(bot);
                        } else if ("pickup_xp".equals(entry.id())) {
                            dropBotXp(bot);
                        }
                    }

                    manager.persistBotSettings(bot);
                }
                playUiClick(player, newVal ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label(), newVal ? "✔ 已开启" : "✘ 已关闭");
                build(player);
            }
            case CYCLE_PRIORITY -> {
                cyclePriority(bot);
                manager.persistBotSettings(bot);
                restartPveIfActive(bot);
                playUiClick(player, 1.0f);
                sendActionBarConfirm(player, entry.label(), "lowest-health".equals(bot.getPvePriority()) ? "最低血量" : "最近");
                build(player);
            }
            case CYCLE_PVE_MODE -> {
                cyclePveMode(bot);
                manager.persistBotSettings(bot);
                restartPveIfActive(bot);
                playUiClick(player, 1.0f);
                sendActionBarConfirm(player, entry.label(), pveModeLabel(bot));
                build(player);
            }
            case ACTION -> {
                playUiClick(player, 1.0f);
                openChatInput(player, bot, entry);
            }
            case MOB_SELECTOR -> {
                playUiClick(player, 1.0f);
                openMobSelector(player, bot);
            }
            case FOOD_SELECTOR -> {
                playUiClick(player, 1.0f);
                openFoodSelector(player, bot);
            }
            case IMMEDIATE -> {
                if ("share_control".equals(entry.id())) {
                    if (!BotAccess.canShare(player, bot)) {
                        player.sendMessage(Lang.get("no-permission"));
                        return;
                    }
                    openShareSelector(player, bot);
                    return;
                }
                applyImmediate(player, bot, entry.id());
                playUiClick(player, 0.85f);
                build(player);
            }
            case DANGER -> {
                if (!isOp) return;
                playUiClick(player, 0.6f);
                applyDanger(player, bot, entry.id());
            }
        }
    }

    private void fireSettingChange(FakePlayer bot, String key, Object oldValue, Object newValue) {
        Bukkit.getPluginManager().callEvent(new FppBotSettingChangeEvent(new FppBotImpl(bot), key, oldValue, newValue));
    }

    private boolean applyToggle(FakePlayer bot, String id) {
        return switch (id) {
            case "frozen" -> {
                boolean old = bot.isFrozen();
                bot.setFrozen(!old);
                fireSettingChange(bot, "frozen", old, bot.isFrozen());
                yield bot.isFrozen();
            }
            case "respawn_on_death" -> {
                boolean old = bot.isRespawnOnDeath();
                bot.setRespawnOnDeath(!old);
                fireSettingChange(bot, "respawn_on_death", old, bot.isRespawnOnDeath());
                yield bot.isRespawnOnDeath();
            }
            case "head_ai_enabled" -> {
                boolean old = bot.isHeadAiEnabled();
                bot.setHeadAiEnabled(!old);
                fireSettingChange(bot, "head_ai_enabled", old, bot.isHeadAiEnabled());
                yield bot.isHeadAiEnabled();
            }
            case "swim_ai_enabled" -> {
                boolean old = bot.isSwimAiEnabled();
                bot.setSwimAiEnabled(!old);
                fireSettingChange(bot, "swim_ai_enabled", old, bot.isSwimAiEnabled());
                yield bot.isSwimAiEnabled();
            }
            case "pickup_items" -> {
                boolean old = bot.isPickUpItemsEnabled();
                boolean v = !old;
                bot.setPickUpItemsEnabled(v);
                fireSettingChange(bot, "pickup_items", old, bot.isPickUpItemsEnabled());

                Player body = bot.getPlayer();
                if (body != null) body.setCanPickupItems(v);
                yield v;
            }
            case "pickup_xp" -> {
                boolean old = bot.isPickUpXpEnabled();
                bot.setPickUpXpEnabled(!old);
                fireSettingChange(bot, "pickup_xp", old, bot.isPickUpXpEnabled());
                yield bot.isPickUpXpEnabled();
            }
            case "auto_milk" -> {
                boolean old = bot.isAutoMilkEnabled();
                bot.setAutoMilkEnabled(!old);
                fireSettingChange(bot, "auto_milk", old, bot.isAutoMilkEnabled());
                yield bot.isAutoMilkEnabled();
            }
            case "auto_eat" -> {
                boolean old = bot.isAutoEatEnabled();
                bot.setAutoEatEnabled(!old);
                fireSettingChange(bot, "auto_eat", old, bot.isAutoEatEnabled());
                yield bot.isAutoEatEnabled();
            }
            case "prevent_bad_omen" -> {
                boolean old = bot.isPreventBadOmen();
                bot.setPreventBadOmen(!old);
                fireSettingChange(bot, "prevent_bad_omen", old, bot.isPreventBadOmen());
                yield bot.isPreventBadOmen();
            }
            case "nav_parkour" -> {
                boolean old = bot.isNavParkour();
                bot.setNavParkour(!old);
                fireSettingChange(bot, "nav_parkour", old, bot.isNavParkour());
                yield bot.isNavParkour();
            }
            case "nav_break_blocks" -> {
                boolean old = bot.isNavBreakBlocks();
                bot.setNavBreakBlocks(!old);
                fireSettingChange(bot, "nav_break_blocks", old, bot.isNavBreakBlocks());
                yield bot.isNavBreakBlocks();
            }
            case "nav_place_blocks" -> {
                boolean old = bot.isNavPlaceBlocks();
                bot.setNavPlaceBlocks(!old);
                fireSettingChange(bot, "nav_place_blocks", old, bot.isNavPlaceBlocks());
                yield bot.isNavPlaceBlocks();
            }
            case "pve_enabled" -> bot.isPveEnabled();
            case "pve_move" -> bot.isPveMoveToTarget();
            default -> false;
        };
    }

    private void restartPveIfActive(FakePlayer bot) {
        var pve = plugin.getPveController();
        if (pve != null) pve.refresh(bot);
        if (bot.isPveEnabled())
            fireSettingChange(
                    bot, "pve_restart", null, bot.getPveSmartAttackMode().name());
    }

    private void cyclePriority(FakePlayer bot) {
        String old = bot.getPvePriority();
        String current = bot.getPvePriority();
        bot.setPvePriority("nearest".equals(current) ? "lowest-health" : "nearest");
        fireSettingChange(bot, "pve_priority", old, bot.getPvePriority());
    }

    private void cyclePveMode(FakePlayer bot) {
        var oldMode = bot.getPveSmartAttackMode();
        boolean oldEnabled = bot.isPveEnabled();
        boolean oldMove = bot.isPveMoveToTarget();

        bot.setPveSmartAttackMode(oldMode.next());
        fireSettingChange(
                bot,
                "pve_smart_attack_mode",
                oldMode.name(),
                bot.getPveSmartAttackMode().name());
        if (oldEnabled != bot.isPveEnabled()) {
            fireSettingChange(bot, "pve_enabled", oldEnabled, bot.isPveEnabled());
        }
        if (oldMove != bot.isPveMoveToTarget()) {
            fireSettingChange(bot, "pve_move", oldMove, bot.isPveMoveToTarget());
        }

        var attackCmd = plugin.getAttackCommand();
        if (attackCmd != null && !bot.isPveEnabled()) attackCmd.stopAttacking(bot.getUuid());
    }

    private String pveModeLabel(FakePlayer bot) {
        return switch (bot.getPveSmartAttackMode()) {
            case OFF -> "✘ 关闭";
            case ON_NO_MOVE -> "✔ 开启 · 静止";
            case ON_MOVE -> "✔ 开启 · 移动";
        };
    }

    private void applyImmediate(Player player, FakePlayer bot, String id) {
        switch (id) {
            case "skin_info" -> sendActionBarConfirm(player, "当前皮肤", skinSummary(bot));
            case "skin_reroll" -> rerollSkin(player, bot);
            case "pve_status" -> sendActionBarConfirm(player, "PVE 状态", pveStatusLabel(bot));
            default -> {}
        }
    }

    private void rerollSkin(Player player, FakePlayer bot) {
        SkinManager skinManager = plugin.getSkinManager();
        if (skinManager == null || !Config.skinRarePoolsEnabled()) {
            sendActionBarConfirm(player, "重掷皮肤", "✘ 皮肤卡池未启用");
            return;
        }
        // Clearing the resolved skin makes resolveEffectiveSkin roll the pools again - identical
        // odds to a fresh spawn, including the rare tiers.
        bot.setResolvedSkin(null);
        skinManager.resolveEffectiveSkin(bot, skin -> {
            boolean applied = skin != null && skin.isValid() && skinManager.applySkinFromProfile(bot, skin);
            sendActionBarConfirm(player, "重掷皮肤", applied ? skinSummary(bot) : "✘ 重掷失败");
            if (player.isOnline()) build(player);
        });
    }

    private void applyDanger(Player player, FakePlayer bot, String id) {
        if ("reset_all".equals(id)) {
            UUID uuid = player.getUniqueId();
            Long confirmTime = pendingResetConfirm.get(uuid);
            long now = System.currentTimeMillis();

            if (confirmTime == null || now - confirmTime > RESET_CONFIRM_WINDOW_MS) {
                pendingResetConfirm.put(uuid, now);
                player.sendMessage(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("⚠ ").color(DANGER_RED))
                       .append(Component.text("5 秒内再次点击以确认重置。")
                                .color(YELLOW)));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.8f, 0.5f);
                startConfirmCountdown(player, bot, uuid);
                return;
            }

            cancelConfirmCountdown(uuid);
            pendingResetConfirm.remove(uuid);
            resetBot(player, bot, true);
            player.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⟲ ").color(YELLOW))
                   .append(Component.text("已重置所有设置：").color(WHITE))
                    .append(Component.text(bot.getName()).color(ACCENT)));
            return;
        }
        if ("delete".equals(id)) {
            String botName = bot.getName();
            UUID playerUuid = player.getUniqueId();

            pendingDelete.add(playerUuid);
            cleanup(playerUuid);
            player.closeInventory();
            pendingDelete.remove(playerUuid);

            manager.delete(botName, "gui_delete");
            player.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✕ ").color(DANGER_RED))
                    .append(Component.text("已删除假人：").color(WHITE))
                    .append(Component.text(botName).color(ACCENT)));
        }
    }

    /** Ticks the reset-all confirm window every second so its lore shows a live countdown. */
    private void startConfirmCountdown(Player player, FakePlayer bot, UUID uuid) {
        cancelConfirmCountdown(uuid);
        int taskId = FppScheduler.runSyncRepeatingWithId(
                plugin,
                () -> {
                    Long confirmTime = pendingResetConfirm.get(uuid);
                    if (confirmTime == null || !player.isOnline()) {
                        cancelConfirmCountdown(uuid);
                        return;
                    }
                    long remainingMs = RESET_CONFIRM_WINDOW_MS - (System.currentTimeMillis() - confirmTime);
                    if (remainingMs <= 0) {
                        pendingResetConfirm.remove(uuid);
                        cancelConfirmCountdown(uuid);
                        build(player);
                        return;
                    }
                    build(player);
                },
                20L,
                20L);
        confirmTickTaskIds.put(uuid, taskId);
    }

    private void cancelConfirmCountdown(UUID uuid) {
        Integer taskId = confirmTickTaskIds.remove(uuid);
        if (taskId != null) FppScheduler.cancelTask(taskId);
    }

    private void applyInput(Player player, FakePlayer bot, String inputType, String raw) {
        switch (inputType) {
            case "rename" -> {
                String newName = raw.trim();
                String plain = PlainTextComponentSerializer.plainText()
                        .serialize(TextUtil.colorize(newName))
                        .trim();
                if (plain.isEmpty()) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                           .append(Component.text("名字为空或无效。")
                                    .color(GRAY)));
                    return;
                }
                if (plain.length() > RENAME_MAX_LENGTH) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("太长 - 最多 " + RENAME_MAX_LENGTH + " 个字符。")
                                    .color(GRAY)));
                    return;
                }
                manager.renameBot(bot, newName);
                sendActionBarConfirm(player, "已重命名", plain);
            }
            case "auto_eat_threshold" -> {
                int val;
                try {
                    val = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("无效数字 - 请输入 0-19。")
                                    .color(GRAY)));
                    return;
                }
                if (val < 0) val = 0;
                if (val > 19) val = 19;
                int old = bot.getAutoEatHungerThreshold();
                bot.setAutoEatHungerThreshold(val);
                fireSettingChange(bot, "auto_eat_threshold", old, bot.getAutoEatHungerThreshold());
                manager.persistBotSettings(bot);
                sendActionBarConfirm(player, "自动进食阈值", bot.getAutoEatHungerThreshold() + " / 20 饥饿值");
            }
            case "chunk_load_radius" -> {
                int globalMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
                int val;
                try {
                    val = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text(
                                            "无效数字 - 请输入 -1（全局）、0（关闭）或 1-" + globalMax + "。")
                                    .color(GRAY)));
                    return;
                }

                if (val < -1) val = -1;
                if (val > globalMax && globalMax > 0) val = globalMax;
                int old = bot.getChunkLoadRadius();
                bot.setChunkLoadRadius(val);
                fireSettingChange(bot, "chunk_load_radius", old, bot.getChunkLoadRadius());
                manager.persistBotSettings(bot);
                String display = val == -1 ? "全局 (" + globalMax + ")" : val == 0 ? "已关闭" : val + " 区块";
                sendActionBarConfirm(player, "区块半径", display);
            }
            case "pve_range" -> {
                double val;
                try {
                    val = Double.parseDouble(raw.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("无效数字 - 请输入 1-64。")
                                    .color(GRAY)));
                    return;
                }
                if (val < 1) val = 1;
                if (val > 64) val = 64;
                bot.setPveRange(val);
                manager.persistBotSettings(bot);
                restartPveIfActive(bot);
                sendActionBarConfirm(player, "PVE 范围", (int) val + " 格");
            }
            case "rental_extend" -> {
                if (!player.hasPermission(Perm.RENT)) {
                    player.sendMessage(Lang.get("no-permission"));
                    return;
                }
                int hours;
                try {
                    hours = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Lang.get("rent-invalid-hours", "value", raw.trim()));
                    return;
                }
                int min = Config.rentalMinHours();
                int max = Config.rentalMaxHours();
                if (hours < min || hours > max) {
                    player.sendMessage(Lang.get(
                            "rent-hours-out-of-range", "min", String.valueOf(min), "max", String.valueOf(max)));
                    return;
                }
                RentalPurchases.Result result = RentalPurchases.extend(plugin, player, bot, hours);
                player.sendMessage(result.message());
            }
            case "left_click_interval" -> {
                int val;
                try {
                    val = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("无效数字 - 请输入 "
                                            + LeftClickCommand.MIN_INTERVAL_TICKS
                                            + "-"
                                            + LeftClickCommand.MAX_INTERVAL_TICKS
                                            + "，或 0 使用全局默认。")
                                    .color(GRAY)));
                    return;
                }
                if (val <= 0) {
                    val = -1;
                } else {
                    val = Math.max(
                            LeftClickCommand.MIN_INTERVAL_TICKS, Math.min(LeftClickCommand.MAX_INTERVAL_TICKS, val));
                }
                bot.setLeftClickIntervalTicks(val);
                manager.persistBotSettings(bot);
                persistClickIntervals(bot);
                String display = val == -1 ? "全局 (" + Config.leftClickIntervalTicks() + ")" : val + " 刻";
                sendActionBarConfirm(player, "左键间隔", display);
            }
            case "right_click_interval" -> {
                int val;
                try {
                    val = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("✘ ").color(OFF_RED))
                            .append(Component.text("无效数字 - 请输入 "
                                            + RightClickCommand.MIN_INTERVAL_TICKS
                                            + "-"
                                            + RightClickCommand.MAX_INTERVAL_TICKS
                                            + "，或 0 使用全局默认。")
                                    .color(GRAY)));
                    return;
                }
                if (val <= 0) {
                    val = -1;
                } else {
                    val = Math.max(
                            RightClickCommand.MIN_INTERVAL_TICKS, Math.min(RightClickCommand.MAX_INTERVAL_TICKS, val));
                }
                bot.setRightClickIntervalTicks(val);
                manager.persistBotSettings(bot);
                persistClickIntervals(bot);
                String display = val == -1 ? "全局 (" + Config.rightClickIntervalTicks() + ")" : val + " 刻";
                sendActionBarConfirm(player, "右键间隔", display);
            }
        }
    }

    private void persistClickIntervals(FakePlayer bot) {
        var db = plugin.getDatabaseManager();
        if (db != null) {
            db.updateBotClickIntervals(
                    bot.getUuid().toString(), bot.getLeftClickIntervalTicks(), bot.getRightClickIntervalTicks());
        }
    }

    private void openMobSelector(Player player, FakePlayer bot) {
        UUID uuid = player.getUniqueId();
        inMobSelector.add(uuid);
        mobSelectorPage.put(uuid, 0);

        pendingRebuild.add(uuid);
        buildMobSelector(player, bot, 0);
        pendingRebuild.remove(uuid);
    }

    private void buildMobSelector(Player player, FakePlayer bot, int page) {
        UUID uuid = player.getUniqueId();
        int totalPages = Math.max(1, (int) Math.ceil(MOB_LIST.size() / (double) MOB_SLOTS));
        page = Math.min(page, totalPages - 1);
        mobSelectorPage.put(uuid, page);

        Set<String> selectedTypes = bot.getPveMobTypes();

        MobSelectorHolder holder = new MobSelectorHolder(uuid);
        Component title = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("] ").color(DARK_GRAY))
                .append(Component.text(bot.getName()).color(ACCENT))
                .append(Component.text("  ·  ").color(DARK_GRAY))
                .append(Component.text("选择怪物").color(DARK_GRAY))
                .append(Component.text("  (" + (page + 1) + "/" + totalPages + ")")
                        .color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, MOB_GUI_SIZE, title);

        int startIdx = page * MOB_SLOTS;
        int endIdx = Math.min(startIdx + MOB_SLOTS, MOB_LIST.size());
        for (int i = startIdx; i < endIdx; i++) {
            MobDisplay mob = MOB_LIST.get(i);
            boolean selected = selectedTypes.contains(mob.type.name());
            inv.setItem(i - startIdx, buildMobItem(mob, selected));
        }

        inv.setItem(MOB_SLOT_BACK, buildMobBarItem(Material.ARROW, "◄  返回设置", ACCENT));

        inv.setItem(
                MOB_SLOT_PREV_PAGE,
                page > 0
                        ? buildMobBarItem(Material.MAGENTA_STAINED_GLASS_PANE, "◄  上一页", COMING_SOON_COLOR)
                        : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        inv.setItem(47, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(48, glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        boolean isAllHostile = selectedTypes.isEmpty();
        ItemStack clearItem = new ItemStack(isAllHostile ? Material.NETHER_STAR : Material.STRUCTURE_VOID);
        ItemMeta clearMeta = clearItem.getItemMeta();
        if (isAllHostile) {
            clearMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            clearMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        clearMeta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✦  所有敌对生物")
                        .color(isAllHostile ? SELECTED_GREEN : VALUE_YELLOW)
                        .decoration(TextDecoration.BOLD, true)));
        List<Component> clearLore = new ArrayList<>();
        clearLore.add(Component.empty());
        clearLore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(isAllHostile ? "◈  当前生效" : "点击清除所有目标")
                        .color(isAllHostile ? SELECTED_GREEN : DARK_GRAY)));
        clearMeta.lore(clearLore);
        clearItem.setItemMeta(clearMeta);
        inv.setItem(MOB_SLOT_CLEAR, clearItem);

        inv.setItem(50, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(51, glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        inv.setItem(
                MOB_SLOT_NEXT_PAGE,
                page < totalPages - 1
                        ? buildMobBarItem(Material.LIME_STAINED_GLASS_PANE, "▶  下一页", ON_GREEN)
                        : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        inv.setItem(MOB_SLOT_CLOSE, buildCloseButton());

        inMobSelector.add(uuid);
        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
    }

    private void handleMobSelectorClick(Player player, MobSelectorHolder holder, int slot) {
        UUID uuid = player.getUniqueId();
        UUID botUuid = botSessions.get(uuid);
        if (botUuid == null) return;
        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) {
            player.closeInventory();
            return;
        }

        int page = mobSelectorPage.getOrDefault(uuid, 0);

        if (slot == MOB_SLOT_BACK) {
            playUiClick(player, 1.0f);
            inMobSelector.remove(uuid);
            mobSelectorPage.remove(uuid);
            pendingRebuild.add(uuid);
            build(player);
            pendingRebuild.remove(uuid);
            return;
        }

        if (slot == MOB_SLOT_CLOSE) {
            playUiClick(player, 0.8f);
            inMobSelector.remove(uuid);
            mobSelectorPage.remove(uuid);
            player.closeInventory();
            return;
        }

        if (slot == MOB_SLOT_PREV_PAGE && page > 0) {
            playUiClick(player, 1.0f);
            pendingRebuild.add(uuid);
            buildMobSelector(player, bot, page - 1);
            pendingRebuild.remove(uuid);
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(MOB_LIST.size() / (double) MOB_SLOTS));
        if (slot == MOB_SLOT_NEXT_PAGE && page < totalPages - 1) {
            playUiClick(player, 1.0f);
            pendingRebuild.add(uuid);
            buildMobSelector(player, bot, page + 1);
            pendingRebuild.remove(uuid);
            return;
        }

        if (slot == MOB_SLOT_CLEAR) {
            bot.setPveMobTypes(new LinkedHashSet<>());
            manager.persistBotSettings(bot);
            restartPveIfActive(bot);
            playUiClick(player, 1.2f);
            sendActionBarConfirm(player, "怪物目标", "所有敌对");
            pendingRebuild.add(uuid);
            buildMobSelector(player, bot, page);
            pendingRebuild.remove(uuid);
            return;
        }

        if (slot >= 0 && slot < MOB_SLOTS) {
            int mobIdx = page * MOB_SLOTS + slot;
            if (mobIdx >= MOB_LIST.size()) return;

            MobDisplay mob = MOB_LIST.get(mobIdx);
            boolean nowSelected = bot.togglePveMobType(mob.type.name());
            manager.persistBotSettings(bot);
            restartPveIfActive(bot);
            playUiClick(player, 1.2f);
            int count = bot.getPveMobTypes().size();
            String label = nowSelected
                    ? "+" + mob.displayName + " (" + count + " 已选中)"
                    : "-" + mob.displayName + " (" + (count == 0 ? "所有敌对" : count + " 已选中") + ")";
            sendActionBarConfirm(player, "怪物目标", label);

            pendingRebuild.add(uuid);
            buildMobSelector(player, bot, page);
            pendingRebuild.remove(uuid);
        }
    }

    private void openFoodSelector(Player player, FakePlayer bot) {
        UUID uuid = player.getUniqueId();
        inFoodSelector.add(uuid);
        foodSelectorPage.put(uuid, 0);
        pendingRebuild.add(uuid);
        buildFoodSelector(player, bot, 0);
        pendingRebuild.remove(uuid);
    }

    private void buildFoodSelector(Player player, FakePlayer bot, int page) {
        UUID uuid = player.getUniqueId();
        List<BotFoods.FoodDef> foods = BotFoods.all();
        int totalPages = Math.max(1, (int) Math.ceil(foods.size() / (double) MOB_SLOTS));
        page = Math.min(Math.max(0, page), totalPages - 1);
        foodSelectorPage.put(uuid, page);

        Set<Material> selected = bot.getAutoEatFoods();

        FoodSelectorHolder holder = new FoodSelectorHolder(uuid);
        Component title = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("] ").color(DARK_GRAY))
                .append(Component.text(bot.getName()).color(ACCENT))
                .append(Component.text("  ·  ").color(DARK_GRAY))
                .append(Component.text("允许的食物").color(DARK_GRAY))
                .append(Component.text("  (" + (page + 1) + "/" + totalPages + ")")
                        .color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, MOB_GUI_SIZE, title);

        int startIdx = page * MOB_SLOTS;
        int endIdx = Math.min(startIdx + MOB_SLOTS, foods.size());
        for (int i = startIdx; i < endIdx; i++) {
            BotFoods.FoodDef food = foods.get(i);
            inv.setItem(i - startIdx, buildFoodItem(food, selected.contains(food.material())));
        }

        inv.setItem(MOB_SLOT_BACK, buildMobBarItem(Material.ARROW, "◄  返回设置", ACCENT));
        inv.setItem(
                MOB_SLOT_PREV_PAGE,
                page > 0
                        ? buildMobBarItem(Material.MAGENTA_STAINED_GLASS_PANE, "◄  上一页", COMING_SOON_COLOR)
                        : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(47, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(48, glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        boolean anyFood = selected.isEmpty();
        ItemStack clearItem = new ItemStack(anyFood ? Material.NETHER_STAR : Material.STRUCTURE_VOID);
        ItemMeta clearMeta = clearItem.getItemMeta();
        if (anyFood) {
            clearMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            clearMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        clearMeta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✦  任意食物")
                        .color(anyFood ? SELECTED_GREEN : VALUE_YELLOW)
                        .decoration(TextDecoration.BOLD, true)));
        List<Component> clearLore = new ArrayList<>();
        clearLore.add(Component.empty());
        clearLore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(anyFood ? "◈  当前吃任意食物" : "点击允许任意食物")
                        .color(anyFood ? SELECTED_GREEN : DARK_GRAY)));
        clearMeta.lore(clearLore);
        clearItem.setItemMeta(clearMeta);
        inv.setItem(MOB_SLOT_CLEAR, clearItem);

        inv.setItem(50, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(51, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(
                MOB_SLOT_NEXT_PAGE,
                page < totalPages - 1
                        ? buildMobBarItem(Material.LIME_STAINED_GLASS_PANE, "▶  下一页", ON_GREEN)
                        : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(MOB_SLOT_CLOSE, buildCloseButton());

        inFoodSelector.add(uuid);
        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
    }

    private ItemStack buildFoodItem(BotFoods.FoodDef food, boolean selected) {
        ItemStack item = new ItemStack(food.material());
        ItemMeta meta = item.getItemMeta();
        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(food.display())
                        .color(selected ? SELECTED_GREEN : WHITE)
                        .decoration(TextDecoration.BOLD, selected)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("饥饿值  ").color(DARK_GRAY))
                .append(Component.text("+" + food.nutrition()).color(VALUE_YELLOW)));
        lore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(selected ? "◈  已选中" : "◈  点击允许")
                        .color(selected ? SELECTED_GREEN : DARK_GRAY)));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void handleFoodSelectorClick(Player player, FoodSelectorHolder holder, int slot) {
        UUID uuid = player.getUniqueId();
        UUID botUuid = botSessions.get(uuid);
        if (botUuid == null) return;
        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) {
            player.closeInventory();
            return;
        }

        int page = foodSelectorPage.getOrDefault(uuid, 0);
        List<BotFoods.FoodDef> foods = BotFoods.all();
        int totalPages = Math.max(1, (int) Math.ceil(foods.size() / (double) MOB_SLOTS));

        if (slot == MOB_SLOT_BACK) {
            playUiClick(player, 1.0f);
            inFoodSelector.remove(uuid);
            foodSelectorPage.remove(uuid);
            pendingRebuild.add(uuid);
            build(player);
            pendingRebuild.remove(uuid);
            return;
        }
        if (slot == MOB_SLOT_CLOSE) {
            playUiClick(player, 0.8f);
            inFoodSelector.remove(uuid);
            foodSelectorPage.remove(uuid);
            player.closeInventory();
            return;
        }
        if (slot == MOB_SLOT_PREV_PAGE && page > 0) {
            playUiClick(player, 1.0f);
            pendingRebuild.add(uuid);
            buildFoodSelector(player, bot, page - 1);
            pendingRebuild.remove(uuid);
            return;
        }
        if (slot == MOB_SLOT_NEXT_PAGE && page < totalPages - 1) {
            playUiClick(player, 1.0f);
            pendingRebuild.add(uuid);
            buildFoodSelector(player, bot, page + 1);
            pendingRebuild.remove(uuid);
            return;
        }
        if (slot == MOB_SLOT_CLEAR) {
            bot.setAutoEatFoods(new LinkedHashSet<>());
            manager.persistBotSettings(bot);
            playUiClick(player, 1.2f);
            sendActionBarConfirm(player, "自动进食食物", "任意食物");
            pendingRebuild.add(uuid);
            buildFoodSelector(player, bot, page);
            pendingRebuild.remove(uuid);
            return;
        }
        if (slot >= 0 && slot < MOB_SLOTS) {
            int idx = page * MOB_SLOTS + slot;
            if (idx >= foods.size()) return;
            BotFoods.FoodDef food = foods.get(idx);
            boolean nowSelected = bot.toggleAutoEatFood(food.material());
            manager.persistBotSettings(bot);
            playUiClick(player, 1.2f);
            int count = bot.getAutoEatFoods().size();
            String label = nowSelected
                    ? "+" + food.display() + " (" + count + " 已选中)"
                    : "-" + food.display() + " (" + (count == 0 ? "任意食物" : count + " 已选中") + ")";
            sendActionBarConfirm(player, "自动进食食物", label);
            pendingRebuild.add(uuid);
            buildFoodSelector(player, bot, page);
            pendingRebuild.remove(uuid);
        }
    }

    private void openShareSelector(Player player, FakePlayer bot) {
        UUID uuid = player.getUniqueId();
        pendingRebuild.add(uuid);
        buildShareSelector(player, bot);
        pendingRebuild.remove(uuid);
    }

    private void buildShareSelector(Player player, FakePlayer bot) {
        ShareSelectorHolder holder = new ShareSelectorHolder(player.getUniqueId());
        Component title = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("] ").color(DARK_GRAY))
                .append(Component.text(bot.getName()).color(ACCENT))
                .append(Component.text("  ·  ").color(DARK_GRAY))
                .append(Component.text("共享控制").color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);
        int slot = 0;
        for (Player candidate : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            if (manager.getByUuid(candidate.getUniqueId()) != null) continue;
            if (candidate.getUniqueId().equals(bot.getSpawnedByUuid())) continue;
            if (candidate.getUniqueId().equals(player.getUniqueId())) continue;
            inv.setItem(slot++, buildSharePlayerItem(candidate, bot.hasSharedController(candidate.getUniqueId())));
        }
        if (slot == 0) {
            ItemStack item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("没有在线玩家").color(OFF_RED));
            meta.lore(List.of(
                    Component.text("玩家必须在线才能共享控制。").color(GRAY)));
            item.setItemMeta(meta);
            inv.setItem(22, item);
        }
        inv.setItem(45, buildMobBarItem(Material.ARROW, "◄  返回设置", ACCENT));
        for (int i = 46; i < 53; i++) inv.setItem(i, glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(53, buildCloseButton());
        player.openInventory(inv);
    }

    private ItemStack buildSharePlayerItem(Player player, boolean shared) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setPlayerProfile(player.getPlayerProfile());
            if (shared) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            meta.displayName(Component.text(player.getName())
                    .color(shared ? SELECTED_GREEN : ACCENT)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text(shared ? "✔ 可控制此假人" : "✘ 无控制权限")
                            .color(shared ? SELECTED_GREEN : GRAY),
                    Component.text("点击切换").color(YELLOW)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void handleShareSelectorClick(Player player, ShareSelectorHolder holder, int slot) {
        UUID uuid = player.getUniqueId();
        UUID botUuid = botSessions.get(uuid);
        if (botUuid == null) return;
        FakePlayer bot = manager.getByUuid(botUuid);
        if (bot == null) {
            player.closeInventory();
            return;
        }
        if (!BotAccess.canShare(player, bot)) {
            player.sendMessage(Lang.get("no-permission"));
            player.closeInventory();
            return;
        }
        if (slot == 45) {
            playUiClick(player, 1.0f);
            pendingRebuild.add(uuid);
            build(player);
            pendingRebuild.remove(uuid);
            return;
        }
        if (slot == 53) {
            playUiClick(player, 0.8f);
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || !item.hasItemMeta() || item.getItemMeta().displayName() == null) return;
        String targetName = PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) return;
        boolean shared = bot.hasSharedController(target.getUniqueId());
        if (shared) bot.removeSharedController(target.getUniqueId());
        else bot.addSharedController(target.getUniqueId());
        playUiClick(player, shared ? 0.85f : 1.2f);
        sendActionBarConfirm(player, "共享控制", target.getName() + (shared ? " 已撤销" : " 已授予"));
        pendingRebuild.add(uuid);
        buildShareSelector(player, bot);
        pendingRebuild.remove(uuid);
    }

    private ItemStack buildMobItem(MobDisplay mob, boolean selected) {
        ItemStack item = new ItemStack(mob.material);
        ItemMeta meta = item.getItemMeta();

        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        TextColor nameColor = selected ? SELECTED_GREEN : WHITE;
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(mob.displayName).color(nameColor).decoration(TextDecoration.BOLD, selected)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("类型  ").color(DARK_GRAY))
                .append(Component.text(mob.category).color(GRAY)));
        lore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("ɪᴅ  ").color(DARK_GRAY))
                .append(Component.text(mob.type.name().toLowerCase()).color(GRAY)));
        lore.add(Component.empty());
        if (selected) {
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("◈  已锁定").color(SELECTED_GREEN)));
            lore.add(hint("◈ ", "点击移除"));
        } else {
            lore.add(hint("◈ ", "点击添加目标"));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack buildMobBarItem(Material mat, String label, TextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(label).color(color).decoration(TextDecoration.BOLD, true)));
        item.setItemMeta(meta);
        return item;
    }

    private void dropBotInventory(FakePlayer fp) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;

        boolean hasItems = false;
        for (ItemStack item : bot.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                hasItems = true;
                break;
            }
        }
        if (!hasItems) return;

        Location loc = bot.getLocation();
        float origYaw = loc.getYaw();
        float origPitch = loc.getPitch();

        bot.setRotation(origYaw, 90f);
        NmsPlayerSpawner.setHeadYaw(bot, origYaw);

        FppScheduler.runSyncLater(
                plugin,
                () -> {
                    Player b = fp.getPlayer();
                    if (b == null || !b.isOnline()) return;

                    ItemStack[] contents = b.getInventory().getContents().clone();
                    b.getInventory().clear();
                    for (ItemStack item : contents) {
                        if (item != null && item.getType() != Material.AIR) {
                            b.getWorld().dropItemNaturally(b.getLocation(), item);
                        }
                    }

                    FppScheduler.runSyncLater(
                            plugin,
                            () -> {
                                Player b2 = fp.getPlayer();
                                if (b2 == null || !b2.isOnline()) return;
                                b2.setRotation(origYaw, origPitch);
                                NmsPlayerSpawner.setHeadYaw(b2, origYaw);
                            },
                            5L);
                },
                3L);
    }

    private void dropBotXp(FakePlayer fp) {
        Player bot = fp.getPlayer();
        if (bot == null || !bot.isOnline()) return;

        int xp = bot.getTotalExperience();
        if (xp <= 0) return;

        World world = bot.getWorld();
        Location loc = bot.getLocation();
        world.spawn(loc, ExperienceOrb.class, orb -> orb.setExperience(xp));

        bot.setTotalExperience(0);
        bot.setLevel(0);
        bot.setExp(0f);
    }

    private void resetBot(Player player, FakePlayer bot, boolean isOp) {
        fireSettingChange(bot, "reset", null, null);

        bot.setFrozen(false);
        bot.setRespawnOnDeath(Config.respawnOnDeath());
        bot.setHeadAiEnabled(true);
        bot.setSwimAiEnabled(Config.swimAiEnabled());
        bot.setChunkLoadRadius(-1);
        bot.setPickUpItemsEnabled(Config.bodyPickUpItems());
        bot.setPickUpXpEnabled(Config.bodyPickUpXp());

        bot.setAiPersonality(null);
        manager.applyPing(bot, -1);

        bot.setPveEnabled(false);
        var attackCmd = plugin.getAttackCommand();
        if (attackCmd != null) attackCmd.stopAttacking(bot.getUuid());
        bot.setPveRange(Config.attackMobDefaultRange());
        bot.setPvePriority(Config.attackMobDefaultPriority());
        bot.setPveSmartAttackMode(FakePlayer.PveSmartAttackMode.OFF);
        bot.setPveMobTypes(new LinkedHashSet<>());

        bot.setNavParkour(Config.pathfindingParkour());
        bot.setNavBreakBlocks(Config.pathfindingBreakBlocks());
        bot.setNavPlaceBlocks(Config.pathfindingPlaceBlocks());
        if (isOp) bot.setRightClickCommand(null);

        manager.persistBotSettings(bot);
        build(player);
        player.sendActionBar(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⟲ ").color(YELLOW))
                .append(Component.text("假人设置  ").color(WHITE))
                .append(Component.text("已重置为默认值").color(YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    private void openChatInput(Player player, FakePlayer bot, BotEntry entry) {
        UUID uuid = player.getUniqueId();
        int[] guiState = sessions.get(uuid);
        if (guiState == null) return;

        pendingChatInput.add(uuid);
        player.closeInventory();
        pendingChatInput.remove(uuid);

        String promptLabel;
        String currentVal;
        switch (entry.id()) {
            case "rename" -> {
                promptLabel = "新显示名称（最大 " + RENAME_MAX_LENGTH + "）";
                currentVal = bot.getDisplayName();
            }
            case "auto_eat_threshold" -> {
                promptLabel = "饥饿值阈值（0-19）";
                currentVal = bot.getAutoEatHungerThreshold() + " / 20";
            }
            case "chunk_load_radius" -> {
                int gMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
                promptLabel = "半径（-1=全局，0=关闭，1-" + gMax + "）";
                int cur = bot.getChunkLoadRadius();
                currentVal = cur == -1 ? "全局 (" + gMax + ")" : cur == 0 ? "已关闭" : cur + " 区块";
            }
            case "pve_range" -> {
                promptLabel = "检测范围（1-64）";
                currentVal = (int) bot.getPveRange() + " 格";
            }
            case "rental_extend" -> {
                int min = Config.rentalMinHours();
                int max = Config.rentalMaxHours();
                promptLabel = "购买小时数（" + min + "-" + max + "，" + Config.rentalPricePerHour() + "/小时）";
                currentVal = bot.isRented()
                        ? RentalPurchases.formatRemaining(
                                RentalPurchases.currentExpiry(bot) - System.currentTimeMillis())
                        : "永久";
            }
            case "left_click_interval" -> {
                promptLabel = "刻数（"
                        + LeftClickCommand.MIN_INTERVAL_TICKS
                        + "-"
                        + LeftClickCommand.MAX_INTERVAL_TICKS
                        + "，0 = 全局默认）";
                currentVal = bot.getLeftClickIntervalTicks() > 0
                        ? bot.getLeftClickIntervalTicks() + " 刻"
                        : "全局 (" + Config.leftClickIntervalTicks() + ")";
            }
            case "right_click_interval" -> {
                promptLabel = "刻数（"
                        + RightClickCommand.MIN_INTERVAL_TICKS
                        + "-"
                        + RightClickCommand.MAX_INTERVAL_TICKS
                        + "，0 = 全局默认）";
                currentVal = bot.getRightClickIntervalTicks() > 0
                        ? bot.getRightClickIntervalTicks() + " 刻"
                        : "全局 (" + Config.rightClickIntervalTicks() + ")";
            }
            default -> {
                promptLabel = entry.label();
                currentVal = "?";
            }
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("┌─ ").color(DARK_GRAY))
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("]  ").color(DARK_GRAY))
                .append(Component.text("假人设置").color(WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text("  ·  编辑数值").color(DARK_GRAY)));
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY))
                .append(Component.text(entry.label()).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        String[] descLines = entry.description().split("\\\\n|\n");
        for (String line : descLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                for (String wrapped : wrapText(trimmed, 42)) {
                    player.sendMessage(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text("│  ").color(DARK_GRAY))
                            .append(Component.text(wrapped).color(GRAY)));
                }
            }
        }
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY)));
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY))
                .append(Component.text("当前  ").color(DARK_GRAY))
                .append(Component.text(currentVal).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("└─ ").color(DARK_GRAY))
                .append(Component.text("输入新数值，或输入 ").color(GRAY))
                .append(Component.text("cancel").color(OFF_RED).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" 返回。").color(GRAY)));
        player.sendMessage(Component.empty());

        int taskId = FppScheduler.runSyncLaterWithId(
                plugin,
                () -> {
                    ChatInputSes stale = chatSessions.remove(uuid);
                    if (stale != null) {
                        sessions.put(uuid, stale.guiState);
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(Component.empty()
                                    .decoration(TextDecoration.ITALIC, false)
                                    .append(Component.text("✦ ").color(ACCENT))
                                    .append(Component.text("输入超时 - 返回设置。")
                                            .color(GRAY)));
                            build(p);
                        }
                    }
                },
                20L * 60);

        chatSessions.put(uuid, new ChatInputSes(entry.id(), bot.getUuid(), guiState.clone(), taskId));
    }

    private ItemStack buildEntryItem(BotEntry entry, FakePlayer bot, Player viewer) {

        if (entry.type() == BotEntryType.COMING_SOON) {
            ItemStack item = new ItemStack(entry.icon());
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                    .append(Component.text(entry.label())
                            .color(COMING_SOON_COLOR)
                            .decoration(TextDecoration.BOLD, true)));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("数值  ").color(DARK_GRAY))
                    .append(Component.text("⚠ 即将推出")
                            .color(COMING_SOON_COLOR)
                            .decoration(TextDecoration.BOLD, true)));
            lore.add(Component.empty());
            for (String line : entry.description().split("\\\\n|\n")) {
                if (!line.isBlank())
                    lore.add(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text(line).color(GRAY)));
            }
            lore.add(Component.empty());
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                    .append(Component.text("功能不可用").color(DARK_GRAY)));
            meta.lore(lore);
            item.setItemMeta(meta);
            return item;
        }
        boolean isToggle = entry.type() == BotEntryType.TOGGLE;
        boolean isDanger = entry.type() == BotEntryType.DANGER;
        boolean isOn = isToggle && getBoolValue(entry.id(), bot, viewer);

        TextColor nameColor = isDanger ? DANGER_RED : (isToggle ? (isOn ? ON_GREEN : OFF_RED) : ACCENT);
        ItemStack item = new ItemStack(dynamicIcon(entry, bot, viewer));
        ItemMeta meta = item.getItemMeta();

        if (isToggle && isOn) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(entry.label()).color(nameColor).decoration(TextDecoration.BOLD, true)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        TextColor valColor = isDanger ? DANGER_RED : (isToggle ? (isOn ? ON_GREEN : OFF_RED) : VALUE_YELLOW);
        lore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("数值  ").color(DARK_GRAY))
                .append(Component.text(valueString(entry, bot, viewer))
                        .color(valColor)
                        .decoration(TextDecoration.BOLD, true)));
        lore.add(Component.empty());
        for (String line : entry.description().split("\\\\n|\n")) {
            if (!line.isBlank())
                lore.add(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(line).color(isDanger ? DANGER_RED : GRAY)));
        }
        lore.add(Component.empty());
        switch (entry.type()) {
            case TOGGLE -> lore.add(hint("◈ ", "点击切换"));
            case CYCLE_PRIORITY -> lore.add(hint("◈ ", "点击循环切换"));
            case ACTION -> lore.add(hint("✎ ", "点击在聊天中编辑"));
            case MOB_SELECTOR -> lore.add(hint("◈ ", "点击打开怪物选择"));
            case FOOD_SELECTOR -> lore.add(hint("◈ ", "点击打开食物列表"));
            case IMMEDIATE -> lore.add(hint("◈ ", "点击清除"));
            case DANGER -> lore.add(dangerConfirmHint(entry, viewer));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static Component hint(String icon, String text) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(icon).color(ACCENT))
                .append(Component.text(text).color(DARK_GRAY));
    }

    /** Shows a live "confirm within Ns" countdown on the reset-all button while it's armed. */
    private Component dangerConfirmHint(BotEntry entry, Player viewer) {
        if ("reset_all".equals(entry.id()) && viewer != null) {
            Long confirmTime = pendingResetConfirm.get(viewer.getUniqueId());
            if (confirmTime != null) {
                long remainingMs = RESET_CONFIRM_WINDOW_MS - (System.currentTimeMillis() - confirmTime);
                long remainingS = Math.max(0, (remainingMs + 999) / 1000);
                return Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("◈ ").color(DANGER_RED))
                        .append(Component.text("在 " + remainingS + " 秒内确认")
                                .color(YELLOW)
                                .decoration(TextDecoration.BOLD, true));
            }
        }
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("◈ ").color(DANGER_RED))
                .append(Component.text("点击确认").color(DARK_GRAY));
    }

    private String valueString(BotEntry entry, FakePlayer bot, Player viewer) {
        if (entry.valueOverride() != null) return entry.valueOverride();
        return switch (entry.id()) {
            case "show_path" -> PathfindingDebugManager.isViewing(viewer.getUniqueId(), bot.getUuid())
                    ? "✔ 已开启"
                    : "✘ 已关闭";
            case "frozen" -> bot.isFrozen() ? "✔ 已开启" : "✘ 已关闭";
case "respawn_on_death" -> bot.isRespawnOnDeath() ? "✔ 重生" : "✘ 消失";
            case "head_ai_enabled" -> bot.isHeadAiEnabled() ? "✔ 已开启" : "✘ 已关闭";
            case "swim_ai_enabled" -> bot.isSwimAiEnabled() ? "✔ 已开启" : "✘ 已关闭";
            case "pickup_items" -> bot.isPickUpItemsEnabled() ? "✔ 已开启" : "✘ 已关闭";
            case "pickup_xp" -> bot.isPickUpXpEnabled() ? "✔ 已开启" : "✘ 已关闭";
            case "auto_milk" -> bot.isAutoMilkEnabled() ? "✔ 已开启" : "✘ 已关闭";
            case "prevent_bad_omen" -> bot.isPreventBadOmen() ? "✔ 已开启" : "✘ 已关闭";
            case "nav_parkour" -> bot.isNavParkour() ? "✔ 已开启" : "✘ 已关闭";
            case "nav_break_blocks" -> bot.isNavBreakBlocks() ? "✔ 已开启" : "✘ 已关闭";
            case "nav_place_blocks" -> bot.isNavPlaceBlocks() ? "✔ 已开启" : "✘ 已关闭";
            case "pve_enabled" -> pveModeLabel(bot);
case "share_control" -> bot.getSharedControllers().size() + " 人共享";
            case "pve_range" -> (int) bot.getPveRange() + " 格";
            case "pve_priority" -> "lowest-health".equals(bot.getPvePriority()) ? "最低血量" : "最近";
            case "pve_mob_type" -> {
                Set<String> types = bot.getPveMobTypes();
                if (types.isEmpty()) yield "所有敌对";
                if (types.size() == 1) {
                    String t = types.iterator().next();
                    for (MobDisplay md : MOB_LIST) {
                        if (md.type.name().equals(t)) yield md.displayName;
                    }
                    yield t.toLowerCase();
                }
               yield types.size() + " 种怪物";
            }
            case "chunk_load_radius" -> {
                int r = bot.getChunkLoadRadius();
                int gMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
                yield r == -1 ? "全局 (" + gMax + ")" : r == 0 ? "已关闭" : r + " 区块";
            }
            case "rental_extend" -> bot.isRented()
                    ? RentalPurchases.formatRemaining(RentalPurchases.currentExpiry(bot) - System.currentTimeMillis())
                    : "永久";
            case "left_click_interval" -> bot.getLeftClickIntervalTicks() > 0
                    ? bot.getLeftClickIntervalTicks() + " 刻"
                    : "全局 (" + Config.leftClickIntervalTicks() + ")";
            case "right_click_interval" -> bot.getRightClickIntervalTicks() > 0
                    ? bot.getRightClickIntervalTicks() + " 刻"
                    : "全局 (" + Config.rightClickIntervalTicks() + ")";
            case "auto_eat" -> bot.isAutoEatEnabled() ? "✔ 已开启" : "✘ 已关闭";
            case "auto_eat_threshold" -> bot.getAutoEatHungerThreshold() + " / 20 饥饿值";
            case "auto_eat_foods" -> {
                int n = bot.getAutoEatFoods().size();
                yield n == 0 ? "任意食物" : n + " 已选中";
            }
            case "reset_all" -> "⚠ 常规 · 聊天 · PVE · 寻路 · 指令";
            case "delete" -> bot.getName();
            case "skin_info" -> skinSummary(bot);
            case "skin_reroll" -> "点击重掷";
            case "pve_status" -> pveStatusLabel(bot);
            default -> "?";
        };
    }

    /** Live combat state for the PVE status entry: off / scanning / fighting. */
    private String pveStatusLabel(FakePlayer bot) {
        if (!bot.isPveEnabled()) return "✘ 关闭";
        var pve = plugin.getPveController();
        if (pve != null && pve.isEngaged(bot.getUuid())) return "⚔ 战斗中";
        return "◌ 扫描目标中";
    }

    /** One-line summary of the bot's current skin: source/rarity + detected player model. */
    private String skinSummary(FakePlayer bot) {
        SkinProfile skin = bot.getResolvedSkin();
        if (skin == null || !skin.isValid()) return "原版默认";
        SkinModelDetector.SkinModel model = SkinModelDetector.detectFromTextureValue(skin.getValue());
        String modelLabel =
                switch (model) {
                    case SLIM -> "纤细";
                    case CLASSIC -> "经典";
                    case UNKNOWN -> "?";
                };
        return skinRarityLabel(skin.getSource()) + " · " + modelLabel;
    }

    private static String skinRarityLabel(String source) {
        if (source == null) return "自定义";
        if (source.startsWith("pool:")) {
            String tail = source.substring(source.lastIndexOf(':') + 1);
            if ("main".equals(tail)) return "主池";
            if (tail.startsWith("1-in-")) return "✨ 稀有 " + tail.replace("1-in-", "1/");
        }
        if (source.startsWith("despawn:")) return "已恢复";
        return "自定义";
    }

    private boolean getBoolValue(String id, FakePlayer bot, Player viewer) {
        return switch (id) {
            case "show_path" -> PathfindingDebugManager.isViewing(viewer.getUniqueId(), bot.getUuid());
            case "frozen" -> bot.isFrozen();
            case "respawn_on_death" -> bot.isRespawnOnDeath();
            case "head_ai_enabled" -> bot.isHeadAiEnabled();
            case "swim_ai_enabled" -> bot.isSwimAiEnabled();
            case "pickup_items" -> bot.isPickUpItemsEnabled();
            case "pickup_xp" -> bot.isPickUpXpEnabled();
            case "auto_milk" -> bot.isAutoMilkEnabled();
            case "auto_eat" -> bot.isAutoEatEnabled();
            case "prevent_bad_omen" -> bot.isPreventBadOmen();
            case "nav_parkour" -> bot.isNavParkour();
            case "nav_break_blocks" -> bot.isNavBreakBlocks();
            case "nav_place_blocks" -> bot.isNavPlaceBlocks();
            case "pve_enabled" -> bot.isPveEnabled();
            case "pve_move" -> bot.isPveMoveToTarget();
            default -> false;
        };
    }

    private Material dynamicIcon(BotEntry entry, FakePlayer bot, Player viewer) {
        return switch (entry.id()) {
            case "show_path" -> PathfindingDebugManager.isViewing(viewer.getUniqueId(), bot.getUuid())
                    ? Material.FILLED_MAP
                    : Material.MAP;
            case "frozen" -> bot.isFrozen() ? Material.BLUE_ICE : Material.PACKED_ICE;
            case "respawn_on_death" -> bot.isRespawnOnDeath() ? Material.TOTEM_OF_UNDYING : Material.SKELETON_SKULL;
            case "head_ai_enabled" -> bot.isHeadAiEnabled() ? Material.PLAYER_HEAD : Material.SKELETON_SKULL;
            case "swim_ai_enabled" -> bot.isSwimAiEnabled() ? Material.WATER_BUCKET : Material.BUCKET;
            case "pickup_items" -> bot.isPickUpItemsEnabled() ? Material.HOPPER : Material.CHEST;
            case "pickup_xp" -> bot.isPickUpXpEnabled() ? Material.EXPERIENCE_BOTTLE : Material.GLASS_BOTTLE;
            case "auto_milk" -> bot.isAutoMilkEnabled() ? Material.MILK_BUCKET : Material.BUCKET;
            case "prevent_bad_omen" -> bot.isPreventBadOmen() ? Material.OMINOUS_BOTTLE : Material.GLASS_BOTTLE;
            case "nav_parkour" -> bot.isNavParkour() ? Material.SLIME_BALL : Material.RABBIT_FOOT;
            case "nav_break_blocks" -> bot.isNavBreakBlocks() ? Material.DIAMOND_PICKAXE : Material.IRON_PICKAXE;
            case "nav_place_blocks" -> bot.isNavPlaceBlocks() ? Material.GRASS_BLOCK : Material.DIRT;
            case "pve_enabled" -> switch (bot.getPveSmartAttackMode()) {
                case OFF -> Material.WOODEN_SWORD;
                case ON_NO_MOVE -> Material.IRON_SWORD;
                case ON_MOVE -> Material.DIAMOND_SWORD;
            };
            case "share_control" -> Material.PLAYER_HEAD;
            case "pve_mob_type" -> {
                Set<String> types = bot.getPveMobTypes();
                if (types.isEmpty()) yield Material.ZOMBIE_HEAD;
                if (types.size() == 1) {
                    String t = types.iterator().next();
                    for (MobDisplay md : MOB_LIST) {
                        if (md.type.name().equals(t)) yield md.material;
                    }
                }
                yield Material.ZOMBIE_HEAD;
            }
            case "chunk_load_radius" -> bot.getChunkLoadRadius() == 0 ? Material.STRUCTURE_VOID : Material.MAP;
            case "pve_status" -> {
                var pve = plugin.getPveController();
                if (!bot.isPveEnabled()) yield Material.GRAY_DYE;
                yield pve != null && pve.isEngaged(bot.getUuid()) ? Material.DIAMOND_SWORD : Material.SPYGLASS;
            }
            default -> entry.icon();
        };
    }

    private ItemStack buildCategoryTab(BotCategory cat, boolean active) {
        ItemStack item = new ItemStack(active ? cat.activeMat() : cat.inactiveMat());
        ItemMeta meta = item.getItemMeta();
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(cat.label()).color(ACCENT).decoration(TextDecoration.BOLD, active)));
        meta.lore(List.of(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(active ? "◈  正在查看" : "点击切换")
                        .color(active ? ON_GREEN : DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCatArrow(boolean isNext) {
        Material mat = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
        TextColor col = isNext ? ON_GREEN : COMING_SOON_COLOR;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(isNext ? "▶" : "◄").color(col).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("滚动分类 " + (isNext ? "向前" : "向后") + "。")
                        .color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildResetButton() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⟲  重置假人").color(YELLOW)));
        meta.lore(List.of(
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("重置所有假人设置").color(GRAY)),
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("为默认值。").color(GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✕  关闭").color(OFF_RED).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("点击 - 关闭菜单").color(DARK_GRAY)),
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Shift+点击 - 返回假人列表")
                                .color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack glassFiller(Material mat) {
        return GuiKit.glassFiller(mat);
    }

    private static List<BotEntry> visibleEntries(BotCategory cat, boolean isOp) {
        if (isOp) return cat.entries();
        return cat.entries().stream().filter(e -> !e.opOnly()).toList();
    }

    private void cleanup(UUID uuid) {
        UUID botUuid = botSessions.get(uuid);
        if (botUuid != null) {
            releaseBotLock(botUuid, uuid);
            resumeBotAfterEditing(botUuid);
        }
        sessions.remove(uuid);
        botSessions.remove(uuid);
        pendingResetConfirm.remove(uuid);
        cancelConfirmCountdown(uuid);
    }

    private boolean acquireBotLock(UUID botUuid, UUID viewerUuid) {
        UUID owner = botLocks.putIfAbsent(botUuid, viewerUuid);
        return owner == null || owner.equals(viewerUuid);
    }

    private void releaseBotLock(UUID botUuid, UUID viewerUuid) {
        botLocks.remove(botUuid, viewerUuid);
    }

    private void releaseAllEditors(UUID botUuid) {
        botLocks.remove(botUuid);
        for (Map.Entry<UUID, UUID> entry : new HashMap<>(botSessions).entrySet()) {
            if (!botUuid.equals(entry.getValue())) continue;
            Player viewer = Bukkit.getPlayer(entry.getKey());
            if (viewer != null) {
                pendingDelete.add(entry.getKey());
                viewer.closeInventory();
            }
            cleanup(entry.getKey());
            pendingDelete.remove(entry.getKey());
        }
    }

    private void pauseBotForEditing(FakePlayer bot) {
        UUID botUuid = bot.getUuid();
        editPauseCounts.merge(botUuid, 1, Integer::sum);
        bot.setInventoryOpen(true);
        Player player = bot.getPlayer();
        if (player != null && player.isOnline()) {
            manager.lockForAction(botUuid, player.getLocation());
            NmsPlayerSpawner.setMovementForward(player, 0f);
            player.setSprinting(false);
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    private void resumeBotAfterEditing(UUID botUuid) {
        Integer count = editPauseCounts.get(botUuid);
        if (count != null && count > 1) {
            editPauseCounts.put(botUuid, count - 1);
            return;
        }
        editPauseCounts.remove(botUuid);
        manager.unlockAction(botUuid);
        FakePlayer fp = manager.getByUuid(botUuid);
        if (fp != null) fp.setInventoryOpen(false);
    }

    private boolean isOp(Player player) {
        return Perm.has(player, Perm.OP);
    }

    private void sendActionBarConfirm(Player player, String label, String value) {
        player.sendActionBar(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✔ ").color(ON_GREEN))
                .append(Component.text(label + "  ").color(WHITE))
                .append(Component.text("→  ").color(DARK_GRAY))
                .append(Component.text(value).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    private static void playUiClick(Player player, float pitch) {
        GuiKit.playUiClick(player, pitch);
    }

    private BotCategory general() {
        int globalMax = Config.chunkLoadingEnabled() ? Config.chunkLoadingRadius() : 0;
        return new BotCategory(
                "⚙ 常规",
                Material.COMPARATOR,
                Material.GRAY_DYE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                List.of(
                        BotEntry.action(
                                "rename",
                                "重命名假人",
                                "设置此假人的显示名字。\n"
                                        + "显示在头顶、Tab 列表\n"
                                        + "和命令输出中。\n"
                                        + "身份 (UUID) 保持不变。",
                                Material.NAME_TAG,
                                false),
                        BotEntry.toggle(
                                "frozen",
                                "冻结",
                                "假人冻结时无法移动。\n开启即可暂停所有移动。",
                                Material.PACKED_ICE,
                                false),
                        BotEntry.toggle(
                                "respawn_on_death",
                                "死亡后重生",
                                "开启后假人死后会自动重生。\n关闭 = 死亡后消失。",
                                Material.TOTEM_OF_UNDYING,
                                false),
                        BotEntry.toggle(
                                "head_ai_enabled",
                                "头部 AI（看向玩家）",
                                "开启后假人会平滑地转头看向玩家。\n"
                                        + "关闭可让头部保持不动。",
                                Material.PLAYER_HEAD,
                                false),
                        BotEntry.action(
                                "chunk_load_radius",
                                "区块半径",
                                "此假人加载的区块数量。\n"
                                        + "-1 = 跟随全局配置\n"
                                        + "0  = 对此假人关闭\n"
                                        + "1-"
                                        + globalMax
                                        + " = 固定半径（受全局上限限制）",
                                Material.MAP,
                                false),
                        BotEntry.toggle(
                                "pickup_items",
                                "拾取物品",
                                "开启后此假人会把物品实体\n捡进自己的背包。",
                                Material.HOPPER,
                                false),
                        BotEntry.toggle(
                                "pickup_xp",
                                "拾取经验",
                                "开启后此假人会收集经验球。\n" + "/fpp xp 的冷却仍然生效。",
                                Material.EXPERIENCE_BOTTLE,
                                false),
                        BotEntry.toggle(
                                "auto_milk",
                                "自动喝奶",
                                "自动清除负面效果\n"
                                        + "（中毒、凋零、缓慢等）\n"
                                        + "全局: "
                                        + (Config.autoMilkEnabled() ? "已开启" : "已关闭"),
                                Material.MILK_BUCKET,
                                false),
                        BotEntry.toggle(
                                "prevent_bad_omen",
                                "屏蔽不祥之兆",
                                "屏蔽不祥之兆、袭击之兆\n"
                                        + "和试炼之兆效果。\n"
                                        + "防止假人触发袭击。\n"
                                        + "全局: "
                                        + (Config.preventBadOmen() ? "已开启" : "已关闭"),
                                Material.OMINOUS_BOTTLE,
                                false),
                        BotEntry.immediate(
                                "share_control",
                                "共享控制",
                                "打开真实玩家选择器\n"
                                        + "以授予或撤销控制权。\n"
                                        + "仅拥有者和管理员可共享。",
                                Material.PLAYER_HEAD,
                                false),
                        BotEntry.action(
                                "rental_extend",
                                "租赁时间",
                                "此假人的剩余付费时间。\n"
                                        + "点击使用你的经济余额\n"
                                        + "购买更多小时 (" + Config.rentalPricePerHour() + "/时)。\n"
                                        + "未租赁 = 永久，永不过期。",
                                Material.CLOCK,
                                false),
                        BotEntry.action(
                                "left_click_interval",
                                "左键间隔",
                                "重复/长按挖掘时破坏方块之间的间隔刻数。\n"
                                        + "攻击实体不受影响（由武器攻速决定）。\n"
                                        + "全局默认: "
                                        + Config.leftClickIntervalTicks()
                                        + " 刻。",
                                Material.IRON_PICKAXE,
                                false),
                        BotEntry.action(
                                "right_click_interval",
                                "右键间隔",
                                "长按右键的脉冲间隔刻数。\n"
                                        + "原版客户端约用 4。\n"
                                        + "全局默认: "
                                        + Config.rightClickIntervalTicks()
                                        + " 刻。",
                                Material.IRON_HOE,
                                false)));
    }

    private BotCategory pve() {
        return new BotCategory(
                "🗡 ᴘᴠᴇ",
                Material.IRON_SWORD,
                Material.STONE_SWORD,
                Material.LIME_STAINED_GLASS_PANE,
                List.of(
                        BotEntry.immediate(
                                "pve_status",
                                "PVE 状态",
                                "此假人的实时战斗状态:\n"
                                        + "关闭 / 扫描 / 战斗。\n"
                                        + "点击刷新。",
                                Material.SPYGLASS,
                                false),
                        BotEntry.cyclePveMode(
                                "pve_enabled",
                                "智能攻击",
                                "在关闭、开启（不移动）、\n"
                                        + "开启（移动）之间循环。\n"
                                        + "智能攻击使用武器冷却\n"
                                        + "和平滑转身。",
                                Material.IRON_SWORD,
                                false),
                        BotEntry.mobSelector(
                                "pve_mob_type",
                                "选择目标怪物",
                                "打开可视选择器来挑选\n"
                                        + "假人攻击的怪物类型。\n"
                                        + "点击可切换多个怪物。\n"
                                        + "'所有敌对' = 清空全部。",
                                Material.ZOMBIE_HEAD,
                                false),
                        BotEntry.action(
                                "pve_range",
                                "检测范围",
                                "假人扫描攻击怪物的距离（格）。\n"
                                        + "范围: 1 – 64 格。",
                                Material.SPYGLASS,
                                false),
                        BotEntry.cyclePriority(
                                "pve_priority",
                                "目标优先级",
                                "假人如何选择目标。\n" + "循环: 最近 ↔ 最低血量",
                                Material.COMPARATOR,
                                false)));
    }

    private BotCategory pathfinding() {
        return new BotCategory(
                "🧭 寻路",
                Material.COMPASS,
                Material.CLOCK,
                Material.CYAN_STAINED_GLASS_PANE,
                List.of(
                        BotEntry.toggle(
                                "show_path",
                                "显示路径（调试）",
                                "沿此假人的当前寻路路线\n"
                                        + "渲染粒子轨迹，仅你可见\n"
                                        + "（Baritone 风格）。\n"
                                        + "橙色 = 下一个路径点，红色 = 目的地。",
                                Material.MAP,
                                false),
                        BotEntry.toggle(
                                "nav_parkour",
                                "跑酷",
                                "允许寻路器规划短距离跳跃，\n而不是总是绕路。",
                                Material.SLIME_BALL,
                                false),
                        BotEntry.toggle(
                                "nav_break_blocks",
                                "破坏方块",
                                "允许假人导航时\n挖穿阻挡的方块。",
                                Material.DIAMOND_PICKAXE,
                                false),
                        BotEntry.toggle(
                                "nav_place_blocks",
                                "放置方块（搭桥）",
                                "允许假人导航时放置方块\n跨越沟壑。",
                                Material.GRASS_BLOCK,
                                false)));
    }

    private BotCategory skin() {
        return new BotCategory(
                "🎨 皮肤",
                Material.PAINTING,
                Material.ITEM_FRAME,
                Material.MAGENTA_STAINED_GLASS_PANE,
                List.of(
                        BotEntry.immediate(
                                "skin_info",
                                "当前皮肤",
                                "此假人正在使用的皮肤:\n"
                                        + "来源（主池 / 稀有档 / 自定义）和\n"
                                        + "玩家模型（纤细/经典，自动检测）。",
                                Material.PAINTING,
                                false),
                        BotEntry.immediate(
                                "skin_reroll",
                                "重掷皮肤",
                                "从卡池中新抽一个皮肤——\n"
                                        + "稀有档概率与全新生成一致。\n"
                                        + "新皮肤会像正常抽取一样保存。",
                                Material.EXPERIENCE_BOTTLE,
                                false)));
    }

    private BotCategory autoEat() {
        return new BotCategory(
                "🍖 自动进食",
                Material.COOKED_BEEF,
                Material.BEEF,
                Material.ORANGE_STAINED_GLASS_PANE,
                List.of(
                        BotEntry.toggle(
                                "auto_eat",
                                "自动进食",
                                "开启后，假人饿了会从背包\n"
                                        + "拿食物吃。它会暂停手头\n"
                                        + "的事，吃完后再切回\n"
                                        + "原来的物品。",
                                Material.COOKED_CHICKEN,
                                false),
                        BotEntry.action(
                                "auto_eat_threshold",
                                "饥饿阈值",
                                "当饥饿值降到该数值或以下时进食\n"
                                        + "（0-19，20 为满饱食度）。\n"
                                        + "越高 = 越早/越频繁进食。",
                                Material.CLOCK,
                                false),
                        BotEntry.foodSelector(
                                "auto_eat_foods",
                                "允许的食物",
                                "挑选假人可吃的食物。\n"
                                        + "优先级: 副手 → 快捷栏 → 背包。\n"
                                        + "未选择 = 吃任何食物。",
                                Material.APPLE,
                                false)));
    }

    private BotCategory danger() {
        return new BotCategory(
                "⚠ 危险操作",
                Material.TNT,
                Material.COAL,
                Material.RED_STAINED_GLASS_PANE,
                List.of(
                        BotEntry.danger(
                                "reset_all",
                                "重置所有设置",
                                "⚠ 将此假人的所有设置\n重置为默认值。\n"
                                        + "常规、聊天、PVE、寻路、\n"
                                        + "指令 - 全部重置。",
                                Material.REDSTONE_BLOCK,
                                true),
                        BotEntry.danger(
                                "delete",
                                   "删除假人",
                                   "⚠ 永久移除此假人。\n此操作无法撤销。",
                                Material.TNT,
                                true)));
    }

    private record GuiHolder(UUID uuid) implements InventoryHolder {
        @SuppressWarnings("NullableProblems")
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MobSelectorHolder(UUID playerUuid) implements InventoryHolder {
        @SuppressWarnings("NullableProblems")
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record FoodSelectorHolder(UUID playerUuid) implements InventoryHolder {
        @SuppressWarnings("NullableProblems")
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record ShareSelectorHolder(UUID playerUuid) implements InventoryHolder {
        @SuppressWarnings("NullableProblems")
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record MobDisplay(EntityType type, Material material, String displayName, String category) {}

    private record BotCategory(
            String label, Material activeMat, Material inactiveMat, Material separatorGlass, List<BotEntry> entries) {}

    private static List<String> wrapText(String text, int maxLen) {
        return GuiKit.wrapText(text, maxLen);
    }

    private enum BotEntryType {
        TOGGLE,
        CYCLE_PRIORITY,
        CYCLE_PVE_MODE,
        ACTION,
        MOB_SELECTOR,
        FOOD_SELECTOR,
        IMMEDIATE,
        DANGER,
        COMING_SOON
    }

    private record BotEntry(
            String id,
            String label,
            String description,
            Material icon,
            BotEntryType type,
            boolean opOnly,
            String valueOverride) {
        BotEntry(String id, String label, String description, Material icon, BotEntryType type, boolean opOnly) {
            this(id, label, description, icon, type, opOnly, null);
        }

        static BotEntry toggle(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.TOGGLE, opOnly);
        }

        static BotEntry cyclePriority(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_PRIORITY, opOnly);
        }

        static BotEntry cyclePveMode(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.CYCLE_PVE_MODE, opOnly);
        }

        static BotEntry action(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.ACTION, opOnly);
        }

        static BotEntry mobSelector(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.MOB_SELECTOR, opOnly);
        }

        static BotEntry foodSelector(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.FOOD_SELECTOR, opOnly);
        }

        static BotEntry immediate(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.IMMEDIATE, opOnly);
        }

        static BotEntry danger(String id, String label, String desc, Material icon, boolean opOnly) {
            return new BotEntry(id, label, desc, icon, BotEntryType.DANGER, opOnly);
        }

        static BotEntry comingSoon(String id, String label, String desc, Material icon) {
            return new BotEntry(id, label, desc, icon, BotEntryType.COMING_SOON, false);
        }
    }

    private record ChatInputSes(String inputType, UUID botUuid, int[] guiState, int cleanupTaskId) {}
}
