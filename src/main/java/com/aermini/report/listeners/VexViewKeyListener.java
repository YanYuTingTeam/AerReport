package com.aermini.report.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.aermini.report.AerReport;

import lk.vexview.event.KeyBoardPressEvent;
import lk.vexview.event.MinecraftKeys;

public class VexViewKeyListener implements Listener {
    private final AerReport plugin;

    public VexViewKeyListener(AerReport plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKeyPress(KeyBoardPressEvent event) {
        if (event.getEventKeyState()) return;
        if (!MinecraftKeys.KEY_P.isTheKey(event.getKey())) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("aerreport.staff.main")) return;
        plugin.getGUIManager().openPanelGUI(player, 1);
    }
}
