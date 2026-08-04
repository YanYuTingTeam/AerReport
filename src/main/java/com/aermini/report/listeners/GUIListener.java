package com.aermini.report.listeners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import com.aermini.report.AerReport;
import com.aermini.report.gui.GUIManager;
import com.aermini.report.managers.BungeeManager;
import com.aermini.report.managers.ConfigManager;
import com.aermini.report.managers.ConfigManager.MenuConfig;
import com.aermini.report.managers.ConfigManager.ReasonConfig;
import com.aermini.report.managers.ReportManager;
import com.aermini.report.models.ReportRecord;
import com.aermini.report.utils.GUIUtils;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class GUIListener implements Listener {
    private final AerReport plugin;
    private final GUIManager guiManager;
    private final ReportManager reportManager;
    private final ConfigManager configManager;
    private final java.util.concurrent.ConcurrentHashMap<UUID, String> selectedReasons = new java.util.concurrent.ConcurrentHashMap<>();

    public GUIListener(AerReport plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.reportManager = plugin.getReportManager();
        this.configManager = plugin.getConfigManager();
        plugin.getBungeeManager().registerMessageHandler((msgType, args) -> {
            if (msgType.equals(BungeeManager.MSG_REPORT_ALERT) && args.length >= 5) {
                // reportedName, reason, server, reportId, notifyTime
                final long notifyTime = Long.parseLong(args[4]);
                final int reportId = Integer.parseInt(args[3]);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    notifyLocalStaff(args[0], args[1], args[2], notifyTime, reportId);
                });
            }
        });
    }

    private boolean isAerReportGUI(String title) {
        if (title == null) return false;
        MenuConfig reportsConfig = configManager.getMenuConfig("reports");
        MenuConfig reportConfig = configManager.getMenuConfig("report");
        MenuConfig panelConfig = configManager.getMenuConfig("panel");
        if (reportsConfig != null && title.equals(reportsConfig.getTitle())) return true;
        if (reportConfig != null && title.equals(reportConfig.getTitle())) return true;
        if (panelConfig != null && title.equals(panelConfig.getTitle())) return true;
        if (title.equals("选择玩家处罚")) return true;
        if (title.startsWith("处罚玩家 ")) return true;
        if (title.startsWith("是否确定 处罚 ")) return true;
        MenuConfig unbanPanelConfig = configManager.getMenuConfig("unbanpanel");
        if (unbanPanelConfig != null && title.equals(unbanPanelConfig.getTitle())) return true;
        if (title.startsWith("处理被封禁玩家 ")) return true;
        if (title.startsWith("是否确定 解封 ")) return true;
        if (title.equals("§c§l确认完全重置数据")) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        String title = event.getView().getTitle();
        if (isAerReportGUI(title)) event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        if (!isAerReportGUI(title)) return;
        event.setCancelled(true); // 先取消, 空格子/双击收集/放入物品也不放行
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (title.equals(configManager.getMenuConfig("reports").getTitle())) {
            handleReportsClick(player, item, event.getClick(), event.getRawSlot());
            return;
        }
        if (title.equals(configManager.getMenuConfig("report").getTitle())) {
            handleReportReasonClick(player, item, event.getRawSlot());
            return;
        }
        if (title.equals("选择玩家处罚")) {
            handlePunishSelectClick(player, item, event.getRawSlot());
            return;
        }
        if (title.equals(configManager.getMenuConfig("panel").getTitle())) {
            handlePanelClick(player, item, event.getClick(), event.getRawSlot());
            return;
        }
        if (title.startsWith("处罚玩家 ")) {
            handleAdminClick(player, item, event.getClick(), event.getRawSlot());
            return;
        }
        if (title.startsWith("是否确定 处罚 ")) {
            handleAdminConfirmClick(player, item, event.getRawSlot());
            return;
        }
        MenuConfig unbanPanelConfig = configManager.getMenuConfig("unbanpanel");
        if (unbanPanelConfig != null && title.equals(unbanPanelConfig.getTitle())) {
            handleUnbanPanelClick(player, item, event.getRawSlot());
            return;
        }
        if (title.startsWith("处理被封禁玩家 ")) {
            handleUnbanAdminClick(player, item, event.getRawSlot());
            return;
        }
        if (title.startsWith("是否确定 解封 ")) {
            handleUnbanConfirmClick(player, item, event.getRawSlot());
            return;
        }
        if (title.equals("§c§l确认完全重置数据")) {
            handleFullResetConfirmClick(player, item, event.getRawSlot());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (isAerReportGUI(event.getView().getTitle())) event.setCancelled(true);
    }

    private void handleReportsClick(Player player, ItemStack item, ClickType clickType, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("reports");
        if (isClickInSlot(rawSlot, menuConfig, "close")) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "prev")) {
            int page = reportManager.getPlayerPage(player.getUniqueId());
            if (page > 1) guiManager.openReportsGUI(player, page - 1);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "next")) {
            int page = reportManager.getPlayerPage(player.getUniqueId());
            guiManager.openReportsGUI(player, page + 1);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "player") && item.getType() == Material.SKULL_ITEM) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null && meta.getDisplayName() != null) {
                String displayName = ChatColor.stripColor(meta.getDisplayName());
                if (displayName != null && !displayName.isEmpty()) {
                    guiManager.openReportReasonGUI(player, displayName);
                }
            }
        }
    }

    private void handleReportReasonClick(Player player, ItemStack item, int slot) {
        MenuConfig menuConfig = configManager.getMenuConfig("report");
        if (isInSlot(item, menuConfig, "close")) {
            player.closeInventory();
            return;
        }
        List<Integer> reasonSlots = menuConfig.getSlots("reasons");
        if (!reasonSlots.contains(slot)) return;
        String reasonName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        ReasonConfig reason = findReasonByName(reasonName);
        if (reason != null) {
            String reportedName = guiManager.getSelectedPlayer(player);
            player.closeInventory();
            if (reportedName == null) return;
            Player reported = Bukkit.getPlayerExact(reportedName);
            if (reported == null) return;
            UUID reportedId = reported.getUniqueId();
            plugin.getDatabaseManager().asyncRun(() -> {
                if (!reportManager.canReport(player.getUniqueId().toString(), reportedId.toString())) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", reportedName);
                    player.sendMessage(configManager.getMessage("max-repeat", placeholders));
                    return;
                }
                String chatMessages = "";
                if (reason.getType().equals("chat")) chatMessages=plugin.getChatManager().getRecentMessages(reportedId);
                int reportId = reportManager.createReport(player, 
                        reportedId.toString(), 
                        reportedName, 
                        reason.getId(), 
                        reason.getName(), 
                        reason.getType(), 
                        chatMessages);
                if (reportId > 0) {
                    reportManager.setCooldown(player.getUniqueId());
                    reportManager.incrementReportCount(player.getUniqueId(), player.getName());
                    reportManager.incrementReportedCount(reportedId, reportedName);
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", reportedName);
                    placeholders.put("reportID", String.valueOf(reportId));
                    player.sendMessage(configManager.getMessage("report", placeholders));
                    Bukkit.getScheduler().runTask(plugin, () -> 
                            notifyStaff(reportedName, reason.getName(), plugin.getServerName(), reportId));
                }
            });
        }
    }

    private void handlePunishSelectClick(Player player, ItemStack item, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("reports");
        if (isClickInSlot(rawSlot, menuConfig, "close")) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "prev")) {
            int page = reportManager.getPlayerPage(player.getUniqueId());
            if (page > 1) {
                guiManager.openPunishSelectGUI(player, page - 1);
            }
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "next")) {
            int page = reportManager.getPlayerPage(player.getUniqueId());
            guiManager.openPunishSelectGUI(player, page + 1);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "player") && item.getType() == Material.SKULL_ITEM) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null && meta.getDisplayName() != null) {
                String displayName = ChatColor.stripColor(meta.getDisplayName());
                if (displayName != null && !displayName.isEmpty()) {
                    Player target = Bukkit.getPlayerExact(displayName);
                    if (target == null) {
                        player.sendMessage(configManager.getMessage("player-not-found"));
                        return;
                    }
                    UUID targetId = target.getUniqueId();
                    String targetName = target.getName();
                    if (!guiManager.startOpening(player.getUniqueId())) return;
                    plugin.getDatabaseManager().asyncRun(() -> {
                        try {
                            ReportRecord existingReport = reportManager.getPendingReportByReported(targetId.toString());
                            if (existingReport != null) {
                                guiManager.openAdminGUI(player, existingReport);
                                return;
                            }
                            String chatMessages = plugin.getChatManager().getRecentMessages(targetId);
                            int reportId = reportManager.createReport(player, 
                                    targetId.toString(), 
                                    targetName, 
                                    "volunteer", 
                                    "志愿者直接处罚", 
                                    "other", 
                                    chatMessages);
                            if (reportId > 0) {
                                ReportRecord record = reportManager.getReport(reportId);
                                if (record != null) guiManager.openAdminGUI(player, record);
                            }
                        } finally {
                            guiManager.finishOpening(player.getUniqueId());
                        }
                    });
                }
            }
        }
    }

    private void handlePanelClick(Player player, ItemStack item, ClickType clickType, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("panel");
        if (isClickInSlot(rawSlot, menuConfig, "close")) {
            player.closeInventory();
            return;
        }
        boolean patrolling = plugin.getVolunteerManager().isPatrolling(player.getUniqueId());
        if (patrolling) {
            if (isClickInSlot(rawSlot, menuConfig, "volun_off")) {
                plugin.getVolunteerManager().exitPatrol(player);
                guiManager.openPanelGUI(player, 1);
                return;
            }
        } else {
            if (isClickInSlot(rawSlot, menuConfig, "volun_on")) {
                plugin.getVolunteerManager().enterPatrol(player);
                guiManager.openPanelGUI(player, 1);
                return;
            }
        }
        if (isClickInSlot(rawSlot, menuConfig, "behaviour")) {
            guiManager.openPanelGUI(player, 1, "behaviour");
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "chat")) {
            guiManager.openPanelGUI(player, 1, "chat");
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "backindex")) {
            guiManager.openPanelGUI(player, 1);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "prev")) {
            int page = reportManager.getStaffPage(player.getUniqueId());
            if (page > 1) guiManager.openPanelGUI(player, page - 1);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "next")) {
            int page = reportManager.getStaffPage(player.getUniqueId());
            guiManager.openPanelGUI(player, page + 1);
            return;
        }
        if (isInSlot(item, menuConfig, "report")) {
            String displayName = item.getItemMeta().getDisplayName();
            String idStr = ChatColor.stripColor(displayName).replaceAll("[^0-9]", "");
            try {
                int reportId = Integer.parseInt(idStr);
                ReportRecord cached = guiManager.getCachedPanelReport(player.getUniqueId(), reportId);
                if (cached != null) {
                    guiManager.openAdminGUINow(player, cached);
                    return;
                }
                if (!guiManager.startOpening(player.getUniqueId())) return;
                plugin.getDatabaseManager().asyncRun(() -> {
                    try {
                        ReportRecord record = reportManager.getReport(reportId);
                        if (record == null) return;
                        Bukkit.getScheduler().runTask(plugin, () -> guiManager.openAdminGUINow(player, record));
                    } finally {
                        guiManager.finishOpening(player.getUniqueId());
                    }
                });
            } catch (NumberFormatException e) {
                player.sendMessage(configManager.getMessage("gui.parse-id-fail"));
            }
        }
    }

    private void handleAdminClick(Player player, ItemStack item, ClickType clickType, int slot) {
        MenuConfig menuConfig = configManager.getMenuConfig("admin");
        ReportRecord record = reportManager.getSelectedReport(player.getUniqueId());
        if (record == null) {
            player.closeInventory();
            return;
        }
        if (isInSlot(item, menuConfig, "close")) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(slot, menuConfig, "exit")) {
            player.sendMessage(configManager.getMessage("gui.cancel-process"));
            guiManager.openPanelGUI(player, 1);
            return;
        }
        if (isClickInSlot(slot, menuConfig, "cancel")) {
            // 合并举报后, 直接把当前合并组(同 reported+type)判定为虚假举报
            plugin.getDatabaseManager().asyncRun(() -> {
                reportManager.markAsFake(record.getId(),
                        player.getUniqueId().toString(),
                        player.getName());
                reportManager.batchMarkAsFakeByType(record.getReportedUuid(),
                        record.getReportType(), record.getId(),
                        player.getUniqueId().toString(), player.getName());
                player.sendMessage(configManager.getMessage("gui.fake-report-batch"));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getVolunteerManager().restoreAfterProcess(player);
                    player.closeInventory();
                });
            });
            return;
        }
        // teleport: 传送到被举报者实时位置(复用右键传送逻辑)
        if (isClickInSlot(slot, menuConfig, "teleport")) {
            handleTeleportClick(player, record, false, ClickType.RIGHT);
            return;
        }
        // ban365/ban3650: 弹出 admin_confirm 确认面板
        if (isClickInSlot(slot, menuConfig, "ban365")) {
            selectedReasons.put(player.getUniqueId(), "ban365");
            guiManager.openAdminConfirmGUI(player, record, "ban365", "封禁365天");
            return;
        }
        if (isClickInSlot(slot, menuConfig, "ban3650")) {
            selectedReasons.put(player.getUniqueId(), "ban3650");
            guiManager.openAdminConfirmGUI(player, record, "ban3650", "封禁3650天");
            return;
        }
        // report_player/reported_player 头颅左/右键传送, 注释配置后 slot=-1 自动不进
        if (isClickInSlot(slot, menuConfig, "report_player")) {
            handleTeleportClick(player, record, true, clickType);
            return;
        }
        if (isClickInSlot(slot, menuConfig, "reported_player")) {
            handleTeleportClick(player, record, false, clickType);
            return;
        }
        List<Integer> reasonSlots = menuConfig.getSlots("reasons");
        if (reasonSlots != null && reasonSlots.contains(slot)) {
            String reasonName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            ReasonConfig reason = findReasonByName(reasonName);
            if (reason != null) {
                selectedReasons.put(player.getUniqueId(), reason.getId());
                guiManager.openAdminConfirmGUI(player, record, reason.getId(), reason.getName());
            } else {
                player.sendMessage(configManager.getMessage("gui.reason-not-found"));
            }
        }
    }

    private void handleTeleportClick(Player player, ReportRecord record, boolean isReporter, ClickType clickType) {
        if (!plugin.getVolunteerManager().isPatrolling(player.getUniqueId())) {
            player.sendMessage(configManager.getMessage("gui.need-patrol"));
            return;
        }
        String targetUuid = isReporter ? record.getReporterUuid() : record.getReportedUuid();
        String targetName = isReporter ? record.getReporterName() : record.getReportedName();
        String currentServer = plugin.getServerName();
        if (!currentServer.equals("unknown")) plugin.getVolunteerManager().enterObserverMode(player, currentServer);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", targetName);
        placeholders.put("type", isReporter? "举报者":"被举报者");
        if (clickType == ClickType.LEFT) {
            String targetServer = record.getServer();
            double x, y, z;
            String world;
            if (isReporter) {
                // 举报者
                x = record.getServerX();
                y = record.getServerY();
                z = record.getServerZ();
                world = record.getServerWorld();
            } else {
                // 被举报者
                x = record.getReportedX();
                y = record.getReportedY();
                z = record.getReportedZ();
                world = record.getReportedWorld();
                if (x == 0 && y == 0 && z == 0) {
                    player.sendMessage(configManager.getMessage("teleport.reported-no-position"));
                    return;
                }
            }
            boolean isSameServer = plugin.getServerName().equals(targetServer);
            plugin.getTeleportManager().createTeleportRequest(
                    player.getUniqueId().toString(),
                    targetUuid,
                    targetName,
                    targetServer,
                    x, y, z, world,
                    "report_time",
                    isSameServer
            );
            if (!isSameServer) {
                plugin.getBungeeManager().sendToServer(player, targetServer);
            } else {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getTeleportManager().processTeleportRequest(player);
                    }
                }.runTaskLater(plugin, 20L);
            }
            String messagePath = isReporter ? "teleport.to-reporter-time" : "teleport.to-reported-time";
            player.sendMessage(configManager.getMessage(messagePath, placeholders));
        }
        else if (clickType == ClickType.RIGHT) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null && target.isOnline()) {
                plugin.getTeleportManager().saveAndSetSpectatorMode(player);
                player.teleport(target.getLocation());
                player.sendMessage(configManager.getMessage("teleport.to-player-current", placeholders));
                return;
            }
            // 优先用缓存子服(打开 admin GUI 时已预查), 省掉 getPlayerServer 的 2s 等待
            String cachedServer = guiManager.getCachedPlayerServer(targetUuid);
            if (cachedServer != null) {
                handleCrossServerTeleport(player, targetUuid, targetName, cachedServer, placeholders);
                return;
            }
            // 缓存未命中, 走 AerServerMonitor 查询
            plugin.getPlayerDataManager().getPlayerServer(java.util.UUID.fromString(targetUuid), server -> {
                if (server == null) {
                    player.sendMessage(configManager.getMessage("teleport.player-not-found"));
                    return;
                }
                handleCrossServerTeleport(player, targetUuid, targetName, server, placeholders);
            });
        }
    }

    private void handleCrossServerTeleport(Player player, String targetUuid, String targetName,
                                            String server, Map<String, String> placeholders) {
        if (server.equals(plugin.getServerName())) {
            Player targetNow = Bukkit.getPlayerExact(targetName);
            if (targetNow != null && targetNow.isOnline()) {
                plugin.getTeleportManager().saveAndSetSpectatorMode(player);
                player.teleport(targetNow.getLocation());
                player.sendMessage(configManager.getMessage("teleport.to-player-current", placeholders));
                return;
            }
        }
        plugin.getTeleportManager().createTeleportRequest(
                player.getUniqueId().toString(),
                targetUuid,
                targetName,
                server,
                0, 0, 0, "world",
                "current",
                false
        );
        plugin.getBungeeManager().sendServerInvite(player.getUniqueId(), UUID.fromString(targetUuid), server);
        plugin.getBungeeManager().sendToServer(player, server);
        placeholders.put("server", server);
        player.sendMessage(configManager.getMessage("teleport.to-player-cross-server", placeholders));
    }

    private void handleAdminConfirmClick(Player player, ItemStack item, int slot) {
        MenuConfig menuConfig = configManager.getMenuConfig("admin_confirm");
        ReportRecord record = reportManager.getSelectedReport(player.getUniqueId());
        String reasonId = selectedReasons.get(player.getUniqueId());
        if (record == null || reasonId == null) {
            player.closeInventory();
            return;
        }
        // ban365/ban3650 走特殊封禁命令, 不走 reasons 配置
        boolean isBan365 = "ban365".equals(reasonId);
        boolean isBan3650 = "ban3650".equals(reasonId);
        String reasonName;
        if (isBan365) reasonName = "封禁365天";
        else if (isBan3650) reasonName = "封禁3650天";
        else {
            ReasonConfig reason = configManager.getReason(reasonId);
            if (reason == null) {
                player.sendMessage(configManager.getMessage("gui.reason-config-error"));
                player.closeInventory();
                return;
            }
            reasonName = reason.getName();
        }
        if (isClickInSlot(slot, menuConfig, "cancel")) {
            guiManager.openAdminGUI(player, record);
            return;
        }
        if (isClickInSlot(slot, menuConfig, "confirm")) {
            if (isBan365) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + record.getReportedName() + " 365d volunteer:behaviour");
            } else if (isBan3650) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + record.getReportedName() + " 3650d volunteer:behaviour");
            } else {
                ReasonConfig reason = configManager.getReason(reasonId);
                executePunish(record.getReportedName(), reason);
            }
            reportManager.processReport(record.getId(),
                    player.getUniqueId().toString(),
                    player.getName(),
                    reasonId,
                    reasonName);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reportID", String.valueOf(record.getId()));
            placeholders.put("realReason", reasonName);
            player.sendMessage(configManager.getMessage("process", placeholders));
            plugin.getVolunteerManager().restoreAfterProcess(player);
            plugin.getDatabaseManager().asyncRun(() -> {
                int remaining = reportManager.getPendingReportCount();
                if (remaining > 0) {
                    guiManager.openPanelGUI(player, 1);
                } else {
                    Bukkit.getScheduler().runTask(plugin, player::closeInventory);
                }
            });
        }
    }

    private void executePunish(String reportedName, ReasonConfig reason) {
        Player reported = Bukkit.getPlayerExact(reportedName);
        for (String cmd : reason.getPunish()) {
            cmd = cmd.replace("{reported}", reportedName);
            if (cmd.startsWith("[CONSOLE] ")) {
                String command = cmd.substring("[CONSOLE] ".length());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else if (cmd.startsWith("[COMMAND] ")) {
                if (reported == null || !reported.isOnline()) continue;
                String command = cmd.substring("[COMMAND] ".length());
                boolean wasOp = reported.isOp();
                try {
                    reported.setOp(true);
                    reported.performCommand(command);
                } finally {
                    reported.setOp(wasOp);
                }
            } else if (cmd.startsWith("[CMD] ")) {
                if (reported == null || !reported.isOnline()) continue;
                String command = cmd.substring("[CMD] ".length());
                reported.performCommand(command);
            } else if (cmd.startsWith("[MESSAGE] ")) {
                if (reported != null && reported.isOnline()) {
                    reported.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            cmd.substring("[MESSAGE] ".length())));
                }
            }
        }
    }

    private void notifyStaff(String reportedName, String reason, String server, int reportId) {
        long notifyTime = System.currentTimeMillis();

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reported", reportedName);
        placeholders.put("reason", reason);
        placeholders.put("server", server);
        String message = configManager.getMessage("alert", placeholders);
        plugin.getPlayerDataManager().notifyStaff("aerreport.staff.alert", message);
    }

    private void notifyLocalStaff(String reportedName, String reason, String server, long notifyTime, int reportId) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reported", reportedName);
        placeholders.put("reason", reason);
        placeholders.put("server", server);
        String message = configManager.getMessage("alert", placeholders);
        TextComponent clickableMessage = new TextComponent(message);
        clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/volunteer"));
        clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent("§7点击打开举报处理面板")}));
        long notifyThreshold = configManager.getConfig().getLong("settings.notify-threshold", 1000);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("aerreport.staff.alert")) {
                long joinTime = plugin.getPlayerDataManager().getPlayerJoinTime(p.getName());
                if (joinTime > 0 && joinTime > notifyTime) {
                    long diff = joinTime - notifyTime;
                    if (diff < notifyThreshold) p.spigot().sendMessage(clickableMessage);
                } else {
                    p.spigot().sendMessage(clickableMessage);
                }
            }
        }
    }

    private boolean isInSlot(ItemStack item, MenuConfig menuConfig, String itemName) {
        if (item == null || item.getType() == Material.AIR) return false;
        String configItem = menuConfig.getItem(itemName);
        if (configItem == null) return false;
        Material expectedMaterial = GUIUtils.parseMaterial(configItem);
        return item.getType() == expectedMaterial;
    }
    
    private boolean isClickInSlot(int rawSlot, MenuConfig menuConfig, String itemName) {
        List<Integer> slots = menuConfig.getSlots(itemName);
        return slots != null && slots.contains(rawSlot);
    }

    private ReasonConfig findReasonByName(String name) {
        for (ReasonConfig reason : configManager.getReasons().values()) {
            if (reason.getName().equals(name)) return reason;
        }
        return null;
    }
    
    public void cleanupPlayerData(UUID uuid) {
        selectedReasons.remove(uuid);
    }
    
    private void handleUnbanPanelClick(Player player, ItemStack item, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("unbanpanel");
        if (isClickInSlot(rawSlot, menuConfig, "main")) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "prev")) {
            int page = reportManager.getStaffPage(player.getUniqueId());
            if (page > 1) {
                guiManager.openUnbanPanelGUI(player, page - 1);
            }
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "next")) {
            int page = reportManager.getStaffPage(player.getUniqueId());
            guiManager.openUnbanPanelGUI(player, page + 1);
            return;
        }
        List<Integer> reportSlots = menuConfig.getSlots("report");
        if (reportSlots != null && reportSlots.contains(rawSlot)) {
            String displayName = item.getItemMeta().getDisplayName();
            String idStr = ChatColor.stripColor(displayName).replaceAll("[^0-9]", "");
            try {
                int reportId = Integer.parseInt(idStr);
                if (!guiManager.startOpening(player.getUniqueId())) return;
                plugin.getDatabaseManager().asyncRun(() -> {
                    try {
                        ReportRecord record = reportManager.getReport(reportId);
                        if (record != null && "processed".equals(record.getStatus())) {
                            reportManager.setSelectedReport(player.getUniqueId(), record);
                            guiManager.openUnbanAdminGUI(player, record);
                        }
                    } finally {
                        guiManager.finishOpening(player.getUniqueId());
                    }
                });
            } catch (NumberFormatException e) {
                player.sendMessage(configManager.getMessage("gui.parse-id-fail"));
            }
        }
    }
    
    private void handleUnbanAdminClick(Player player, ItemStack item, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("unbanadmin");
        ReportRecord record = reportManager.getSelectedReport(player.getUniqueId());
        if (record == null) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "close")) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "exit")) {
            guiManager.openUnbanPanelGUI(player, 1);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "changeReason")) {
            guiManager.openAdminGUI(player, record);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "unban")) {
            guiManager.openUnbanConfirmGUI(player, record);
            return;
        }
    }

    private void handleUnbanConfirmClick(Player player, ItemStack item, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("unban_confirm");
        ReportRecord record = reportManager.getSelectedReport(player.getUniqueId());
        if (record == null) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "cancel")) {
            guiManager.openUnbanAdminGUI(player, record);
            return;
        }
        if (isClickInSlot(rawSlot, menuConfig, "confirm")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "unban " + record.getReportedName());
            reportManager.markAsUnbanned(record.getId(), 
                    player.getUniqueId().toString(), 
                    player.getName());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reported", record.getReportedName());
            player.sendMessage(configManager.getMessage("gui.unban-success", placeholders));
            guiManager.openUnbanPanelGUI(player, 1);
        }
    }

    private void handleFullResetConfirmClick(Player player, ItemStack item, int rawSlot) {
        if (rawSlot == 14 || rawSlot == 15 || rawSlot == 24) {
            // cancel
            player.closeInventory();
            player.sendMessage(configManager.getMessage("admin.fullreset-cancelled"));
            return;
        }
        if (rawSlot == 11 || rawSlot == 12 || rawSlot == 20) {
            // confirm
            player.closeInventory();
            reportManager.asyncFullResetAllData(player);
            return;
        }
    }
}
