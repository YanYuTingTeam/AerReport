package com.aermini.report.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.aermini.report.AerReport;
import com.aermini.report.managers.ConfigManager;
import com.aermini.report.managers.ConfigManager.MenuConfig;
import com.aermini.report.managers.ConfigManager.ReasonConfig;
import com.aermini.report.managers.ReportManager;
import com.aermini.report.models.PlayerInfo;
import com.aermini.report.models.ReportRecord;
import com.aermini.report.utils.GUIUtils;

public class GUIManager {
    private final AerReport plugin;
    private final ConfigManager config;
    private final ReportManager reportManager;
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, String> selectedPlayers = new java.util.concurrent.ConcurrentHashMap<>();
    // 防重复点击, 异步查库期间同一玩家的后续点击直接丢弃
    private final java.util.Set<java.util.UUID> openingSet = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // 面板渲染时缓存当页记录, 点击时不用再查库
    private final java.util.concurrent.ConcurrentHashMap<java.util.UUID, java.util.Map<Integer, ReportRecord>> panelCache = new java.util.concurrent.ConcurrentHashMap<>();

    public GUIManager(AerReport plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.reportManager = plugin.getReportManager();
    }
    
    public void setSelectedPlayer(org.bukkit.entity.Player player, String playerName) {
        selectedPlayers.put(player.getUniqueId(), playerName);
    }
    
    public String getSelectedPlayer(org.bukkit.entity.Player player) {
        return selectedPlayers.get(player.getUniqueId());
    }
    
    public void removeSelectedPlayer(java.util.UUID uuid) {
        selectedPlayers.remove(uuid);
    }

    public boolean startOpening(java.util.UUID uuid) {
        return openingSet.add(uuid);
    }

    public void finishOpening(java.util.UUID uuid) {
        openingSet.remove(uuid);
    }

    public ReportRecord getCachedPanelReport(java.util.UUID viewer, int reportId) {
        java.util.Map<Integer, ReportRecord> cache = panelCache.get(viewer);
        return cache != null ? cache.get(reportId) : null;
    }

    public void removePanelCache(java.util.UUID uuid) {
        panelCache.remove(uuid);
    }

    private void openInvSync(Player player, Inventory inv) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.openInventory(inv);
        });
    }
    
    private int getSlot(MenuConfig menuConfig, String itemName) {
        List<Integer> slots = menuConfig.getSlots(itemName);
        if (slots == null || slots.isEmpty()) {
            plugin.getLogger().severe("配置错误: 菜单 [" + menuConfig.getTitle() + "] 的 [" + itemName + "] 槽位未配置，请检查config.yml");
            return -1;
        }
        return slots.get(0);
    }

    public void openReportsGUI(Player player, int page) {
        MenuConfig menuConfig = config.getMenuConfig("reports");
        if (menuConfig == null) return;
        String title = menuConfig.getTitle();
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int pageSize = menuConfig.getSlots("player").size();
        int totalPages = (int) Math.ceil((double) onlinePlayers.size() / pageSize);
        if (totalPages == 0) totalPages = 1;
        int startIndex = (page - 1) * pageSize;
        List<Integer> slots = menuConfig.getSlots("player");
        for (int i = 0; i < slots.size() && startIndex + i < onlinePlayers.size(); i++) {
            Player target = onlinePlayers.get(startIndex + i);
            ItemStack item = GUIUtils.createPlayerHead(target.getName(), 
                    menuConfig.getName("player").replace("{players}", target.getName()),
                    Arrays.asList(GUIUtils.colorize(menuConfig.getLore("player").get(0))));
            inv.setItem(slots.get(i), item);
        }
        if (page > 1) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1, 
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            int slot = getSlot(menuConfig, "prev");
            if (slot >= 0) inv.setItem(slot, prevItem);
        }
        if (page < totalPages) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            int slot = getSlot(menuConfig, "next");
            if (slot >= 0) inv.setItem(slot, nextItem);
        }
        ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) inv.setItem(closeSlot, closeItem);
        reportManager.setPlayerPage(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    public void openReportReasonGUI(Player player, String targetName) {
        MenuConfig menuConfig = config.getMenuConfig("report");
        if (menuConfig == null) return;
        selectedPlayers.put(player.getUniqueId(), targetName);
        String title = menuConfig.getTitle();
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        ItemStack playerHead = GUIUtils.createPlayerHead(targetName,
                menuConfig.getName("player").replace("{reported}", targetName),
                menuConfig.getLore("player"));
        int playerSlot = getSlot(menuConfig, "player");
        if (playerSlot >= 0) inv.setItem(playerSlot, playerHead);
        ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) inv.setItem(closeSlot, closeItem);
        List<ReasonConfig> reasons = config.getReasonList();
        List<Integer> slots = menuConfig.getSlots("reasons");
        for (int i = 0; i < slots.size() && i < reasons.size(); i++) {
            ReasonConfig reason = reasons.get(i);
            ItemStack item = GUIUtils.createItem(reason.getItem(), 1, reason.getData(),
                    GUIUtils.colorize("&5" + reason.getName()),
                    Arrays.asList(GUIUtils.colorize("&9举报" + reason.getName())));
            inv.setItem(slots.get(i), item);
        }
        player.openInventory(inv);
    }

    public void openPunishSelectGUI(Player player, int page) {
        MenuConfig menuConfig = config.getMenuConfig("reports");
        if (menuConfig == null) return;
        String title = "选择玩家处罚";
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        int pageSize = menuConfig.getSlots("player").size();
        int totalPages = (int) Math.ceil((double) onlinePlayers.size() / pageSize);
        if (totalPages == 0) totalPages = 1;
        int startIndex = (page - 1) * pageSize;
        List<Integer> slots = menuConfig.getSlots("player");
        for (int i = 0; i < slots.size() && startIndex + i < onlinePlayers.size(); i++) {
            Player target = onlinePlayers.get(startIndex + i);
            ItemStack item = GUIUtils.createPlayerHead(target.getName(), 
                    menuConfig.getName("player").replace("{players}", target.getName()),
                    Arrays.asList(GUIUtils.colorize("&c点击处罚此玩家")));
            inv.setItem(slots.get(i), item);
        }
        if (page > 1) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1, 
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            int slot = getSlot(menuConfig, "prev");
            if (slot >= 0) inv.setItem(slot, prevItem);
        }
        if (page < totalPages) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            int slot = getSlot(menuConfig, "next");
            if (slot >= 0) inv.setItem(slot, nextItem);
        }
        ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) inv.setItem(closeSlot, closeItem);

        reportManager.setPlayerPage(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    public void openPanelGUI(Player player, int page) {
        MenuConfig menuConfig = config.getMenuConfig("panel");
        if (menuConfig == null) return;
        plugin.getDatabaseManager().asyncRun(() -> {
        String title = menuConfig.getTitle();
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        ItemStack mainItem = GUIUtils.createItem(menuConfig.getItem("main"), 1,
                menuConfig.getData("main"), menuConfig.getName("main"), menuConfig.getLore("main"));
        int mainSlot = getSlot(menuConfig, "main");
        if (mainSlot >= 0) inv.setItem(mainSlot, mainItem);

        int pageSize = menuConfig.getSlots("report").size();
        List<ReportRecord> reports = reportManager.getPendingReports(page, pageSize);
        int totalReports = reportManager.getPendingReportCount();
        int totalPages = (int) Math.ceil((double) totalReports / pageSize);
        if (totalPages == 0) totalPages = 1;
        List<Integer> slots = menuConfig.getSlots("report");
        java.util.Map<Integer, ReportRecord> pageCache = new java.util.HashMap<>();
        for (int i = 0; i < reports.size() && i < slots.size(); i++) {
            ReportRecord record = reports.get(i);
            pageCache.put(record.getId(), record);
            ItemStack item = createReportItem(record, menuConfig, player);
            inv.setItem(slots.get(i), item);
        }
        panelCache.put(player.getUniqueId(), pageCache);
        if (page > 1) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1,
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            int prevSlot = getSlot(menuConfig, "prev");
            if (prevSlot >= 0) inv.setItem(prevSlot, prevItem);
        }
        if (page < totalPages) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            int nextSlot = getSlot(menuConfig, "next");
            if (nextSlot >= 0) inv.setItem(nextSlot, nextItem);
        }
        reportManager.setStaffPage(player.getUniqueId(), page);
        openInvSync(player, inv);
        });
    }

    private ItemStack createReportItem(ReportRecord record, MenuConfig menuConfig, Player viewer) {
        List<String> lore = new ArrayList<>(menuConfig.getLore("report"));
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", record.getReporterName())
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", String.valueOf(record.getReportCount()))
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", record.getReasonName())
                      .replace("{reportTime}", record.getTimeWithSuffix())
                      .replace("{reportTimestamp}", String.valueOf(record.getReportTime()))
                      .replace("{reportTimeFormat}", record.getFormattedTime())
                      .replace("{reportType}", record.getReportType())
                      .replace("{reportChat}", record.getChatMessages() != null ? record.getChatMessages() : "");
            lore.set(i, line);
        }
        String name = menuConfig.getName("report").replace("{reportID}", String.valueOf(record.getId()));
        String itemMaterial;
        int itemData = 0;
        if ("processing".equals(record.getStatus())) {
            String processingUuid = record.getProcessingBy();
            String processingBy = reportManager.getStaffNameByUuid(processingUuid);
            if (processingBy != null) {
                if (viewer.getUniqueId().toString().equals(processingUuid)) {
                    lore.add(0, GUIUtils.colorize("&e&l你正在处理此举报"));
                } else {
                    lore.add(0, GUIUtils.colorize("&c&l正在被 " + processingBy + " 处理中..."));
                }
            }
            itemMaterial = menuConfig.getSection().getString("report.item.working", "REDSTONE");
            itemData = menuConfig.getSection().getInt("report.data.working", 0);
        } else {
            itemMaterial = menuConfig.getSection().getString("report.item.normal", "DIAMOND");
            itemData = menuConfig.getSection().getInt("report.data.normal", 0);
        }
        return GUIUtils.createItem(itemMaterial, 1, itemData, name, lore);
    }

    public void openAdminGUI(Player player, ReportRecord record) {
        MenuConfig menuConfig = config.getMenuConfig("admin");
        if (menuConfig == null) return;
        plugin.getDatabaseManager().asyncRun(() -> {
        PlayerInfo reporterInfo = reportManager.getOrCreatePlayerInfo(
                java.util.UUID.fromString(record.getReporterUuid()), record.getReporterName());
        PlayerInfo reportedInfo = reportManager.getOrCreatePlayerInfo(
                java.util.UUID.fromString(record.getReportedUuid()), record.getReportedName());
        Inventory inv = buildAdminInv(menuConfig, record,
                reporterInfo != null ? String.valueOf(reporterInfo.getReportCount()) : "0",
                reporterInfo != null ? reporterInfo.getStatusText() : "离线",
                reportedInfo != null ? reportedInfo.getStatusText() : "离线");
        reportManager.setSelectedReport(player.getUniqueId(), record);
        openInvSync(player, inv);
        });
    }

    // 面板缓存命中时主线程直接开, 玩家信息异步查完再回填两个头颅
    public void openAdminGUINow(Player player, ReportRecord record) {
        MenuConfig menuConfig = config.getMenuConfig("admin");
        if (menuConfig == null) return;
        Inventory inv = buildAdminInv(menuConfig, record, "..", "查询中", "查询中");
        reportManager.setSelectedReport(player.getUniqueId(), record);
        player.openInventory(inv);
        plugin.getDatabaseManager().asyncRun(() -> {
            PlayerInfo reporterInfo = reportManager.getOrCreatePlayerInfo(
                    java.util.UUID.fromString(record.getReporterUuid()), record.getReporterName());
            PlayerInfo reportedInfo = reportManager.getOrCreatePlayerInfo(
                    java.util.UUID.fromString(record.getReportedUuid()), record.getReportedName());
            ItemStack reporterHead = buildReporterHead(menuConfig, record,
                    reporterInfo != null ? String.valueOf(reporterInfo.getReportCount()) : "0",
                    reporterInfo != null ? reporterInfo.getStatusText() : "离线");
            ItemStack reportedHead = buildReportedHead(menuConfig, record,
                    reportedInfo != null ? reportedInfo.getStatusText() : "离线");
            String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!player.getOpenInventory().getTitle().equals(title)) return; // 已切界面就不回填
                Inventory top = player.getOpenInventory().getTopInventory();
                int reporterSlot = getSlot(menuConfig, "report_player");
                if (reporterSlot >= 0) top.setItem(reporterSlot, reporterHead);
                int reportedSlot = getSlot(menuConfig, "reported_player");
                if (reportedSlot >= 0) top.setItem(reportedSlot, reportedHead);
            });
        });
    }

    private ItemStack buildReporterHead(MenuConfig menuConfig, ReportRecord record, String rCount, String rOnline) {
        List<String> reporterLore = new ArrayList<>(menuConfig.getLore("report_player"));
        for (int i = 0; i < reporterLore.size(); i++) {
            String line = reporterLore.get(i);
            line = line.replace("{player}", record.getReporterName())
                      .replace("{r_server}", record.getServer())
                      .replace("{r_reportCount}", rCount)
                      .replace("{r_online}", rOnline);
            reporterLore.set(i, line);
        }
        return GUIUtils.createPlayerHead(record.getReporterName(),
                menuConfig.getName("report_player").replace("{player}", record.getReporterName()),
                reporterLore);
    }

    private ItemStack buildReportedHead(MenuConfig menuConfig, ReportRecord record, String dOnline) {
        List<String> reportedLore = new ArrayList<>(menuConfig.getLore("reported_player"));
        for (int i = 0; i < reportedLore.size(); i++) {
            String line = reportedLore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", String.valueOf(record.getReportCount()))
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{online}", dOnline);
            reportedLore.set(i, line);
        }
        return GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("reported_player").replace("{reported}", record.getReportedName()),
                reportedLore);
    }

    private Inventory buildAdminInv(MenuConfig menuConfig, ReportRecord record, String rCount, String rOnline, String dOnline) {
        String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        List<String> playerLore = new ArrayList<>(menuConfig.getLore("player"));
        for (int i = 0; i < playerLore.size(); i++) {
            String line = playerLore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", record.getReporterName())
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", String.valueOf(record.getReportCount()))
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", record.getReasonName())
                      .replace("{reportTime}", record.getTimeWithSuffix())
                      .replace("{reportTimestamp}", String.valueOf(record.getReportTime()))
                      .replace("{reportTimeFormat}", record.getFormattedTime())
                      .replace("{reportType}", record.getReportType())
                      .replace("{reportChat}", record.getChatMessages() != null ? record.getChatMessages() : "");
            playerLore.set(i, line);
        }
        ItemStack playerHead = GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("player").replace("{reportID}", String.valueOf(record.getId())),
                playerLore);
        int playerSlot = getSlot(menuConfig, "player");
        if (playerSlot >= 0) inv.setItem(playerSlot, playerHead);

        ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) inv.setItem(closeSlot, closeItem);
        // 举报者
        ItemStack reporterHead = buildReporterHead(menuConfig, record, rCount, rOnline);
        int reporterSlot = getSlot(menuConfig, "report_player");
        if (reporterSlot >= 0) inv.setItem(reporterSlot, reporterHead);
        // 被举报者
        ItemStack reportedHead = buildReportedHead(menuConfig, record, dOnline);
        int reportedSlot = getSlot(menuConfig, "reported_player");
        if (reportedSlot >= 0) inv.setItem(reportedSlot, reportedHead);
        // 取消处理
        ItemStack exitItem = GUIUtils.createItem(menuConfig.getItem("exit"), 1,
                menuConfig.getData("exit"), menuConfig.getName("exit"), new ArrayList<>());
        int exitSlot = getSlot(menuConfig, "exit");
        if (exitSlot >= 0) inv.setItem(exitSlot, exitItem);

        // 虚假举报
        ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) inv.setItem(cancelSlot, cancelItem);

        List<ReasonConfig> reasons = config.getReasonList();
        List<Integer> slots = menuConfig.getSlots("reasons");
        for (int i = 0; i < slots.size() && i < reasons.size(); i++) {
            ReasonConfig reason = reasons.get(i);
            ItemStack item = GUIUtils.createItem(reason.getItem(), 1, reason.getData(),
                    GUIUtils.colorize("&5" + reason.getName()),
                    Arrays.asList(GUIUtils.colorize("&9选择处罚" + reason.getName())));
            inv.setItem(slots.get(i), item);
        }
        return inv;
    }

    public void openAdminConfirmGUI(Player player, ReportRecord record, String reasonId, String reasonName) {
        MenuConfig menuConfig = config.getMenuConfig("admin_confirm");
        if (menuConfig == null) {
            plugin.getLogger().severe("配置错误: 未找到admin_confirm菜单配置，请检查config.yml中的menu.admin_confirm节点");
            player.sendMessage(config.getMessage("gui.admin-confirm-not-found"));
            return;
        }
        String title = menuConfig.getTitle()
                .replace("{reported}", record.getReportedName())
                .replace("{reason}", reasonName);
        plugin.getDatabaseManager().asyncRun(() -> {
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        ItemStack playerHead = GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("player").replace("{reported}", record.getReportedName()),
                menuConfig.getLore("player"));
        int playerSlot = getSlot(menuConfig, "player");
        if (playerSlot >= 0) inv.setItem(playerSlot, playerHead);

        ItemStack confirmItem = GUIUtils.createItem(menuConfig.getItem("confirm"), 1,
                menuConfig.getData("confirm"), menuConfig.getName("confirm"), new ArrayList<>());
        int confirmSlot = getSlot(menuConfig, "confirm");
        if (confirmSlot >= 0) inv.setItem(confirmSlot, confirmItem);
        else player.sendMessage(config.getMessage("gui.confirm-slot-not-config"));

        ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) inv.setItem(cancelSlot, cancelItem);
        else player.sendMessage(config.getMessage("gui.cancel-slot-not-config"));

        ItemStack warningItem = GUIUtils.createItem(menuConfig.getItem("warning"), 1,
                menuConfig.getData("warning"), menuConfig.getName("warning"), menuConfig.getLore("warning"));
        int warningSlot = getSlot(menuConfig, "warning");
        if (warningSlot >= 0) inv.setItem(warningSlot, warningItem);
        openInvSync(player, inv);
        });
    }

    public void openAdminCancelsGUI(Player player, ReportRecord record, List<ReportRecord> otherReports) {
        MenuConfig menuConfig = config.getMenuConfig("admin_cancels");
        if (menuConfig == null) {
            player.sendMessage(config.getMessage("gui.admin-cancels-not-found"));
            return;
        }

        String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
        plugin.getDatabaseManager().asyncRun(() -> {
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        ItemStack playerHead = GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("player").replace("{reported}", record.getReportedName()),
                menuConfig.getLore("player"));
        int playerSlot = getSlot(menuConfig, "player");
        if (playerSlot >= 0) inv.setItem(playerSlot, playerHead);

        ItemStack confirmItem = GUIUtils.createItem(menuConfig.getItem("confirm"), 1,
                menuConfig.getData("confirm"), menuConfig.getName("confirm"), new ArrayList<>());
        int confirmSlot = getSlot(menuConfig, "confirm");
        if (confirmSlot >= 0) inv.setItem(confirmSlot, confirmItem);

        ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) inv.setItem(cancelSlot, cancelItem);

        ItemStack warningItem = GUIUtils.createItem(menuConfig.getItem("warning"), 1,
                menuConfig.getData("warning"), menuConfig.getName("warning"), menuConfig.getLore("warning"));
        int warningSlot = getSlot(menuConfig, "warning");
        if (warningSlot >= 0) inv.setItem(warningSlot, warningItem);
        openInvSync(player, inv);
        });
    }
    
    public void openUnbanPanelGUI(Player player, int page) {
        MenuConfig menuConfig = config.getMenuConfig("unbanpanel");
        if (menuConfig == null) {
            player.sendMessage(ChatColor.RED + "配置错误: unbanpanel 菜单未配置");
            return;
        }
        plugin.getDatabaseManager().asyncRun(() -> {
        String title = menuConfig.getTitle();
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        ItemStack mainItem = GUIUtils.createItem(menuConfig.getItem("main"), 1,
                menuConfig.getData("main"), menuConfig.getName("main"), menuConfig.getLore("main"));
        int mainSlot = getSlot(menuConfig, "main");
        if (mainSlot >= 0) inv.setItem(mainSlot, mainItem);

        int pageSize = menuConfig.getSlots("report").size();
        List<ReportRecord> reports = reportManager.getProcessedReports(page, pageSize);
        int totalReports = reportManager.getProcessedReportCount();
        int totalPages = (int) Math.ceil((double) totalReports / pageSize);
        if (totalPages == 0) totalPages = 1;
        List<Integer> slots = menuConfig.getSlots("report");
        for (int i = 0; i < reports.size() && i < slots.size(); i++) {
            ReportRecord record = reports.get(i);
            ItemStack item = createUnbanReportItem(record, menuConfig);
            inv.setItem(slots.get(i), item);
        }
        if (page > 1) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1,
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            int prevSlot = getSlot(menuConfig, "prev");
            if (prevSlot >= 0) inv.setItem(prevSlot, prevItem);
        }
        if (page < totalPages) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            int nextSlot = getSlot(menuConfig, "next");
            if (nextSlot >= 0) inv.setItem(nextSlot, nextItem);
        }
        reportManager.setStaffPage(player.getUniqueId(), page);
        openInvSync(player, inv);
        });
    }

    private ItemStack createUnbanReportItem(ReportRecord record, MenuConfig menuConfig) {
        List<String> lore = new ArrayList<>(menuConfig.getLore("report"));
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", record.getReporterName())
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", String.valueOf(record.getReportCount()))
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", record.getReasonName())
                      .replace("{reportTime}", record.getTimeWithSuffix())
                      .replace("{reportTimestamp}", String.valueOf(record.getReportTime()))
                      .replace("{reportTimeFormat}", record.getFormattedTime())
                      .replace("{reportType}", record.getReportType())
                      .replace("{reportChat}", record.getChatMessages() != null ? record.getChatMessages() : "")
                      .replace("{reportedReason}", record.getProcessedReasonName() != null ? record.getProcessedReasonName() : "未知")
                      .replace("{reportedType}", "封禁")
                      .replace("{reportedTime}", record.getFormattedProcessedTime())
                      .replace("{banId}", record.getBanId() != null ? record.getBanId() : "未知")
                      .replace("{operater}", record.getProcessedByName() != null ? record.getProcessedByName() : "未知");
            lore.set(i, line);
        }
        String name = menuConfig.getName("report").replace("{reportID}", String.valueOf(record.getId()));
        String itemMaterial = menuConfig.getSection().getString("report.item.banned", "INK_SACK");
        int itemData = menuConfig.getSection().getInt("report.data.banned", 1);
        return GUIUtils.createItem(itemMaterial, 1, itemData, name, lore);
    }
    
    public void openUnbanAdminGUI(Player player, ReportRecord record) {
        MenuConfig menuConfig = config.getMenuConfig("unbanadmin");
        if (menuConfig == null) {
            player.sendMessage(ChatColor.RED + "配置错误: unbanadmin 菜单未配置");
            return;
        }
        plugin.getDatabaseManager().asyncRun(() -> {
        String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        List<String> playerLore = new ArrayList<>(menuConfig.getLore("player"));
        for (int i = 0; i < playerLore.size(); i++) {
            String line = playerLore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", record.getReporterName())
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", String.valueOf(record.getReportCount()))
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", record.getReasonName())
                      .replace("{reportTime}", record.getTimeWithSuffix())
                      .replace("{reportTimestamp}", String.valueOf(record.getReportTime()))
                      .replace("{reportTimeFormat}", record.getFormattedTime())
                      .replace("{reportType}", record.getReportType())
                      .replace("{reportChat}", record.getChatMessages() != null ? record.getChatMessages() : "")
                      .replace("{reportedReason}", record.getProcessedReasonName() != null ? record.getProcessedReasonName() : "未知")
                      .replace("{reportedType}", "封禁")
                      .replace("{reportedTime}", record.getFormattedProcessedTime())
                      .replace("{banId}", record.getBanId() != null ? record.getBanId() : "未知")
                      .replace("{operater}", record.getProcessedByName() != null ? record.getProcessedByName() : "未知");
            playerLore.set(i, line);
        }
        ItemStack playerHead = GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("player").replace("{reportID}", String.valueOf(record.getId())),
                playerLore);
        int playerSlot = getSlot(menuConfig, "player");
        if (playerSlot >= 0) inv.setItem(playerSlot, playerHead);
        ItemStack changeReasonItem = GUIUtils.createItem(menuConfig.getItem("changeReason"), 1,
                menuConfig.getData("changeReason"), menuConfig.getName("changeReason"), 
                menuConfig.getLore("changeReason"));
        int changeReasonSlot = getSlot(menuConfig, "changeReason");
        if (changeReasonSlot >= 0) inv.setItem(changeReasonSlot, changeReasonItem);
        List<String> unbanLore = new ArrayList<>(menuConfig.getLore("unban"));
        for (int i = 0; i < unbanLore.size(); i++) {
            unbanLore.set(i, unbanLore.get(i).replace("{reported}", record.getReportedName()));
        }
        ItemStack unbanItem = GUIUtils.createItem(menuConfig.getItem("unban"), 1,
                menuConfig.getData("unban"), menuConfig.getName("unban"), unbanLore);
        int unbanSlot = getSlot(menuConfig, "unban");
        if (unbanSlot >= 0) inv.setItem(unbanSlot, unbanItem);
        ItemStack exitItem = GUIUtils.createItem(menuConfig.getItem("exit"), 1,
                menuConfig.getData("exit"), menuConfig.getName("exit"), menuConfig.getLore("exit"));
        int exitSlot = getSlot(menuConfig, "exit");
        if (exitSlot >= 0) inv.setItem(exitSlot, exitItem);
        ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) inv.setItem(closeSlot, closeItem);
        openInvSync(player, inv);
        });
    }
    
    public void openUnbanConfirmGUI(Player player, ReportRecord record) {
        MenuConfig menuConfig = config.getMenuConfig("unban_confirm");
        if (menuConfig == null) {
            player.sendMessage(ChatColor.RED + "配置错误: unban_confirm 菜单未配置");
            return;
        }
        plugin.getDatabaseManager().asyncRun(() -> {
        String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        ItemStack playerHead = GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("player").replace("{reported}", record.getReportedName()),
                menuConfig.getLore("player"));
        int playerSlot = getSlot(menuConfig, "player");
        if (playerSlot >= 0) inv.setItem(playerSlot, playerHead);
        ItemStack confirmItem = GUIUtils.createItem(menuConfig.getItem("confirm"), 1,
                menuConfig.getData("confirm"), menuConfig.getName("confirm"), new ArrayList<>());
        int confirmSlot = getSlot(menuConfig, "confirm");
        if (confirmSlot >= 0) inv.setItem(confirmSlot, confirmItem);
        ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) inv.setItem(cancelSlot, cancelItem);
        ItemStack warningItem = GUIUtils.createItem(menuConfig.getItem("warning"), 1,
                menuConfig.getData("warning"), menuConfig.getName("warning"), menuConfig.getLore("warning"));
        int warningSlot = getSlot(menuConfig, "warning");
        if (warningSlot >= 0) inv.setItem(warningSlot, warningItem);
        openInvSync(player, inv);
        });
    }
    
    public void openFullResetConfirmGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§c§l确认完全重置数据");
        ItemStack warningItem = GUIUtils.createItem("STAINED_GLASS_PANE", 1, 14, 
                "§c§lWARNING", 
                Arrays.asList(
                        "§c此操作将清空所有数据",
                        "§7- 所有举报记录",
                        "§7- 所有玩家数据",
                        "§7- 所有操作日志",
                        "",
                        "§c§l操作不可逆, 慎重考虑."
                ));
        inv.setItem(4, warningItem);
        inv.setItem(13, warningItem);
        inv.setItem(22, warningItem);
        ItemStack confirmItem = GUIUtils.createItem("STAINED_GLASS_PANE", 1, 13, 
                "§a§l确认重置", 
                Arrays.asList(
                        "§7点击确认清空所有数据",
                        "",
                        "§c操作不可逆. 思考不规范, 亲人两行泪."
                ));
        inv.setItem(11, confirmItem);
        inv.setItem(12, confirmItem);
        inv.setItem(20, confirmItem);
        
        ItemStack cancelItem = GUIUtils.createItem("STAINED_GLASS_PANE", 1, 14, 
                "§c§l取消", 
                Arrays.asList(
                        "§7点击取消操作"
                ));
        inv.setItem(14, cancelItem);
        inv.setItem(15, cancelItem);
        inv.setItem(24, cancelItem);
        
        ItemStack glass = GUIUtils.createItem("STAINED_GLASS_PANE", 1, 15, " ", new ArrayList<>());
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }
        player.openInventory(inv);
    }
}
