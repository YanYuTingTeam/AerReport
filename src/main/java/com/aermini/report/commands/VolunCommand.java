package com.aermini.report.commands;

import com.aermini.report.AerReport;
import com.aermini.report.gui.GUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VolunCommand implements CommandExecutor {
    private final AerReport plugin;
    private final GUIManager guiManager;
    public VolunCommand(AerReport plugin) {
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
        if (!player.hasPermission("aerreport.staff.unban")) {
            sender.sendMessage(plugin.getConfigManager().getMessage("common.no-permission"));
            return true;
        }
        guiManager.openUnbanPanelGUI(player, 1);
        return true;
    }
}
