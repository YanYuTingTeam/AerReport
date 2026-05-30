package com.aermini.report.commands;

import com.aermini.report.AerReport;
import com.aermini.report.gui.GUIManager;
import com.aermini.report.managers.ReportManager;
import com.aermini.report.managers.VolunteerManager;
import com.aermini.report.models.ReportRecord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class VolunteerCommand implements CommandExecutor {
    private final AerReport plugin;
    private final GUIManager guiManager;
    public VolunteerCommand(AerReport plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aerreport.staff.main")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("common.no-permission"));
            return true;
        }
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("leave")) {
                handleLeave(player);
                return true;
            }
            if (args[0].equalsIgnoreCase("open")) {
                handleOpen(player, args);
                return true;
            }
        }
        ReportManager reportManager = plugin.getReportManager();
        ReportRecord processingReport = reportManager.getProcessingReportRecord(player.getUniqueId().toString());
        if (processingReport != null) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reportID", String.valueOf(processingReport.getId()));
            player.sendMessage(plugin.getConfigManager().getMessage("volunteer.auto-open-detail", placeholders));
            guiManager.openAdminGUI(player, processingReport);
        } else {
            guiManager.openPanelGUI(player, 1);
        }
        return true;
    }
    
    private void handleLeave(Player player) {
        VolunteerManager volunteerManager = plugin.getVolunteerManager();
        ReportRecord processingReport = plugin.getReportManager().getProcessingReportRecord(player.getUniqueId().toString());
        if (processingReport != null) {
            plugin.getReportManager().cancelProcessing(processingReport.getId(), player.getUniqueId().toString());
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("reportID", String.valueOf(processingReport.getId()));
            player.sendMessage(plugin.getConfigManager().getMessage("volunteer.cancel-report", placeholders));
        }
        boolean success = volunteerManager.exitObserverMode(player);
        if (!success) player.sendMessage(plugin.getConfigManager().getMessage("volunteer.exit-fail"));
    }

    private void handleOpen(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getConfigManager().getMessage("gui.parse-id-fail"));
            return;
        }
        try {
            int reportId = Integer.parseInt(args[1]);
            ReportManager reportManager = plugin.getReportManager();
            ReportRecord record = reportManager.getReport(reportId);
            if (record == null) {
                player.sendMessage(plugin.getConfigManager().getMessage("gui.parse-id-fail"));
                return;
            }
            if (reportManager.isProcessingBy(reportId, player.getUniqueId().toString())) {
                guiManager.openAdminGUI(player, record);
                return;
            }
            if (!"pending".equals(record.getStatus())) {
                player.sendMessage(plugin.getConfigManager().getMessage("gui.report-already-processed"));
                return;
            }
            if (reportManager.isBeingProcessedByOther(reportId, player.getUniqueId().toString())) {
                String processingBy = reportManager.getProcessingByName(reportId);
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("staff", processingBy);
                player.sendMessage(plugin.getConfigManager().getMessage("gui.processing-by-other", placeholders));
                return;
            }
            if (reportManager.startProcessing(reportId, player.getUniqueId().toString(), player.getName())) {
                guiManager.openAdminGUI(player, record);
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("gui.process-fail"));
            }
        } catch (NumberFormatException e) {
            player.sendMessage(plugin.getConfigManager().getMessage("gui.parse-id-fail"));
        }
    }
}
