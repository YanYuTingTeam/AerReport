package com.aermini.report.commands;

import com.aermini.report.AerReport;
import com.aermini.report.gui.GUIManager;
import com.aermini.report.managers.ReportManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReportCommand implements CommandExecutor {
    private final AerReport plugin;
    private final ReportManager reportManager;
    private final GUIManager guiManager;

    public ReportCommand(AerReport plugin) {
        this.plugin = plugin;
        this.reportManager = plugin.getReportManager();
        this.guiManager = plugin.getGUIManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getConfigManager().getMessage("common.player-only"));
            return true;
        }
        Player player = (Player) sender;
        if (reportManager.isInCooldown(player.getUniqueId())) {
            String msg = plugin.getConfigManager().getMessage("cooldown");
            player.sendMessage(msg);
            return true;
        }
        if (args.length == 0) {
            guiManager.openReportsGUI(player, 1);
            return true;
        }
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("player_offline"));
            return true;
        }
        guiManager.openReportReasonGUI(player, targetName);
        return true;
    }
}
