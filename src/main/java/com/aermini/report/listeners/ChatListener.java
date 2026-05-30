package com.aermini.report.listeners;

import com.aermini.report.AerReport;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {
    private final AerReport plugin;
    public ChatListener(AerReport plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        plugin.getChatManager().recordMessage(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                event.getMessage()
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getPlayerDataManager().recordPlayerJoin(event.getPlayer().getName());
        plugin.getBungeeManager().onPlayerJoin(event.getPlayer());
        plugin.getReportManager().updatePlayerInfo(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                plugin.getServerName(),
                true
        );
        
        if (event.getPlayer().hasPermission("aerreport.staff.main")) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                int pending = plugin.getReportManager().getPendingReportCount();
                if (pending > 0) {
                    String message = plugin.getConfigManager().getMessage("notification")
                            .replace("{waiting}", String.valueOf(pending));
                    TextComponent clickableMessage = new TextComponent(message);
                    clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/volunteer"));
                    clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                            new TextComponent[]{new TextComponent("§7打开志愿者面板")}));
                    event.getPlayer().spigot().sendMessage(clickableMessage);
                }
                plugin.getTeleportManager().checkAndProcessTeleportRequest(
                        event.getPlayer().getUniqueId());
                plugin.getVolunteerManager().checkAndRestoreFromObserverMode(event.getPlayer());
            }, 40L);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getVolunteerManager().checkAndRestoreFromObserverMode(event.getPlayer());
            }, 40L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().clearPlayerJoinTime(event.getPlayer().getName());
        plugin.getReportManager().updatePlayerInfo(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getName(),
                plugin.getServerName(),
                false
        );
        plugin.getReportManager().removeSelectedReport(event.getPlayer().getUniqueId());
        // 要考虑到切子服而退出服务器 于是不在此处重置处理中状态
        // cleanupExpiredProcessing负责定期处理. 见 ReportManager.java
    }
}
