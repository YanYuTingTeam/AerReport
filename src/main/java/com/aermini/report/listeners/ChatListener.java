package com.aermini.report.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.aermini.report.AerReport;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        plugin.getPlayerDataManager().recordPlayerJoin(player.getName());
        plugin.getBungeeManager().onPlayerJoin(player);
        plugin.getReportManager().updatePlayerInfo(
                player.getUniqueId(),
                player.getName(),
                plugin.getServerName(),
                true
        );

        plugin.getVolunteerManager().hidePatrolsFrom(player);

        if (player.hasPermission("aerreport.staff.main")) {
            plugin.getDatabaseManager().asyncRun(() -> {
                boolean patrol = plugin.getVolunteerManager().syncPatrolFromDb(player);
                if (patrol) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            plugin.getVolunteerManager().applyPatrol(player));
                }
            });

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getDatabaseManager().asyncRun(() -> {
                    int pending = plugin.getReportManager().getPendingReportCount();
                    if (pending > 0) {
                        String message = plugin.getConfigManager().getMessage("notification")
                                .replace("{waiting}", String.valueOf(pending));
                        TextComponent clickableMessage = new TextComponent(message);
                        clickableMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/volunteer"));
                        clickableMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new TextComponent[]{new TextComponent("§7打开志愿者面板")}));
                        player.spigot().sendMessage(clickableMessage);
                    }
                });
                plugin.getTeleportManager().checkAndProcessTeleportRequest(player.getUniqueId());
                plugin.getVolunteerManager().checkAndRestoreFromObserverMode(player);
            }, 40L);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getVolunteerManager().checkAndRestoreFromObserverMode(player);
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getVolunteerManager().isPatrolling(player.getUniqueId())) return;
        if (event.getNewGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getPlayerDataManager().clearPlayerJoinTime(event.getPlayer().getName());
        plugin.getReportManager().updatePlayerInfo(
                uuid,
                event.getPlayer().getName(),
                plugin.getServerName(),
                false
        );
        plugin.getReportManager().cleanupPlayerData(uuid);
        plugin.getGUIManager().removeSelectedPlayer(uuid);
        plugin.getGUIManager().removePanelCache(uuid);
    }
}
