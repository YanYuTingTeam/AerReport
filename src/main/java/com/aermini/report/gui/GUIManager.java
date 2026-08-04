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
    private final java.util.concurrent.ConcurrentHashMap<String, String> playerServerCache = new java.util.concurrent.ConcurrentHashMap<>();

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

    public String getCachedPlayerServer(String uuid) {
        return playerServerCache.get(uuid);
    }

    public void putCachedPlayerServer(String uuid, String server) {
        if (server != null) playerServerCache.put(uuid, server);
    }

    private String buildOnlineStatus(String name, String currentServer) {
        org.bukkit.entity.Player p = Bukkit.getPlayerExact(name);
        if (p != null && p.isOnline()) return "在线(本服)";
        if (currentServer != null) return "在线(" + currentServer + ")";
        return "离线";
    }

    private void openInvSync(Player player, Inventory inv) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.openInventory(inv);
        });
    }
    
    private int getSlot(MenuConfig menuConfig, String itemName) {
        List<Integer> slots = menuConfig.getSlots(itemName);
        if (slots == null || slots.isEmpty()) return -1;
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
        int prevSlot = getSlot(menuConfig, "prev");
        if (prevSlot >= 0) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1,
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            inv.setItem(prevSlot, prevItem);
        }
        int nextSlot = getSlot(menuConfig, "next");
        if (nextSlot >= 0) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            inv.setItem(nextSlot, nextItem);
        }
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) {
            ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                    menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
            inv.setItem(closeSlot, closeItem);
        }
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
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) {
            ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                    menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
            inv.setItem(closeSlot, closeItem);
        }
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
        int prevSlot = getSlot(menuConfig, "prev");
        if (prevSlot >= 0) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1,
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            inv.setItem(prevSlot, prevItem);
        }
        int nextSlot = getSlot(menuConfig, "next");
        if (nextSlot >= 0) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            inv.setItem(nextSlot, nextItem);
        }
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) {
            ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                    menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
            inv.setItem(closeSlot, closeItem);
        }

        reportManager.setPlayerPage(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    public void openPanelGUI(Player player, int page) {
        openPanelGUI(player, page, reportManager.getStaffReportType(player.getUniqueId()));
    }

    public void openPanelGUI(Player player, int page, String reportType) {
        MenuConfig menuConfig = config.getMenuConfig("panel");
        if (menuConfig == null) return;
        if (reportType != null) reportManager.setStaffReportType(player.getUniqueId(), reportType);
        final String type = reportType != null ? reportType : reportManager.getStaffReportType(player.getUniqueId());
        plugin.getDatabaseManager().asyncRun(() -> {
        String title = menuConfig.getTitle();
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        int mainSlot = getSlot(menuConfig, "main");
        if (mainSlot >= 0) {
            ItemStack mainItem = GUIUtils.createItem(menuConfig.getItem("main"), 1,
                    menuConfig.getData("main"), menuConfig.getName("main"), menuConfig.getLore("main"));
            inv.setItem(mainSlot, mainItem);
        }

        // behaviour/chat
        int behaviourSlot = getSlot(menuConfig, "behaviour");
        if (behaviourSlot >= 0) {
            ItemStack behaviourItem = GUIUtils.createItem(menuConfig.getItem("behaviour"), 1,
                    menuConfig.getData("behaviour"), menuConfig.getName("behaviour"), menuConfig.getLore("behaviour"));
            inv.setItem(behaviourSlot, behaviourItem);
        }
        int chatSlot = getSlot(menuConfig, "chat");
        if (chatSlot >= 0) {
            ItemStack chatItem = GUIUtils.createItem(menuConfig.getItem("chat"), 1,
                    menuConfig.getData("chat"), menuConfig.getName("chat"), menuConfig.getLore("chat"));
            inv.setItem(chatSlot, chatItem);
        }

        int pageSize = menuConfig.getSlots("report").size();
        List<ReportRecord> reports = reportManager.getPendingReportsMerged(page, pageSize, type);
        int totalReports = reportManager.getPendingMergedCount(type);
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
        int prevSlot = getSlot(menuConfig, "prev");
        if (prevSlot >= 0) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1,
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            inv.setItem(prevSlot, prevItem);
        }
        int nextSlot = getSlot(menuConfig, "next");
        if (nextSlot >= 0) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            inv.setItem(nextSlot, nextItem);
        }
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) {
            ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                    menuConfig.getData("close"), menuConfig.getName("close"),
                    menuConfig.getLore("close") != null && !menuConfig.getLore("close").isEmpty()
                            ? menuConfig.getLore("close") : new ArrayList<>());
            inv.setItem(closeSlot, closeItem);
        }
        int backindexSlot = getSlot(menuConfig, "backindex");
        if (backindexSlot >= 0) {
            ItemStack backindexItem = GUIUtils.createItem(menuConfig.getItem("backindex"), 1,
                    menuConfig.getData("backindex"), menuConfig.getName("backindex"), new ArrayList<>());
            inv.setItem(backindexSlot, backindexItem);
        }
        reportManager.setStaffPage(player.getUniqueId(), page);
        openInvSync(player, inv);

        final java.util.UUID pUuid = player.getUniqueId();
        final boolean patrol = plugin.getVolunteerManager().syncPatrolFromDb(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(pUuid);
            if (p == null || !p.isOnline()) return;
            String openTitle = p.getOpenInventory().getTitle();
            if (openTitle == null) return;
            if (!menuConfig.getTitle().equals(openTitle)) return;
            org.bukkit.inventory.Inventory topInv = p.getOpenInventory().getTopInventory();
            if (topInv == null) return;
            String volunItem = patrol ? "volun_off" : "volun_on";
            int volunSlot = getSlot(menuConfig, volunItem);
            if (volunSlot >= 0) {
                ItemStack volunStack = GUIUtils.createItem(menuConfig.getItem(volunItem), 1,
                        menuConfig.getData(volunItem), menuConfig.getName(volunItem),
                        menuConfig.getLore(volunItem) != null && !menuConfig.getLore(volunItem).isEmpty()
                                ? menuConfig.getLore(volunItem) : new ArrayList<>());
                topInv.setItem(volunSlot, volunStack);
            }
        });
        });
    }

    private ItemStack createReportItem(ReportRecord record, MenuConfig menuConfig, Player viewer) {
        List<String> lore = new ArrayList<>(menuConfig.getLore("report"));
        String players = record.getMergedReporterNames() != null ? record.getMergedReporterNames() : record.getReporterName();
        String reasons = record.getMergedReasonNames() != null ? record.getMergedReasonNames() : record.getReasonName();
        String count = record.getMergedCount() > 0 ? String.valueOf(record.getMergedCount()) : String.valueOf(record.getReportCount());
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", players)
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", count)
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", reasons)
                      .replace("{reportTime}", record.getTimeWithSuffix())
                      .replace("{reportTimestamp}", String.valueOf(record.getReportTime()))
                      .replace("{reportTimeFormat}", record.getFormattedTime())
                      .replace("{reportType}", record.getReportType())
                      .replace("{reportChat}", record.getChatMessages() != null ? record.getChatMessages() : "");
            lore.set(i, line);
        }
        String name = menuConfig.getName("report").replace("{reportID}", String.valueOf(record.getId()));
        String itemMaterial = menuConfig.getSection().getString("report.item.normal", "DIAMOND");
        int itemData = menuConfig.getSection().getInt("report.data.normal", 0);
        return GUIUtils.createItem(itemMaterial, 1, itemData, name, lore);
    }

    public void openAdminGUI(Player player, ReportRecord record) {
        MenuConfig menuConfig = config.getMenuConfig("admin");
        if (menuConfig == null) return;
        plugin.getDatabaseManager().asyncRun(() -> {
            reportManager.fillMergedInfo(record);
            java.util.concurrent.CompletableFuture<String> reporterFuture = new java.util.concurrent.CompletableFuture<>();
            java.util.concurrent.CompletableFuture<String> reportedFuture = new java.util.concurrent.CompletableFuture<>();
            plugin.getPlayerDataManager().getPlayerServer(java.util.UUID.fromString(record.getReporterUuid()), reporterFuture::complete);
            plugin.getPlayerDataManager().getPlayerServer(java.util.UUID.fromString(record.getReportedUuid()), reportedFuture::complete);
            String reporterServer = null, reportedServer = null;
            try { reporterServer = reporterFuture.get(3, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignore) {}
            try { reportedServer = reportedFuture.get(3, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignore) {}
            putCachedPlayerServer(record.getReporterUuid(), reporterServer);
            putCachedPlayerServer(record.getReportedUuid(), reportedServer);
            PlayerInfo reporterInfo = reportManager.getOrCreatePlayerInfo(
                    java.util.UUID.fromString(record.getReporterUuid()), record.getReporterName());
            PlayerInfo reportedInfo = reportManager.getOrCreatePlayerInfo(
                    java.util.UUID.fromString(record.getReportedUuid()), record.getReportedName());
            Inventory inv = buildAdminInv(menuConfig, record,
                    reporterInfo != null ? String.valueOf(reporterInfo.getReportCount()) : "0",
                    buildOnlineStatus(record.getReporterName(), reporterServer),
                    buildOnlineStatus(record.getReportedName(), reportedServer),
                    reporterServer != null ? reporterServer : "未知",
                    reportedServer != null ? reportedServer : "未知");
            reportManager.setSelectedReport(player.getUniqueId(), record);
            openInvSync(player, inv);
        });
    }

    // 面板缓存命中时主线程直接开, 玩家信息异步查完再回填两个头颅
    public void openAdminGUINow(Player player, ReportRecord record) {
        MenuConfig menuConfig = config.getMenuConfig("admin");
        if (menuConfig == null) return;
        Inventory inv = buildAdminInv(menuConfig, record, "..", "查询中", "查询中", "查询中", "查询中");
        reportManager.setSelectedReport(player.getUniqueId(), record);
        player.openInventory(inv);
        plugin.getDatabaseManager().asyncRun(() -> {
            // 补充合并信息后回填 player 头颅(显示合并组举报人/理由/数量)
            reportManager.fillMergedInfo(record);
            java.util.concurrent.CompletableFuture<String> reporterFuture = new java.util.concurrent.CompletableFuture<>();
            java.util.concurrent.CompletableFuture<String> reportedFuture = new java.util.concurrent.CompletableFuture<>();
            plugin.getPlayerDataManager().getPlayerServer(java.util.UUID.fromString(record.getReporterUuid()), reporterFuture::complete);
            plugin.getPlayerDataManager().getPlayerServer(java.util.UUID.fromString(record.getReportedUuid()), reportedFuture::complete);
            String reporterServer = null, reportedServer = null;
            try { reporterServer = reporterFuture.get(3, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignore) {}
            try { reportedServer = reportedFuture.get(3, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignore) {}
            putCachedPlayerServer(record.getReporterUuid(), reporterServer);
            putCachedPlayerServer(record.getReportedUuid(), reportedServer);
            PlayerInfo reporterInfo = reportManager.getOrCreatePlayerInfo(
                    java.util.UUID.fromString(record.getReporterUuid()), record.getReporterName());
            PlayerInfo reportedInfo = reportManager.getOrCreatePlayerInfo(
                    java.util.UUID.fromString(record.getReportedUuid()), record.getReportedName());
            ItemStack reporterHead = buildReporterHead(menuConfig, record,
                    reporterInfo != null ? String.valueOf(reporterInfo.getReportCount()) : "0",
                    buildOnlineStatus(record.getReporterName(), reporterServer),
                    reporterServer != null ? reporterServer : "未知");
            ItemStack reportedHead = buildReportedHead(menuConfig, record,
                    buildOnlineStatus(record.getReportedName(), reportedServer),
                    reportedServer != null ? reportedServer : "未知");
            // 重新构造 player 头颅(用合并字段)
            ItemStack playerHead = buildAdminPlayerHead(menuConfig, record);
            String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!player.getOpenInventory().getTitle().equals(title)) return; // 已切界面就不回填
                Inventory top = player.getOpenInventory().getTopInventory();
                int playerSlot = getSlot(menuConfig, "player");
                if (playerSlot >= 0) top.setItem(playerSlot, playerHead);
                int reporterSlot = getSlot(menuConfig, "report_player");
                if (reporterSlot >= 0) top.setItem(reporterSlot, reporterHead);
                int reportedSlot = getSlot(menuConfig, "reported_player");
                if (reportedSlot >= 0) top.setItem(reportedSlot, reportedHead);
            });
        });
    }

    // 用合并字段构造 admin GUI 中间的 player 头颅
    private ItemStack buildAdminPlayerHead(MenuConfig menuConfig, ReportRecord record) {
        String players = record.getMergedReporterNames() != null ? record.getMergedReporterNames() : record.getReporterName();
        String reasons = record.getMergedReasonNames() != null ? record.getMergedReasonNames() : record.getReasonName();
        String count = record.getMergedCount() > 0 ? String.valueOf(record.getMergedCount()) : String.valueOf(record.getReportCount());
        List<String> playerLore = new ArrayList<>(menuConfig.getLore("player"));
        for (int i = 0; i < playerLore.size(); i++) {
            String line = playerLore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", players)
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", count)
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", reasons)
                      .replace("{reportTime}", record.getTimeWithSuffix())
                      .replace("{reportTimestamp}", String.valueOf(record.getReportTime()))
                      .replace("{reportTimeFormat}", record.getFormattedTime())
                      .replace("{reportType}", record.getReportType())
                      .replace("{reportChat}", record.getChatMessages() != null ? record.getChatMessages() : "");
            playerLore.set(i, line);
        }
        return GUIUtils.createPlayerHead(record.getReportedName(),
                menuConfig.getName("player").replace("{reportID}", String.valueOf(record.getId())),
                playerLore);
    }

    private ItemStack buildReporterHead(MenuConfig menuConfig, ReportRecord record, String rCount, String rOnline, String rCurrentServer) {
        List<String> reporterLore = new ArrayList<>(menuConfig.getLore("report_player"));
        for (int i = 0; i < reporterLore.size(); i++) {
            String line = reporterLore.get(i);
            line = line.replace("{player}", record.getReporterName())
                      .replace("{r_server}", record.getServer())
                      .replace("{r_current_server}", rCurrentServer)
                      .replace("{r_reportCount}", rCount)
                      .replace("{r_online}", rOnline);
            reporterLore.set(i, line);
        }
        return GUIUtils.createPlayerHead(record.getReporterName(),
                menuConfig.getName("report_player").replace("{player}", record.getReporterName()),
                reporterLore);
    }

    private ItemStack buildReportedHead(MenuConfig menuConfig, ReportRecord record, String dOnline, String currentServer) {
        List<String> reportedLore = new ArrayList<>(menuConfig.getLore("reported_player"));
        for (int i = 0; i < reportedLore.size(); i++) {
            String line = reportedLore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{current_server}", currentServer)
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

    private Inventory buildAdminInv(MenuConfig menuConfig, ReportRecord record, String rCount, String rOnline, String dOnline, String rCurrentServer, String currentServer) {
        String title = menuConfig.getTitle().replace("{reported}", record.getReportedName());
        Inventory inv = Bukkit.createInventory(null, menuConfig.getSlot(), title);
        // 合并组字段优先, 单条回退
        String players = record.getMergedReporterNames() != null ? record.getMergedReporterNames() : record.getReporterName();
        String reasons = record.getMergedReasonNames() != null ? record.getMergedReasonNames() : record.getReasonName();
        String count = record.getMergedCount() > 0 ? String.valueOf(record.getMergedCount()) : String.valueOf(record.getReportCount());
        List<String> playerLore = new ArrayList<>(menuConfig.getLore("player"));
        for (int i = 0; i < playerLore.size(); i++) {
            String line = playerLore.get(i);
            line = line.replace("{server}", record.getServer())
                      .replace("{player}", players)
                      .replace("{reported}", record.getReportedName())
                      .replace("{reportCount}", count)
                      .replace("{reportCounts}", String.valueOf(record.getReportCounts()))
                      .replace("{reason}", reasons)
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

        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) {
            ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                    menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
            inv.setItem(closeSlot, closeItem);
        }
        ItemStack reporterHead = buildReporterHead(menuConfig, record, rCount, rOnline, rCurrentServer);
        int reporterSlot = getSlot(menuConfig, "report_player");
        if (reporterSlot >= 0) inv.setItem(reporterSlot, reporterHead);
        ItemStack reportedHead = buildReportedHead(menuConfig, record, dOnline, currentServer);
        int reportedSlot = getSlot(menuConfig, "reported_player");
        if (reportedSlot >= 0) inv.setItem(reportedSlot, reportedHead);
        int exitSlot = getSlot(menuConfig, "exit");
        if (exitSlot >= 0) {
            ItemStack exitItem = GUIUtils.createItem(menuConfig.getItem("exit"), 1,
                    menuConfig.getData("exit"), menuConfig.getName("exit"), new ArrayList<>());
            inv.setItem(exitSlot, exitItem);
        }
        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) {
            ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                    menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
            inv.setItem(cancelSlot, cancelItem);
        }

        // teleport/ban365/ban3650 新物品, 配置存在则创建
        int teleportSlot = getSlot(menuConfig, "teleport");
        if (teleportSlot >= 0) {
            ItemStack teleportItem = GUIUtils.createItem(menuConfig.getItem("teleport"), 1,
                    menuConfig.getData("teleport"), menuConfig.getName("teleport"),
                    menuConfig.getLore("teleport") != null && !menuConfig.getLore("teleport").isEmpty() ? menuConfig.getLore("teleport") : new ArrayList<>());
            inv.setItem(teleportSlot, teleportItem);
        }
        int ban365Slot = getSlot(menuConfig, "ban365");
        if (ban365Slot >= 0) {
            ItemStack ban365Item = GUIUtils.createItem(menuConfig.getItem("ban365"), 1,
                    menuConfig.getData("ban365"), menuConfig.getName("ban365"),
                    menuConfig.getLore("ban365") != null && !menuConfig.getLore("ban365").isEmpty() ? menuConfig.getLore("ban365") : new ArrayList<>());
            inv.setItem(ban365Slot, ban365Item);
        }
        int ban3650Slot = getSlot(menuConfig, "ban3650");
        if (ban3650Slot >= 0) {
            ItemStack ban3650Item = GUIUtils.createItem(menuConfig.getItem("ban3650"), 1,
                    menuConfig.getData("ban3650"), menuConfig.getName("ban3650"),
                    menuConfig.getLore("ban3650") != null && !menuConfig.getLore("ban3650").isEmpty() ? menuConfig.getLore("ban3650") : new ArrayList<>());
            inv.setItem(ban3650Slot, ban3650Item);
        }

        List<ReasonConfig> reasonConfigs = config.getReasonList();
        List<Integer> slots = menuConfig.getSlots("reasons");
        for (int i = 0; slots != null && i < slots.size() && i < reasonConfigs.size(); i++) {
            ReasonConfig reason = reasonConfigs.get(i);
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

        int confirmSlot = getSlot(menuConfig, "confirm");
        if (confirmSlot >= 0) {
            ItemStack confirmItem = GUIUtils.createItem(menuConfig.getItem("confirm"), 1,
                    menuConfig.getData("confirm"), menuConfig.getName("confirm"), new ArrayList<>());
            inv.setItem(confirmSlot, confirmItem);
        } else player.sendMessage(config.getMessage("gui.confirm-slot-not-config"));

        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) {
            ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                    menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
            inv.setItem(cancelSlot, cancelItem);
        } else player.sendMessage(config.getMessage("gui.cancel-slot-not-config"));

        int warningSlot = getSlot(menuConfig, "warning");
        if (warningSlot >= 0) {
            ItemStack warningItem = GUIUtils.createItem(menuConfig.getItem("warning"), 1,
                    menuConfig.getData("warning"), menuConfig.getName("warning"), menuConfig.getLore("warning"));
            inv.setItem(warningSlot, warningItem);
        }
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
        int mainSlot = getSlot(menuConfig, "main");
        if (mainSlot >= 0) {
            ItemStack mainItem = GUIUtils.createItem(menuConfig.getItem("main"), 1,
                    menuConfig.getData("main"), menuConfig.getName("main"), menuConfig.getLore("main"));
            inv.setItem(mainSlot, mainItem);
        }

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
        int prevSlot = getSlot(menuConfig, "prev");
        if (prevSlot >= 0) {
            ItemStack prevItem = GUIUtils.createItem(menuConfig.getItem("prev"), 1,
                    menuConfig.getData("prev"), menuConfig.getName("prev"), new ArrayList<>());
            inv.setItem(prevSlot, prevItem);
        }
        int nextSlot = getSlot(menuConfig, "next");
        if (nextSlot >= 0) {
            ItemStack nextItem = GUIUtils.createItem(menuConfig.getItem("next"), 1,
                    menuConfig.getData("next"), menuConfig.getName("next"), new ArrayList<>());
            inv.setItem(nextSlot, nextItem);
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
        int changeReasonSlot = getSlot(menuConfig, "changeReason");
        if (changeReasonSlot >= 0) {
            ItemStack changeReasonItem = GUIUtils.createItem(menuConfig.getItem("changeReason"), 1,
                    menuConfig.getData("changeReason"), menuConfig.getName("changeReason"),
                    menuConfig.getLore("changeReason"));
            inv.setItem(changeReasonSlot, changeReasonItem);
        }
        List<String> unbanLore = new ArrayList<>(menuConfig.getLore("unban"));
        for (int i = 0; i < unbanLore.size(); i++) {
            unbanLore.set(i, unbanLore.get(i).replace("{reported}", record.getReportedName()));
        }
        int unbanSlot = getSlot(menuConfig, "unban");
        if (unbanSlot >= 0) {
            ItemStack unbanItem = GUIUtils.createItem(menuConfig.getItem("unban"), 1,
                    menuConfig.getData("unban"), menuConfig.getName("unban"), unbanLore);
            inv.setItem(unbanSlot, unbanItem);
        }
        int exitSlot = getSlot(menuConfig, "exit");
        if (exitSlot >= 0) {
            ItemStack exitItem = GUIUtils.createItem(menuConfig.getItem("exit"), 1,
                    menuConfig.getData("exit"), menuConfig.getName("exit"), menuConfig.getLore("exit"));
            inv.setItem(exitSlot, exitItem);
        }
        int closeSlot = getSlot(menuConfig, "close");
        if (closeSlot >= 0) {
            ItemStack closeItem = GUIUtils.createItem(menuConfig.getItem("close"), 1,
                    menuConfig.getData("close"), menuConfig.getName("close"), new ArrayList<>());
            inv.setItem(closeSlot, closeItem);
        }
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
        int confirmSlot = getSlot(menuConfig, "confirm");
        if (confirmSlot >= 0) {
            ItemStack confirmItem = GUIUtils.createItem(menuConfig.getItem("confirm"), 1,
                    menuConfig.getData("confirm"), menuConfig.getName("confirm"), new ArrayList<>());
            inv.setItem(confirmSlot, confirmItem);
        }
        int cancelSlot = getSlot(menuConfig, "cancel");
        if (cancelSlot >= 0) {
            ItemStack cancelItem = GUIUtils.createItem(menuConfig.getItem("cancel"), 1,
                    menuConfig.getData("cancel"), menuConfig.getName("cancel"), new ArrayList<>());
            inv.setItem(cancelSlot, cancelItem);
        }
        int warningSlot = getSlot(menuConfig, "warning");
        if (warningSlot >= 0) {
            ItemStack warningItem = GUIUtils.createItem(menuConfig.getItem("warning"), 1,
                    menuConfig.getData("warning"), menuConfig.getName("warning"), menuConfig.getLore("warning"));
            inv.setItem(warningSlot, warningItem);
        }
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
