package com.aermini.report.listeners;

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
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GUIListener implements Listener {
    private final AerReport plugin;
    private final GUIManager guiManager;
    private final ReportManager reportManager;
    private final ConfigManager configManager;
    private final Map<UUID, String> selectedReasons = new HashMap<>();

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
        if (title.contains("还存在其他被举报")) return true;
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
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;
        String title = event.getView().getTitle();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        if (title.equals(configManager.getMenuConfig("reports").getTitle())) {
            event.setCancelled(true);
            handleReportsClick(player, item, event.getClick(), event.getRawSlot());
            return;
        }
        if (title.equals(configManager.getMenuConfig("report").getTitle())) {
            event.setCancelled(true);
            handleReportReasonClick(player, item, event.getRawSlot());
            return;
        }
        if (title.equals("选择玩家处罚")) {
            event.setCancelled(true);
            handlePunishSelectClick(player, item, event.getRawSlot());
            return;
        }
        if (title.equals(configManager.getMenuConfig("panel").getTitle())) {
            event.setCancelled(true);
            handlePanelClick(player, item, event.getClick(), event.getRawSlot());
            return;
        }
        if (title.startsWith("处罚玩家 ")) {
            event.setCancelled(true);
            handleAdminClick(player, item, event.getClick(), event.getRawSlot());
            return;
        }
        if (title.startsWith("是否确定 处罚 ")) {
            event.setCancelled(true);
            handleAdminConfirmClick(player, item, event.getRawSlot());
            return;
        }
        if (title.contains("还存在其他被举报")) {
            event.setCancelled(true);
            handleAdminCancelsClick(player, item, event.getRawSlot());
            return;
        }
        MenuConfig unbanPanelConfig = configManager.getMenuConfig("unbanpanel");
        if (unbanPanelConfig != null && title.equals(unbanPanelConfig.getTitle())) {
            event.setCancelled(true);
            handleUnbanPanelClick(player, item, event.getRawSlot());
            return;
        }
        if (title.startsWith("处理被封禁玩家 ")) {
            event.setCancelled(true);
            handleUnbanAdminClick(player, item, event.getRawSlot());
            return;
        }
        if (title.startsWith("是否确定 解封 ")) {
            event.setCancelled(true);
            handleUnbanConfirmClick(player, item, event.getRawSlot());
            return;
        }
        if (title.equals("§c§l确认完全重置数据")) {
            event.setCancelled(true);
            handleFullResetConfirmClick(player, item, event.getRawSlot());
            return;
        }
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
            if (reportedName != null) {
                Player reported = Bukkit.getPlayerExact(reportedName);
                if (reported != null) {
                    if (!reportManager.canReport(player.getUniqueId().toString(), reported.getUniqueId().toString())) {
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("player", reportedName);
                        player.sendMessage(configManager.getMessage("max-repeat", placeholders));
                        player.closeInventory();
                        return;
                    }
                    String chatMessages = "";
                    if (reason.getType().equals("chat")) chatMessages=plugin.getChatManager().getRecentMessages(reported.getUniqueId());
                    int reportId = reportManager.createReport(player, 
                            reported.getUniqueId().toString(), 
                            reportedName, 
                            reason.getId(), 
                            reason.getName(), 
                            reason.getType(), 
                            chatMessages);
                    if (reportId > 0) {
                        reportManager.setCooldown(player.getUniqueId());
                        reportManager.incrementReportCount(player.getUniqueId(), player.getName());
                        reportManager.incrementReportedCount(reported.getUniqueId(), reportedName);
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("player", reportedName);
                        placeholders.put("reportID", String.valueOf(reportId));
                        player.sendMessage(configManager.getMessage("report", placeholders));
                        notifyStaff(reportedName, reason.getName(), plugin.getServerName(), reportId);
                    }
                }
            }
            player.closeInventory();
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
                    if (target != null) {
                        ReportRecord existingReport = reportManager.getPendingReportByReported(target.getUniqueId().toString());
                        if (existingReport != null) {
                            guiManager.openAdminGUI(player, existingReport);
                        } else {
                            String chatMessages = plugin.getChatManager().getRecentMessages(target.getUniqueId());
                            int reportId = reportManager.createReport(player, 
                                    target.getUniqueId().toString(), 
                                    target.getName(), 
                                    "volunteer", 
                                    "志愿者直接处罚", 
                                    "other", 
                                    chatMessages);
                            if (reportId > 0) {
                                ReportRecord record = reportManager.getReport(reportId);
                                if (record != null) guiManager.openAdminGUI(player, record);
                            }
                        }
                    } else {
                        player.sendMessage(configManager.getMessage("player-not-found"));
                    }
                }
            }
        }
    }

    private void handlePanelClick(Player player, ItemStack item, ClickType clickType, int rawSlot) {
        MenuConfig menuConfig = configManager.getMenuConfig("panel");
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
                ReportRecord record = reportManager.getReport(reportId);
                if (record != null) {
                    if (reportManager.isBeingProcessedByOther(reportId, player.getUniqueId().toString())) {
                        String processingBy = reportManager.getProcessingByName(reportId);
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("staff", processingBy);
                        player.sendMessage(configManager.getMessage("gui.processing-by-other", placeholders));
                        return;
                    }
                    if (reportManager.startProcessing(reportId, player.getUniqueId().toString(), player.getName())) {
                        guiManager.openAdminGUI(player, record);
                    } else {
                        player.sendMessage(configManager.getMessage("gui.process-fail"));
                    }
                }
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
            reportManager.cancelProcessing(record.getId(), player.getUniqueId().toString());
            plugin.getVolunteerManager().restoreAfterProcess(player);
            player.closeInventory();
            return;
        }
        if (isClickInSlot(slot, menuConfig, "exit")) {
            reportManager.cancelProcessing(record.getId(), player.getUniqueId().toString());
            player.sendMessage(configManager.getMessage("gui.cancel-process"));
            plugin.getVolunteerManager().restoreAfterProcess(player);
            guiManager.openPanelGUI(player, 1);
            return;
        }
        if (isClickInSlot(slot, menuConfig, "cancel")) {
            List<ReportRecord> otherReports = reportManager.getOtherPendingReports(
                    record.getReportedUuid(), record.getId());
            if (!otherReports.isEmpty()) {
                guiManager.openAdminCancelsGUI(player, record, otherReports);
            } else {
                reportManager.markAsFake(record.getId(), 
                        player.getUniqueId().toString(), 
                        player.getName());
                player.sendMessage(configManager.getMessage("gui.fake-report-single"));
                player.closeInventory();
            }
            return;
        }
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
                if ("processed".equals(record.getStatus())) reportManager.startProcessing(record.getId(), player.getUniqueId().toString(), player.getName());
                selectedReasons.put(player.getUniqueId(), reason.getId());
                guiManager.openAdminConfirmGUI(player, record, reason.getId(), reason.getName());
            } else {
                player.sendMessage(configManager.getMessage("gui.reason-not-found"));
            }
        }
    }

    private void handleTeleportClick(Player player, ReportRecord record, boolean isReporter, ClickType clickType) {
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
            } else {
                plugin.getPlayerDataManager().getPlayerServer(targetName, server -> {
                    if (server != null) {
                        plugin.getTeleportManager().createTeleportRequest(
                                player.getUniqueId().toString(),
                                targetUuid,
                                targetName,
                                server,
                                0, 0, 0, "world",
                                "current",
                                false
                        );
                        plugin.getBungeeManager().sendToServer(player, server);
                        placeholders.put("server", server);
                        player.sendMessage(configManager.getMessage("teleport.to-player-cross-server", placeholders));
                    } else {
                        player.sendMessage(configManager.getMessage("teleport.player-not-found"));
                    }
                });
            }
        }
    }

    private void handleAdminConfirmClick(Player player, ItemStack item, int slot) {
        MenuConfig menuConfig = configManager.getMenuConfig("admin_confirm");
        ReportRecord record = reportManager.getSelectedReport(player.getUniqueId());
        String reasonId = selectedReasons.get(player.getUniqueId());
        if (record == null || reasonId == null) {
            player.closeInventory();
            return;
        }
        ReasonConfig reason = configManager.getReason(reasonId);
        if (reason == null) {
            player.sendMessage(configManager.getMessage("gui.reason-config-error"));
            player.closeInventory();
            return;
        }
        if (isClickInSlot(slot, menuConfig, "cancel")) {
            guiManager.openAdminGUI(player, record);
            return;
        }
        if (isClickInSlot(slot, menuConfig, "confirm")) {
            executePunish(record.getReportedName(), reason);
            reportManager.processReport(record.getId(), 
                    player.getUniqueId().toString(), 
                    player.getName(), 
                    reasonId,
                    reason.getName());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reportID", String.valueOf(record.getId()));
            placeholders.put("realReason", reason.getName());
            player.sendMessage(configManager.getMessage("process", placeholders));
            plugin.getVolunteerManager().restoreAfterProcess(player);
            int remaining = reportManager.getPendingReportCount();
            if (remaining > 0) {
                guiManager.openPanelGUI(player, 1);
            } else {
                player.closeInventory();
            }
        }
    }

    private void handleAdminCancelsClick(Player player, ItemStack item, int slot) {
        MenuConfig menuConfig = configManager.getMenuConfig("admin_cancels");
        ReportRecord record = reportManager.getSelectedReport(player.getUniqueId());
        if (record == null) {
            player.closeInventory();
            return;
        }
        if (isClickInSlot(slot, menuConfig, "cancel")) {
            reportManager.markAsFake(record.getId(), 
                    player.getUniqueId().toString(), 
                    player.getName());
            player.sendMessage(configManager.getMessage("gui.fake-report-single"));
            plugin.getVolunteerManager().restoreAfterProcess(player);
            int remaining = reportManager.getPendingReportCount();
            if (remaining > 0) {
                guiManager.openPanelGUI(player, 1);
            } else {
                player.closeInventory();
            }
            return;
        }
        if (isClickInSlot(slot, menuConfig, "confirm")) {
            reportManager.markAsFake(record.getId(), 
                    player.getUniqueId().toString(), 
                    player.getName());
            reportManager.batchMarkAsFake(record.getReportedUuid(), 
                    record.getId(), 
                    player.getUniqueId().toString(), 
                    player.getName());
            player.sendMessage(configManager.getMessage("gui.fake-report-batch"));
            plugin.getVolunteerManager().restoreAfterProcess(player);
            int remaining = reportManager.getPendingReportCount();
            if (remaining > 0) {
                guiManager.openPanelGUI(player, 1);
            } else {
                player.closeInventory();
            }
        }
    }

    private void executePunish(String reportedName, ReasonConfig reason) {
        for (String cmd : reason.getPunish()) {
            cmd = cmd.replace("{reported}", reportedName);
            if (cmd.startsWith("[CONSOLE] ")) {
                String command = cmd.substring("[CONSOLE] ".length());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else if (cmd.startsWith("[COMMAND] ") || cmd.startsWith("[CMD] ")) {
                String command = cmd.startsWith("[COMMAND] ") 
                        ? cmd.substring("[COMMAND] ".length()) 
                        : cmd.substring("[CMD] ".length());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else if (cmd.startsWith("[MESSAGE] ")) {
                Player reported = Bukkit.getPlayerExact(reportedName);
                if (reported != null) {
                    reported.sendMessage(ChatColor.translateAlternateColorCodes('&', 
                            cmd.substring("[MESSAGE] ".length())));
                }
            }
        }
    }

    private void notifyStaff(String reportedName, String reason, String server, int reportId) {
        long notifyTime = System.currentTimeMillis();
        notifyLocalStaff(reportedName, reason, server, notifyTime, reportId);
        plugin.getBungeeManager().broadcastReportAlert(reportedName, reason, server, reportId);
    }

    private void notifyLocalStaff(String reportedName, String reason, String server, long notifyTime, int reportId) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("reported", reportedName);
        placeholders.put("reason", reason);
        placeholders.put("server", server);
        String message = configManager.getMessage("alert", placeholders);
        TextComponent clickableMessage = new TextComponent(message);
        clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, 
                "/volunteer open " + reportId));
        clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new TextComponent[]{new TextComponent("§7点击处理第§f§l"+reportId+"§r§7号举报")}));
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
                ReportRecord record = reportManager.getReport(reportId);
                if (record != null && "processed".equals(record.getStatus())) {
                    reportManager.setSelectedReport(player.getUniqueId(), record);
                    guiManager.openUnbanAdminGUI(player, record);
                }
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
            reportManager.markAsFake(record.getId(), 
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
