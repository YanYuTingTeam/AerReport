package com.aermini.report.commands;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.aermini.report.AerReport;
import com.aermini.report.gui.GUIManager;
import com.aermini.report.managers.ReportManager;
import com.aermini.report.managers.VolunteerManager;
import com.aermini.report.models.ReportRecord;

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
        }
        ReportManager reportManager = plugin.getReportManager();
        plugin.getDatabaseManager().asyncRun(() -> {
            ReportRecord processingReport = reportManager.getProcessingReportRecord(player.getUniqueId().toString());
            if (processingReport != null) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("reportID", String.valueOf(processingReport.getId()));
                player.sendMessage(plugin.getConfigManager().getMessage("volunteer.auto-open-detail", placeholders));
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> guiManager.openAdminGUINow(player, processingReport));
            } else {
                guiManager.openPanelGUI(player, 1);
            }
        });
        return true;
    }
    
    private void handleLeave(Player player) {
        VolunteerManager volunteerManager = plugin.getVolunteerManager();
        plugin.getDatabaseManager().asyncRun(() -> {
            ReportRecord processingReport = plugin.getReportManager().getProcessingReportRecord(player.getUniqueId().toString());
            if (processingReport != null) {
                plugin.getReportManager().cancelProcessing(processingReport.getId(), player.getUniqueId().toString());
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("reportID", String.valueOf(processingReport.getId()));
                player.sendMessage(plugin.getConfigManager().getMessage("volunteer.cancel-report", placeholders));
            }
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                boolean success = volunteerManager.exitObserverMode(player);
                if (!success) player.sendMessage(plugin.getConfigManager().getMessage("volunteer.exit-fail"));
            });
        });
    }
}
