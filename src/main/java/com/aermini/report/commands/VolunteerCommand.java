package com.aermini.report.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.aermini.report.AerReport;

public class VolunteerCommand implements CommandExecutor {
    private final AerReport plugin;
    public VolunteerCommand(AerReport plugin) {
        this.plugin = plugin;
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
        plugin.getGUIManager().openPanelGUI(player, 1);
        return true;
    }
}
