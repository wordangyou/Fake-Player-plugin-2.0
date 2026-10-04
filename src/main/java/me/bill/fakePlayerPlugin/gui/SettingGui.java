package me.bill.fakePlayerPlugin.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

import com.destroystokyo.paper.profile.PlayerProfile;

import me.bill.fakePlayerPlugin.FakePlayerPlugin;
import me.bill.fakePlayerPlugin.config.Config;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayer;
import me.bill.fakePlayerPlugin.fakeplayer.FakePlayerManager;
import me.bill.fakePlayerPlugin.fakeplayer.NmsPlayerSpawner;
import me.bill.fakePlayerPlugin.fakeplayer.pathfinding.PathfindingDebugManager;
import me.bill.fakePlayerPlugin.permission.Perm;
import me.bill.fakePlayerPlugin.util.AttributionManager;
import me.bill.fakePlayerPlugin.util.FppScheduler;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class SettingGui implements Listener {

    private static final TextColor ACCENT = GuiKit.ACCENT;
    private static final TextColor ON_GREEN = GuiKit.ON_GREEN;
    private static final TextColor OFF_RED = GuiKit.OFF_RED;
    private static final TextColor VALUE_YELLOW = GuiKit.VALUE_YELLOW;
    private static final TextColor YELLOW = GuiKit.YELLOW;
    private static final TextColor GRAY = GuiKit.GRAY;
    private static final TextColor DARK_GRAY = GuiKit.DARK_GRAY;
    private static final TextColor WHITE = GuiKit.WHITE;
    private static final TextColor COMING_SOON_COLOR = GuiKit.COMING_SOON_COLOR;

    private static final int SIZE = 54;
    private static final int SETTINGS_PER_PAGE = 45;
    private static final int SLOT_RESET = 45;
    private static final int SLOT_CAT_PREV = 46;
    private static final int SLOT_CAT_NEXT = 52;
    private static final int SLOT_CLOSE = 53;

    private static final int CAT_WINDOW = 5;

    private static final int CAT_WINDOW_START = 47;

    private static final UUID SKIN_OWNER_UUID = UUID.fromString("a318f9f4-e2bf-479c-a47a-6a2c1b0b9e66");
    private static final String SKIN_OWNER_NAME = "F_PP";

    private static final long SKULL_TTL_MS = 30L * 60 * 1_000;

    private volatile ItemStack cachedOwnerSkull = null;
    private volatile long skullRefreshedAt = 0L;

    private final FakePlayerPlugin plugin;

    private final Map<UUID, int[]> sessions = new HashMap<>();

    private final Map<UUID, ChatInputSession> chatSessions = new HashMap<>();

    private final Set<UUID> pendingChatInput = new HashSet<>();

    private final Set<UUID> pendingRebuild = new HashSet<>();

    private final Category[] categories;

    public SettingGui(FakePlayerPlugin plugin) {
        this.plugin = plugin;
        this.categories = new Category[] {general(), body(), auth(), perf(), debug()};

        if (!AttributionManager.quickAuthorCheck()) {
            // Attribution integrity check failed - silently continuing.
        }
    }

    public void open(Player player) {
        sessions.put(player.getUniqueId(), new int[] {0, 0, 0});
        build(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getInventory())) return;
        if (!Perm.has(player, Perm.SETTINGS)) return;

        int[] state = sessions.get(holder.uuid);
        if (state == null) return;

        List<SettingsTabRef> tabs = visibleTabs(player);
        if (tabs.isEmpty()) return;

        int slot = event.getSlot();
        int catIdx = state[0];
        int pageIdx = state[1];
        int catOffset = state[2];
        if (catIdx >= tabs.size()) catIdx = tabs.size() - 1;
        SettingsTabRef currentTab = tabs.get(catIdx);

        if (slot == SLOT_RESET) {
            playUiClick(player, 0.6f);
            resetAllCategories(player);
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
            if (catOffset + CAT_WINDOW < tabs.size()) {
                playUiClick(player, 1.0f);
                state[2]++;
            }
            build(player);
            return;
        }

        if (slot == SLOT_CLOSE) {
            playUiClick(player, 0.8f);
            if (event.isShiftClick() && Perm.has(player, Perm.LIST)) {
                // Switching inventories still fires the close event, so settings save as usual.
                player.performCommand("fpp list");
                return;
            }
            player.closeInventory();
            return;
        }

        if (slot >= CAT_WINDOW_START && slot < CAT_WINDOW_START + CAT_WINDOW) {
            int ci = catOffset + (slot - CAT_WINDOW_START);
            if (ci < tabs.size()) {
                if (ci != catIdx) playUiClick(player, 1.3f);
                state[0] = ci;
                state[1] = 0;
                build(player);
            }
            return;
        }

        int settingIdx = slotToSettingIdx(slot);
        if (settingIdx >= 0) {
            List<SettingEntry> settings = currentTab.entries(player);
            int entryIdx = pageIdx * SETTINGS_PER_PAGE + settingIdx;
            if (entryIdx >= settings.size()) return;

            SettingEntry entry = settings.get(entryIdx);

            if (PATHFINDING_DEBUG_ALL_KEY.equals(entry.configKey)) {
                boolean newState = !PathfindingDebugManager.isViewingAny(player.getUniqueId());
                for (FakePlayer fp : plugin.getFakePlayerManager().getActivePlayers()) {
                    PathfindingDebugManager.setViewing(player.getUniqueId(), fp.getUuid(), newState);
                }
                playUiClick(player, newState ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label, newState ? "✔ 已开启" : "✘ 已关闭");
                build(player);
                return;
            }

            if (entry.type == SettingType.COMING_SOON) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.8f, 1.0f);
                player.sendActionBar(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                        .append(Component.text(entry.label + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
                        .append(Component.text("- 即将推出")
                                .color(COMING_SOON_COLOR)
                                .decoration(TextDecoration.BOLD, true)));
                return;
            }

            if (entry.type == SettingType.ACTION) {
                if (entry.clickAction != null) entry.clickAction.run();
                else handleAction(player, entry.configKey);
                build(player);
            } else if (entry.type == SettingType.TOGGLE) {
                entry.apply(plugin);
                plugin.saveConfig();
                Config.reload();
                applyLiveEffect(entry.configKey);
                String newVal = entry.currentValueString(plugin);
                playUiClick(player, newVal.startsWith("✔") ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label, newVal);
                build(player);
            } else if (entry.type == SettingType.DEBUG_TOGGLE) {
                entry.applyDebugToggle();
                Config.reload();
                String newVal = entry.currentValueString(plugin);
                playUiClick(player, newVal.startsWith("✔") ? 1.2f : 0.85f);
                sendActionBarConfirm(player, entry.label, newVal);
                build(player);
            } else {
                playUiClick(player, 1.0f);
                openChatInput(player, entry, state.clone());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;

        if (pendingChatInput.contains(uuid)) return;

        if (pendingRebuild.contains(uuid)) return;
        sessions.remove(uuid);

        plugin.saveConfig();
        Config.reload();

        if (event.getReason() != InventoryCloseEvent.Reason.DISCONNECT && event.getPlayer() instanceof Player player) {
            player.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✔ ").color(ON_GREEN))
                    .append(Component.text("设置已保存 • 配置已更新").color(WHITE)));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSession ses = chatSessions.remove(uuid);
        if (ses == null) return;

        event.setCancelled(true);
        handleChatInput(
                uuid,
                ses,
                PlainTextComponentSerializer.plainText()
                        .serialize(event.message())
                        .trim());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyPlayerChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ChatInputSession ses = chatSessions.remove(uuid);
        if (ses == null) return;

        event.setCancelled(true);
        handleChatInput(uuid, ses, event.getMessage().trim());
    }

    private void handleChatInput(UUID uuid, ChatInputSession ses, String raw) {
        FppScheduler.cancelTask(ses.cleanupTaskId);

        sessions.put(uuid, ses.guiState);
        FppScheduler.runSync(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) return;

            if (raw.equalsIgnoreCase("cancel")) {
                p.sendMessage(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("✦ ").color(ACCENT))
                        .append(Component.text("已取消 - 返回设置。")
                                .color(GRAY)));
                build(p);
                return;
            }

            boolean ok = tryApply(p, ses.entry, raw);
            if (ok) {
                plugin.saveConfig();
                Config.reload();
                applyLiveEffect(ses.entry.configKey);
                sendActionBarConfirm(p, ses.entry.label, ses.entry.currentValueString(plugin));
            }
            build(p);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.remove(uuid);
        pendingChatInput.remove(uuid);
        ChatInputSession ses = chatSessions.remove(uuid);
        if (ses != null) {
            FppScheduler.cancelTask(ses.cleanupTaskId);
        }
    }

    private void openChatInput(Player player, SettingEntry entry, int[] guiState) {
        UUID uuid = player.getUniqueId();

        pendingChatInput.add(uuid);
        player.closeInventory();
        pendingChatInput.remove(uuid);

        String currentVal = entry.currentValueString(plugin).replace("✔ ", "").replace("✘ ", "");

        player.sendMessage(Component.empty());
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("┌─ ").color(DARK_GRAY))
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("]  ").color(DARK_GRAY))
                .append(Component.text("设置").color(WHITE).decoration(TextDecoration.BOLD, true))
                .append(Component.text("  ·  编辑数值").color(DARK_GRAY)));
        player.sendMessage(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("│  ").color(DARK_GRAY))
                .append(Component.text(entry.label).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
        for (String line : entry.description.split("\\\\n|\n")) {
            if (!line.isBlank()) {
                player.sendMessage(Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("│  ").color(DARK_GRAY))
                        .append(Component.text(line).color(GRAY)));
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
                .append(Component.text("输入新数值，或 ").color(GRAY))
                .append(Component.text("cancel").color(OFF_RED).decoration(TextDecoration.BOLD, true))
                .append(Component.text(" 返回。").color(GRAY)));
        player.sendMessage(Component.empty());

        int taskId = FppScheduler.runSyncLaterWithId(
                plugin,
                () -> {
                    ChatInputSession stale = chatSessions.remove(uuid);
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

        chatSessions.put(uuid, new ChatInputSession(entry, guiState, taskId));
    }

    private boolean tryApply(Player player, SettingEntry entry, String raw) {
        var cfg = plugin.getConfig();
        try {
            switch (entry.type) {
                case CYCLE_INT -> {
                    int val = Integer.parseInt(raw);
                    if (val < 0) {
                        player.sendMessage(Component.empty()
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text("✘ ").color(OFF_RED))
                                .append(Component.text("数值必须为 ").color(GRAY))
                                .append(Component.text("0 或更大").color(VALUE_YELLOW))
                                .append(Component.text("。").color(GRAY)));
                        return false;
                    }
                    cfg.set(entry.configKey, val);
                }
                case CYCLE_DOUBLE -> {
                    double val = Double.parseDouble(raw);
                    if (val < 0) {
                        player.sendMessage(Component.empty()
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text("✘ ").color(OFF_RED))
                                .append(Component.text("数值必须为 ").color(GRAY))
                                .append(Component.text("0 或更大").color(VALUE_YELLOW))
                                .append(Component.text("。").color(GRAY)));
                        return false;
                    }
                    cfg.set(entry.configKey, val);
                }
                case STRING -> {
                    if (raw.isBlank()) {
                        player.sendMessage(Component.empty()
                                .decoration(TextDecoration.ITALIC, false)
                                .append(Component.text("✘ ").color(OFF_RED))
                                .append(Component.text("数值不能为空。").color(GRAY)));
                        return false;
                    }
                    cfg.set(entry.configKey, raw);
                }
                default -> {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✘ ").color(OFF_RED))
                    .append(Component.text("\"").color(GRAY))
                    .append(Component.text(raw).color(VALUE_YELLOW))
                    .append(Component.text("\" 不是有效数字。").color(GRAY)));
            return false;
        }
        return true;
    }

    private void build(Player player) {
        UUID uuid = player.getUniqueId();
        int[] state = sessions.get(uuid);
        if (state == null) return;

        int catIdx = state[0];
        int pageIdx = state[1];
        int catOffset = state[2];
        List<SettingsTabRef> tabs = visibleTabs(player);
        if (tabs.isEmpty()) return;
        if (catIdx >= tabs.size()) catIdx = tabs.size() - 1;
        state[0] = catIdx;
        SettingsTabRef tab = tabs.get(catIdx);

        GuiHolder holder = new GuiHolder(uuid);
        Component title = Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("[").color(DARK_GRAY))
                .append(Component.text("ꜰᴘᴘ").color(ACCENT))
                .append(Component.text("] ").color(DARK_GRAY))
                .append(Component.text(tab.label()).color(DARK_GRAY));

        Inventory inv = Bukkit.createInventory(holder, SIZE, title);

        List<SettingEntry> settings = tab.entries(player);
        int settingsCount = settings.size();
        int totalPages = Math.max(1, (int) Math.ceil(settingsCount / (double) SETTINGS_PER_PAGE));
        pageIdx = Math.min(pageIdx, Math.max(0, totalPages - 1));
        state[1] = pageIdx;

        int startIdx = pageIdx * SETTINGS_PER_PAGE;
        int endIdx = Math.min(startIdx + SETTINGS_PER_PAGE, settingsCount);
        for (int i = startIdx; i < endIdx; i++) {
            inv.setItem(i - startIdx, buildSettingItem(settings.get(i), player));
        }

        inv.setItem(SLOT_RESET, buildResetAllButton());

        inv.setItem(
                SLOT_CAT_PREV, catOffset > 0 ? buildCatArrow(false) : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        for (int i = 0; i < CAT_WINDOW; i++) {
            int ci = catOffset + i;
            inv.setItem(
                    CAT_WINDOW_START + i,
                    ci < tabs.size()
                            ? buildCategoryTab(tabs.get(ci), ci == catIdx)
                            : glassFiller(Material.GRAY_STAINED_GLASS_PANE));
        }

        inv.setItem(
                SLOT_CAT_NEXT,
                catOffset + CAT_WINDOW < tabs.size()
                        ? buildCatArrow(true)
                        : glassFiller(Material.GRAY_STAINED_GLASS_PANE));

        inv.setItem(SLOT_CLOSE, buildCloseButton());

        pendingRebuild.add(uuid);
        player.openInventory(inv);
        pendingRebuild.remove(uuid);
        sessions.put(uuid, state);
    }

    private static int slotToSettingIdx(int slot) {
        return slot < 45 ? slot : -1;
    }

    private static int settingIdxToSlot(int localIdx) {
        return localIdx;
    }

    private ItemStack buildSettingItem(SettingEntry entry, Player viewer) {

        if (entry.type == SettingType.COMING_SOON) {

            ItemStack item =
                    "skin.guaranteed-skin".equals(entry.configKey) ? getOwnerSkull() : new ItemStack(entry.icon);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⊘ ").color(COMING_SOON_COLOR))
                    .append(Component.text(entry.label)
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
            for (String line : entry.description.split("\\\\n|\n")) {
                if (!line.isBlank()) {
                    lore.add(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text(line).color(GRAY)));
                }
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

        boolean isToggle = entry.type == SettingType.TOGGLE;
        boolean isOn = isToggle
                && (PATHFINDING_DEBUG_ALL_KEY.equals(entry.configKey)
                        ? PathfindingDebugManager.isViewingAny(viewer.getUniqueId())
                        : plugin.getConfig().getBoolean(entry.configKey, false));

        TextColor nameColor = isToggle ? (isOn ? ON_GREEN : OFF_RED) : ACCENT;

        ItemStack item = "skin.guaranteed-skin".equals(entry.configKey) ? getOwnerSkull() : new ItemStack(entry.icon);
        ItemMeta meta = item.getItemMeta();

        if (isToggle && isOn) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(entry.label).color(nameColor).decoration(TextDecoration.BOLD, true)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        String valStr = PATHFINDING_DEBUG_ALL_KEY.equals(entry.configKey)
                ? (isOn ? "✔ 已开启" : "✘ 已关闭")
                : entry.currentValueString(plugin);
        TextColor valColor = isToggle ? (isOn ? ON_GREEN : OFF_RED) : VALUE_YELLOW;
        lore.add(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("数值  ").color(DARK_GRAY))
                .append(Component.text(valStr).color(valColor).decoration(TextDecoration.BOLD, true)));
        lore.add(Component.empty());

        String[] descLines = entry.description.split("\\\\n|\n");
        for (String line : descLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                for (String wrapped : wrapText(trimmed, 40)) {
                    lore.add(Component.empty()
                            .decoration(TextDecoration.ITALIC, false)
                            .append(Component.text(wrapped).color(GRAY)));
                }
            }
        }
        lore.add(Component.empty());

        if (isToggle) {
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("◈ ").color(ACCENT))
                    .append(Component.text("点击切换").color(DARK_GRAY)));
        } else if (entry.type == SettingType.ACTION) {
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("⚠ ").color(OFF_RED))
                    .append(Component.text("点击执行此操作").color(DARK_GRAY)));
        } else {
            lore.add(Component.empty()
                    .decoration(TextDecoration.ITALIC, false)
                    .append(Component.text("✎ ").color(ACCENT))
                    .append(Component.text("点击在聊天中设置数值").color(DARK_GRAY)));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<SettingsTabRef> visibleTabs(Player viewer) {
        List<SettingsTabRef> tabs = new ArrayList<>(categories.length);
        for (Category category : categories) {
            tabs.add(new SettingsTabRef(category));
        }
        return tabs;
    }

    private ItemStack buildCategoryTab(SettingsTabRef tab, boolean active) {
        Material mat = active ? tab.activeMat() : tab.inactiveMat();
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(tab.label()).color(ACCENT).decoration(TextDecoration.BOLD, active)));
        meta.lore(List.of(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(active ? "◈  正在查看" : "点击切换")
                        .color(active ? ON_GREEN : DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildCatArrow(boolean isNext) {
        Material mat = isNext ? Material.LIME_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
        String label = isNext ? "▶" : "◄";
        TextColor col = isNext ? ON_GREEN : COMING_SOON_COLOR;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(label).color(col).decoration(TextDecoration.BOLD, true)));
        meta.lore(List.of(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("滚动分类 " + (isNext ? "向前" : "向后") + "。")
                        .color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private record SettingsTabRef(Category builtin) {

        String label() {
            return builtin.label;
        }

        Material activeMat() {
            return builtin.activeMat;
        }

        Material inactiveMat() {
            return builtin.inactiveMat;
        }

        Material separatorGlass() {
            return builtin.separatorGlass;
        }

        List<SettingEntry> entries(Player viewer) {
            return builtin.settings;
        }
    }

    private ItemStack buildResetAllButton() {
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⟲  重置所有").color(YELLOW).decoration(TextDecoration.BOLD, false)));
        meta.lore(List.of(
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("重置所有分类中的").color(GRAY)),
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("全部设置为默认值。").color(GRAY))));
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
                        .append(Component.text("点击 - 保存并关闭").color(DARK_GRAY)),
                Component.empty()
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text("Shift+点击 - 保存并打开假人列表")
                                .color(DARK_GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack getOwnerSkull() {
        long now = System.currentTimeMillis();
        ItemStack cached = cachedOwnerSkull;
        if (cached != null && (now - skullRefreshedAt) < SKULL_TTL_MS) {
            return cached.clone();
        }

        ItemStack skull = buildSkullSync();
        cachedOwnerSkull = skull;
        skullRefreshedAt = now;

        scheduleSkullRefresh();
        return skull.clone();
    }

    private ItemStack buildSkullSync() {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            PlayerProfile profile = Bukkit.createProfile(SKIN_OWNER_UUID, SKIN_OWNER_NAME);
            meta.setPlayerProfile(profile);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private void scheduleSkullRefresh() {
        FppScheduler.runAsync(plugin, () -> {
            try {
                PlayerProfile profile = Bukkit.createProfile(SKIN_OWNER_UUID, SKIN_OWNER_NAME);
                profile.complete(true);
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                if (meta != null) {
                    meta.setPlayerProfile(profile);
                    skull.setItemMeta(meta);
                }
                cachedOwnerSkull = skull;
                skullRefreshedAt = System.currentTimeMillis();
            } catch (Exception ignored) {

            }
        });
    }

    private void resetAllCategories(Player player) {
        var cfg = plugin.getConfig();
        var defaults = cfg.getDefaults();
        for (Category cat : categories) {
            for (SettingEntry entry : cat.settings) {
                switch (entry.type) {
                    case TOGGLE -> cfg.set(
                            entry.configKey, defaults != null ? defaults.getBoolean(entry.configKey, false) : false);
                    case CYCLE_INT -> cfg.set(
                            entry.configKey,
                            defaults != null
                                    ? defaults.getInt(entry.configKey, entry.intValues[0])
                                    : entry.intValues[0]);
                    case CYCLE_DOUBLE -> cfg.set(
                            entry.configKey,
                            defaults != null
                                    ? defaults.getDouble(entry.configKey, entry.dblValues[0])
                                    : entry.dblValues[0]);
                    case STRING -> cfg.set(
                            entry.configKey, defaults != null ? defaults.getString(entry.configKey, "") : "");
                    default -> {}
                }
            }
        }
        plugin.saveConfig();
        Config.reload();
        for (Category cat : categories) {
            for (SettingEntry entry : cat.settings) {
                applyLiveEffect(entry.configKey);
            }
        }
        build(player);
        player.sendActionBar(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("⟲ ").color(YELLOW))
                .append(Component.text("所有设置  ").color(WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text("已重置为默认值").color(YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    private ItemStack glassFiller(Material mat) {
        return GuiKit.glassFiller(mat);
    }

    private void applyLiveEffect(String configKey) {
        FakePlayerManager fpm = plugin.getFakePlayerManager();

        if (configKey.startsWith("performance.self-profiler")) {
            var profiler = plugin.getBuiltinProfiler();
            if (profiler != null) {
                if (Config.performanceSelfProfilerEnabled()) profiler.start();
                else profiler.stop();
                profiler.setMethodLevelEnabled(Config.performanceSelfProfilerMethodLevel());
            }
            return;
        }

        if (configKey.startsWith("performance.")) {
            // Every performance.* key (interval, thresholds, spark toggle, ...) is only read at
            // PerformanceMonitor#start() time, so a plain stop()+start() picks up whatever changed -
            // start() itself checks performance.enabled and no-ops if it's now off.
            var monitor = plugin.getPerformanceMonitor();
            if (monitor != null) {
                monitor.stop();
                monitor.start();
            }
            return;
        }

        if (configKey.startsWith("auth.")) {
            // Nothing to restart live - auth.* is read fresh on every bot join (see
            // BotAuthManager#handleBotJoin), so a config change only affects the *next* join.
            return;
        }

        if (configKey.equals("body.pushable")
                || configKey.equals("body.damageable")
                || configKey.equals("combat.max-health")) {
            if (fpm != null) fpm.applyBodyConfig();
            return;
        }

        if (configKey.equals("body.pick-up-items")) {
            boolean enabled = plugin.getConfig().getBoolean("body.pick-up-items", false);
            if (fpm != null) {

                fpm.getActivePlayers().forEach(fp -> {
                    Player body = fp.getPlayer();
                    if (body != null) body.setCanPickupItems(enabled && fp.isPickUpItemsEnabled());
                });
                if (!enabled) {
                    fpm.getActivePlayers().forEach(this::dropBotInventoryWithAnimation);
                }
            }
            return;
        }

        if (configKey.equals("body.pick-up-xp")) {
            boolean enabled = plugin.getConfig().getBoolean("body.pick-up-xp", true);
            if (!enabled && fpm != null) {
                fpm.getActivePlayers().forEach(fp -> {
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
                });
            }
            return;
        }

        if (configKey.equals("skin.guaranteed-skin")) {
            boolean enabled = plugin.getConfig().getBoolean("skin.guaranteed-skin", false);
            if (fpm != null && plugin.getSkinManager() != null) {
                fpm.getActivePlayers().forEach(fp -> {
                    Player bot = fp.getPlayer();
                    if (bot == null || !bot.isOnline()) return;

                    if (enabled) {

                        plugin.getSkinManager().resolveEffectiveSkin(fp, skin -> {
                            if (skin == null || !skin.isValid()) {
                                Config.debugSkin(
                                        "SettingGui: no valid skin" + " resolved for bot '" + fp.getName() + "'");
                                return;
                            }
                            FppScheduler.runSyncLater(
                                    plugin,
                                    () -> {
                                        Player b = fp.getPlayer();
                                        if (b == null || !b.isOnline()) return;
                                        plugin.getSkinManager().applySkinFromProfile(fp, skin);
                                        Config.debugSkin("SettingGui:"
                                                + " re-applied"
                                                + " custom"
                                                + " skin"
                                                + " for bot"
                                                + " '"
                                                + fp.getName()
                                                + "'");
                                    },
                                    3L);
                        });
                    } else {

                        boolean reset = plugin.getSkinManager().resetToDefaultSkin(fp);
                        Config.debugSkin(
                                "SettingGui: reset bot '" + fp.getName() + "' to default skin (success=" + reset + ")");
                    }
                });
            }
        }
    }

    private void dropBotInventoryWithAnimation(FakePlayer fp) {
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

    private void sendActionBarConfirm(Player player, String label, String newVal) {
        player.sendActionBar(Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text("✔ ").color(ON_GREEN))
                .append(Component.text(label + "  ").color(WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text("→  ").color(DARK_GRAY))
                .append(Component.text(newVal).color(VALUE_YELLOW).decoration(TextDecoration.BOLD, true)));
    }

    private static void playUiClick(Player player, float pitch) {
        GuiKit.playUiClick(player, pitch);
    }

    private Category general() {
        return new Category(
                "⚙ 常规",
                Material.COMPARATOR,
                Material.GRAY_DYE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                List.of(
                        SettingEntry.toggle(
                                "persistence.enabled",
                                "重启后保留",
                                "假人在服务器重启后\n恢复到上次位置。",
                                Material.ENDER_CHEST),
                        SettingEntry.toggle(
                                "chunk-loading.enabled",
                                "区块加载",
                                "假人像真实玩家一样\n保持周围区块加载。",
                                Material.GRASS_BLOCK),
                        SettingEntry.cycleInt(
                                "spawn-cooldown",
                                "生成冷却（秒）",
                                "每位玩家两次 /fpp spawn\n之间的秒数。0 = 禁用。",
                                Material.CLOCK,
                                new int[] {0, 10, 30, 60, 120, 300}),
                        SettingEntry.cycleInt(
                                "limits.max-bots",
                                "全局假人上限",
                                "全服最大假人数量。\n0 = 无限制。",
                                Material.CHEST,
                                new int[] {10, 25, 50, 100, 250, 500, 1000}),
                        SettingEntry.cycleInt(
                                "limits.user-bot-limit",
                                "单玩家假人上限",
                                "拥有 fpp.user.spawn 权限玩家的\n默认个人上限。",
                                Material.SHIELD,
                                new int[] {1, 2, 3, 5, 10}),
                        SettingEntry.cycleInt(
                                "chunk-loading.radius",
                                "区块加载半径",
                                "假人离开前的最长随机延迟。\n20 = 1 秒。",
                                Material.COMPASS,
                                new int[] {0, 2, 4, 6, 8, 12, 16}),
                        SettingEntry.cycleInt(
                                "chunk-loading.radius-duplicate",
                                "区块加载半径",
                                "每个假人周围保持加载的\n区块半径。0 = 服务器默认。",
                                Material.COMPASS,
                                new int[] {0, 2, 4, 6, 8, 12, 16}),
                        SettingEntry.action(
                                "reset-all-bots",
                                "重置所有假人",
                                "移除所有活动假人并\n" + "清除其正在运行的任务。",
                                Material.TNT)));
    }

    private void handleAction(Player player, String key) {
        if ("reset-all-bots".equals(key)) {
            int count = plugin.getFakePlayerManager().getActivePlayers().size();
            plugin.getFakePlayerManager().removeAll();
            player.sendMessage(Component.text("已重置 " + count + " 个活动假人。", YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    private Category body() {
        return new Category(
                "🤖 实体",
                Material.ARMOR_STAND,
                Material.ARMOR_STAND,
                Material.LIME_STAINED_GLASS_PANE,
                List.of(
                        SettingEntry.toggle(
                                "body.pushable",
                                "可推动",
                                "允许玩家和实体\n推动假人身体。",
                                Material.PISTON),
                        SettingEntry.toggle(
                                "body.damageable",
                                "可受伤",
                                "假人承受玩家/实体伤害。\n关闭 = 仅免疫 PVP/怪物伤害。",
                                Material.IRON_SWORD),
                        SettingEntry.toggle(
                                "body.pick-up-items",
                                "拾取物品",
                                "假人像真实玩家一样\n从地面拾取物品。",
                                Material.HOPPER),
                        SettingEntry.toggle(
                                "body.pick-up-xp",
                                "拾取经验",
                                "假人从地面收集经验球。",
                                Material.EXPERIENCE_BOTTLE),
                        SettingEntry.toggle(
                                "head-ai.enabled",
                                "头部 AI",
                                "假人平滑转向面朝\n范围内最近的玩家。",
                                Material.ENDER_EYE),
                        SettingEntry.toggle(
                                "swim-ai.enabled",
                                "游泳 AI",
                                "假人淹没在水中或岩浆中时\n使用基础漂浮/跳跃游泳 AI。",
                                Material.WATER_BUCKET),
                        SettingEntry.toggle(
                                "death.respawn-on-death",
                                "死亡后重生",
                                "假人被杀死后自动回来。",
                                Material.TOTEM_OF_UNDYING),
                        SettingEntry.toggle(
                                "death.suppress-drops",
                                "抑制掉落",
                                "假人死亡时不掉落\n物品或经验。",
                                Material.CHEST),
                        SettingEntry.toggle(
                                "body.drop-items-on-despawn",
                                "移除时掉落",
                                "假人被移除时掉落背包 + 经验。\n关闭 = 同名假人下次生成时\n记住其物品。",
                                Material.ENDER_CHEST),
                        SettingEntry.cycleDouble(
                                "combat.max-health",
                                "最大生命（半心）",
                                "假人基础生命。20 = 10 颗心。\n" + "生成时与 /fpp reload 时应用。",
                                Material.GOLDEN_APPLE,
                                new double[] {5, 10, 15, 20, 40}),
                        SettingEntry.cycleInt(
                                "death.respawn-delay",
                                "重生延迟（刻）",
                                "死亡假人归来前的刻数。\n1 = 立即  ·  20 = 1 秒。",
                                Material.CLOCK,
                                new int[] {1, 5, 10, 15, 20, 40, 60, 100})));
    }

    private Category auth() {
        return new Category(
                "🔐 登录验证",
                Material.TRIPWIRE_HOOK,
                Material.STRING,
                Material.PURPLE_STAINED_GLASS_PANE,
                List.of(
                        SettingEntry.toggle(
                                "auth.enabled",
                                "启用认证",
                                "假人针对已安装的登录插件\n自动注册/登录。需要开启\ndatabase.enabled 才能记住密码。",
                                Material.TRIPWIRE_HOOK),
                        SettingEntry.text(
                                "auth.register-command",
                                "注册命令",
                                "假人首次加入时发送。\n%password% 会替换为其\n生成的密码。不加前导斜杠。",
                                Material.WRITABLE_BOOK),
                        SettingEntry.text(
                                "auth.login-command",
                                "登录命令",
                                "假人之后每次加入时发送，\n使用其记住的密码。\n不加前导斜杠。",
                                Material.WRITTEN_BOOK),
                        SettingEntry.cycleInt(
                                "auth.delay-min-ticks",
                                "最短延迟（刻）",
                                "注册/登录命令触发前的\n最短随机延迟。\n20 = 1 秒。",
                                Material.CLOCK,
                                new int[] {0, 10, 20, 40, 60}),
                        SettingEntry.cycleInt(
                                "auth.delay-max-ticks",
                                "最长延迟（刻）",
                                "注册/登录命令触发前的\n最长随机延迟。\n60 = 3 秒。",
                                Material.CLOCK,
                                new int[] {20, 40, 60, 100, 200}),
                        SettingEntry.cycleInt(
                                "auth.pending-timeout-ticks",
                                "等待超时（刻）",
                                "假人等待结果时保持冻结的\n最长时限。\n100 = 5 秒。",
                                Material.REPEATER,
                                new int[] {40, 60, 100, 200, 400}),
                        SettingEntry.cycleInt(
                                "auth.password.length",
                                "密码长度",
                                "生成密码的字符数。\n如果你的登录插件有最大长度\n限制，请保持在其之下。",
                                Material.NAME_TAG,
                                new int[] {8, 10, 12, 16, 24}),
                        SettingEntry.toggle(
                                "auth.password.uppercase",
                                "密码：大写字母",
                                "包含大写字母。",
                                Material.PAPER),
                        SettingEntry.toggle(
                                "auth.password.lowercase",
                                "密码：小写字母",
                                "包含小写字母。",
                                Material.PAPER),
                        SettingEntry.toggle(
                                "auth.password.digits", "密码：数字", "包含数字。", Material.PAPER),
                        SettingEntry.toggle(
                                "auth.password.symbols",
                                "密码：符号",
                                "包含符号 (!@#%^&*-_=+)。",
                                Material.PAPER)));
    }

    private Category perf() {
        return new Category(
                "📈 性能",
                Material.SPYGLASS,
                Material.CLOCK,
                Material.CYAN_STAINED_GLASS_PANE,
                List.of(
                        SettingEntry.toggle(
                                "performance.enabled",
                                "启用监控",
                                "监视服务器健康（TPS；\n装有 Spark 时含 MSPT/CPU/GC），\n并在出现问题时于控制台警告。",
                                Material.SPYGLASS),
                        SettingEntry.toggle(
                                "performance.spark-enabled",
                                "Spark 集成",
                                "使用已安装的 Spark 获取 MSPT/CPU/GC\n数据，并启用 /fpp perf spark 的\nCPU 分析器。",
                                Material.OBSERVER),
                        SettingEntry.toggle(
                                "performance.placeholders",
                                "占位符",
                                "通过 PlaceholderAPI 暴露性能数据\n（%fpp_tps% 等）。",
                                Material.PAPER),
                        SettingEntry.cycleInt(
                                "performance.sample-interval-ticks",
                                "采样间隔（刻）",
                                "多久采集一次快照。\n20 = 1 秒。",
                                Material.CLOCK,
                                new int[] {20, 40, 60, 100, 200}),
                        SettingEntry.cycleInt(
                                "performance.history-minutes",
                                "历史记录（分钟）",
                                "为 /fpp perf history 保留\n样本的时长。",
                                Material.BOOK,
                                new int[] {5, 10, 15, 30, 60}),
                        SettingEntry.cycleDouble(
                                "performance.warn-mspt",
                                "警告 MSPT",
                                "每刻毫秒数（MSPT）的控制台\n警告阈值（需要 Spark）。",
                                Material.REDSTONE_TORCH,
                                new double[] {40.0, 50.0, 60.0, 80.0, 100.0}),
                        SettingEntry.cycleDouble(
                                "performance.warn-tps",
                                "警告 TPS",
                                "每秒刻数（TPS）跌破该值时的\n控制台警告阈值。",
                                Material.REDSTONE_TORCH,
                                new double[] {15.0, 16.0, 18.0, 19.0}),
                        SettingEntry.cycleInt(
                                "performance.warn-consecutive-samples",
                                "警告采样数",
                                "触发警告前所需的连续坏样本数。\n避免一次性卡顿尖峰误报。",
                                Material.TARGET,
                                new int[] {1, 2, 3, 5, 10}),
                        SettingEntry.cycleInt(
                                "performance.warn-cooldown-minutes",
                                "警告冷却（分）",
                                "重复控制台警告之间的\n最短间隔。",
                                Material.CLOCK,
                                new int[] {1, 3, 5, 10, 30}),
                        SettingEntry.cycleInt(
                                "performance.auto-profiler-timeout-seconds",
                                "自动分析超时（秒）",
                                "警告触发时自动启动的 Spark\n分析会话的最长运行时间。",
                                Material.MAGMA_CREAM,
                                new int[] {30, 60, 90, 120}),
                        SettingEntry.toggle(
                                "performance.self-profiler.enabled",
                                "自分析器",
                                "为 /fpp perf report 计时插件\n自身代码的各个区段。开启时有\n少量开销。",
                                Material.COMPARATOR),
                        SettingEntry.toggle(
                                "performance.self-profiler.method-level",
                                "自分析器：方法级",
                                "更精细的方法计时。\n开销更高 - 仅用于深度\n诊断。",
                                Material.REDSTONE),
                        SettingEntry.toggle(
                                "performance.self-profiler.export-on-warning",
                                "警告时导出",
                                "控制台警告触发时自动保存\n完整性能报告。",
                                Material.CHEST)));
    }

    private static final String PATHFINDING_DEBUG_ALL_KEY = "pathfinding_debug_all";

    private Category debug() {
        return new Category(
                "🐛 调试",
                Material.REDSTONE_TORCH,
                Material.LEVER,
                Material.RED_STAINED_GLASS_PANE,
                List.of(
                        SettingEntry.toggle(
                                PATHFINDING_DEBUG_ALL_KEY,
                                "显示所有路径",
                                "切换寻路粒子轨迹（与假人\n"
                                        + "自身设置相同），对所有当前\n"
                                        + "活动假人生效，仅你可见。此后\n"
                                        + "生成的假人不会自动应用 - 重新点击以纳入。",
                                Material.MAP),
                        // ── Core switches ────────────────────────────────────
                        SettingEntry.debugToggle(
                                "enabled", "总调试开关", "启用所有调试输出。", Material.BEACON),
                        SettingEntry.debugToggle(
                                "debug-chat",
                                "调试聊天",
                                "向 OP/notify 玩家广播调试输出。",
                                Material.BOOK),
                        SettingEntry.debugToggle("general", "常规", "基础调试信息。", Material.PAPER),
                        SettingEntry.debugToggle(
                                "startup", "启动", "初始化期间的详细日志。", Material.CAMPFIRE),
                        // ── Bot systems ──────────────────────────────────────
                        SettingEntry.debugToggle(
                                "pathfinding",
                                "寻路",
                                "详细导航诊断（卡住/重算，\n"
                                        + "放弃、看门狗、挖矿停滞）输出到控制台。",
                                Material.TARGET),
                        SettingEntry.debugToggle(
                                "skin-pool",
                                "皮肤池",
                                "完整皮肤流水线追踪：池加载、稀有度抽取、\n"
                                        + "缓存命中、下载、模型检测、MineSkin\n"
                                        + "签名以及皮肤应用。",
                                Material.ARMOR_STAND),
                        SettingEntry.debugToggle(
                                "commands", "命令", "命令执行调试。", Material.COMMAND_BLOCK),
                        SettingEntry.debugToggle(
                                "head-ai", "头部 AI", "假人头部旋转与 AI 目标选择。", Material.ENDER_EYE),
                        SettingEntry.debugToggle("chat", "聊天", "假人聊天系统调试。", Material.OAK_SIGN),
                        SettingEntry.debugToggle(
                                "right-click",
                                "右键",
                                "玩家-假人交互（右键）。",
                                Material.OAK_DOOR),
                        SettingEntry.debugToggle(
                                "right-click-head",
                                "右键头部",
                                "右键点击假人头部。",
                                Material.CARVED_PUMPKIN),
                        SettingEntry.debugToggle(
                                "left-click", "左键", "假人左键（破坏/攻击）。", Material.IRON_PICKAXE),
                        SettingEntry.debugToggle(
                                "left-click-head",
                                "左键头部",
                                "左键瞄准/目标选择。",
                                Material.SKELETON_SKULL),
                        // ── NMS internals ────────────────────────────────────
                        SettingEntry.debugToggle(
                                "nms.enabled", "NMS 总开关", "底层 NMS 交互。", Material.COMPASS),
                        SettingEntry.debugToggle("nms.bot", "NMS 假人", "假人生命周期事件。", Material.PLAYER_HEAD),
                        SettingEntry.debugToggle(
                                "nms.connection",
                                "NMS 连接",
                                "NMS 连接/数据包监听事件。",
                                Material.REDSTONE),
                        SettingEntry.debugToggle(
                                "nms.damage",
                                "NMS 伤害",
                                "假人伤害判定与取消。",
                                Material.IRON_SWORD),
                        SettingEntry.debugToggle(
                                "nms.physics", "NMS 物理", "物理与移动刻操作。", Material.ANVIL),
                        SettingEntry.debugToggle(
                                "nms.skin", "NMS 皮肤", "皮肤应用与解析。", Material.LEATHER),
                        // ── Storage & network ────────────────────────────────
                        SettingEntry.debugToggle(
                                "database.enabled", "数据库总开关", "所有数据库调试。", Material.CHEST),
                        SettingEntry.debugToggle(
                                "database.connection", "数据库连接", "连接生命周期。", Material.HOPPER),
                        SettingEntry.debugToggle(
                                "database.operations", "数据库操作", "查询操作。", Material.WRITABLE_BOOK),
                        SettingEntry.debugToggle(
                                "database.migration",
                                "数据库迁移",
                                "迁移与表结构操作。",
                                Material.FURNACE),
                        SettingEntry.debugToggle(
                                "database.persistence",
                                "数据库持久化",
                                "持久化保存/加载。",
                                Material.ENDER_CHEST),
                        SettingEntry.debugToggle("packets", "数据包", "数据包注入调试。", Material.MAP),
                        SettingEntry.debugToggle(
                                "network", "网络", "多服务器网络操作。", Material.SPYGLASS),
                        SettingEntry.debugToggle(
                                "config-sync",
                                "配置同步",
                                "跨网络配置同步。",
                                Material.REPEATER)));
    }

    private static final class GuiHolder implements InventoryHolder {
        final UUID uuid;

        GuiHolder(UUID uuid) {
            this.uuid = uuid;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private record Category(
            String label,
            Material activeMat,
            Material inactiveMat,
            Material separatorGlass,
            List<SettingEntry> settings) {}

    private enum SettingType {
        TOGGLE,
        CYCLE_INT,
        CYCLE_DOUBLE,
        STRING,
        ACTION,
        COMING_SOON,
        DEBUG_TOGGLE
    }

    private record ChatInputSession(SettingEntry entry, int[] guiState, int cleanupTaskId) {}

    private static final class SettingEntry {
        final String configKey;
        final String label;
        final String description;
        final Material icon;
        final SettingType type;
        final int[] intValues;
        final double[] dblValues;
        final String valueOverride;
        final Runnable clickAction;

        private SettingEntry(
                String configKey,
                String label,
                String description,
                Material icon,
                SettingType type,
                int[] intValues,
                double[] dblValues,
                String valueOverride,
                Runnable clickAction) {
            this.configKey = configKey;
            this.label = label;
            this.description = description;
            this.icon = icon;
            this.type = type;
            this.intValues = intValues;
            this.dblValues = dblValues;
            this.valueOverride = valueOverride;
            this.clickAction = clickAction;
        }

        static SettingEntry toggle(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon, SettingType.TOGGLE, null, null, null, null);
        }

        static SettingEntry cycleInt(String key, String label, String desc, Material icon, int[] values) {
            return new SettingEntry(key, label, desc, icon, SettingType.CYCLE_INT, values, null, null, null);
        }

        static SettingEntry cycleDouble(String key, String label, String desc, Material icon, double[] values) {
            return new SettingEntry(key, label, desc, icon, SettingType.CYCLE_DOUBLE, null, values, null, null);
        }

        static SettingEntry text(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon, SettingType.STRING, null, null, null, null);
        }

        static SettingEntry comingSoon(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon, SettingType.COMING_SOON, null, null, null, null);
        }

        static SettingEntry action(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon, SettingType.ACTION, null, null, null, null);
        }

        static SettingEntry action(
                String key, String label, String desc, Material icon, String valueOverride, Runnable clickAction) {
            return new SettingEntry(key, label, desc, icon, SettingType.ACTION, null, null, valueOverride, clickAction);
        }

        static SettingEntry debugToggle(String key, String label, String desc, Material icon) {
            return new SettingEntry(key, label, desc, icon, SettingType.DEBUG_TOGGLE, null, null, null, null);
        }

        String currentValueString(FakePlayerPlugin plugin) {
            if (valueOverride != null) return valueOverride;
            var cfg = plugin.getConfig();
            return switch (type) {
                case TOGGLE -> cfg.getBoolean(configKey, false) ? "✔ 已开启" : "✘ 已关闭";
                case DEBUG_TOGGLE -> Config.debugBoolValue(configKey, false) ? "✔ 已开启" : "✘ 已关闭";
                case CYCLE_INT -> String.valueOf(cfg.getInt(configKey, intValues[0]));
                case STRING -> cfg.getString(configKey, "");
                case ACTION -> "点击执行";
                case CYCLE_DOUBLE -> {
                    double d = cfg.getDouble(configKey, dblValues[0]);
                    yield (d == Math.floor(d) && !Double.isInfinite(d))
                            ? String.valueOf((int) d)
                            : String.format("%.2f", d);
                }
                case COMING_SOON -> "⚠ 即将推出";
            };
        }

        void apply(FakePlayerPlugin plugin) {
            if (type == SettingType.TOGGLE) {
                plugin.getConfig().set(configKey, !plugin.getConfig().getBoolean(configKey, false));
            }
        }

        void applyDebugToggle() {
            if (type == SettingType.DEBUG_TOGGLE) {
                Config.setDebugBool(configKey, !Config.debugBoolValue(configKey, false));
            }
        }
    }

    private static List<String> wrapText(String text, int maxLen) {
        return GuiKit.wrapText(text, maxLen);
    }
}
