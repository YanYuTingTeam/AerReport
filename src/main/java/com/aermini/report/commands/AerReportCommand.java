package com.aermini.report.commands;

import com.aermini.report.AerReport;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class AerReportCommand implements CommandExecutor {
    private final AerReport plugin;
    public AerReportCommand(AerReport plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("aerreport.admin")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("common.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                reloadConfig(sender);
                break;
            case "info":
                sendInfo(sender);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "fullreset":
                handleFullReset(sender);
                break;
            case "help":
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void reloadConfig(CommandSender sender) {
        plugin.getConfigManager().reloadConfigs();
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.reload-success"));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.reset-usage"));
            sender.sendMessage(plugin.getConfigManager().getMessage("admin.reset-all-help"));
            return;
        }
        String type = args[1].toLowerCase();
        switch (type) {
            case "all":
                resetAll(sender);
                break;
            default:
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("type", type);
                sender.sendMessage(plugin.getConfigManager().getMessage("admin.reset-type-unknown", placeholders));
                sender.sendMessage(plugin.getConfigManager().getMessage("admin.reset-types-available"));
                break;
        }
    }

    private void resetAll(CommandSender sender) {
        int count = plugin.getReportManager().resetAllReports();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("count", String.valueOf(count));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.reset-all-success", placeholders));
    }

    private void handleFullReset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("common.player-only"));
            return;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("aerreport.admin.fullreset")) {
            player.sendMessage(plugin.getConfigManager().getMessage("common.no-permission"));
            return;
        }
        plugin.getGUIManager().openFullResetConfirmGUI(player);
    }

    private void sendInfo(CommandSender sender) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("version", plugin.getDescription().getVersion());
        placeholders.put("author", plugin.getDescription().getAuthors().toString());
        placeholders.put("count", String.valueOf(plugin.getReportManager().getPendingReportCount()));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.info-header"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.info-version", placeholders));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.info-author", placeholders));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.info-pending", placeholders));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.info-footer"));
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-header"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-report"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-volunteer"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-reload"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-info"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-reset-process"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-reset-all"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-fullreset"));
        sender.sendMessage(plugin.getConfigManager().getMessage("admin.help-footer"));
    }
}
