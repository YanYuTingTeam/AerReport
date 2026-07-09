package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.aermini.report.database.DatabaseManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VolunteerManager {
    private final AerReport plugin;
    private final DatabaseManager databaseManager;
    // 志愿者原始位置
    private final Map<UUID, Location> originalLocations = new HashMap<>();

    public VolunteerManager(AerReport plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void enterObserverMode(Player player, String originalServer) {
        originalLocations.put(player.getUniqueId(), player.getLocation().clone());
        databaseManager.asyncRun(() -> databaseManager.saveVolunteerState(player.getUniqueId(), player.getName(), originalServer));
    }

    public boolean exitObserverMode(Player player) {
        UUID uuid = player.getUniqueId();
        String originalServer = databaseManager.getVolunteerOriginalServer(uuid);
        String currentServer = plugin.getServerName();
        List<String> returnWhitelist = plugin.getConfigManager().getReturnServerWhitelist();
        boolean needTransfer = originalServer != null 
                && returnWhitelist.contains(originalServer) 
                && !originalServer.equals(currentServer);
        plugin.getTeleportManager().restoreGameMode(player);
        if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
        if (needTransfer) {
            databaseManager.asyncRun(() -> databaseManager.setVolunteerPendingExit(uuid, true));
            plugin.getBungeeManager().sendToServer(player, originalServer);
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("server", originalServer);
            player.sendMessage(plugin.getConfigManager().getMessage("volunteer.exit-success", placeholders));
        } else {
            Location originalLocation = originalLocations.remove(uuid);
            if (originalLocation != null && originalLocation.getWorld() != null) {
                player.teleport(originalLocation);
                player.sendMessage(plugin.getConfigManager().getMessage("volunteer.restore-success"));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("volunteer.exit-nolocation"));
            }
            databaseManager.asyncRun(() -> databaseManager.removeVolunteerState(uuid));
        }
        return true;
    }

    public void restoreAfterProcess(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isInObserverMode(uuid)) return;
        plugin.getTeleportManager().restoreGameMode(player);
        if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
        Location originalLocation = originalLocations.remove(uuid);
        if (originalLocation != null && originalLocation.getWorld() != null) player.teleport(originalLocation);
        databaseManager.asyncRun(() -> databaseManager.removeVolunteerState(uuid));
    }

    public void checkAndRestoreFromObserverMode(Player player) {
        UUID uuid = player.getUniqueId();
        if (databaseManager.isVolunteerPendingExit(uuid)) {
            databaseManager.asyncRun(() -> {
                databaseManager.setVolunteerPendingExit(uuid, false);
                databaseManager.removeVolunteerState(uuid);
            });
            plugin.getTeleportManager().restoreGameMode(player);
            if (player.getGameMode() == GameMode.SPECTATOR) player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(plugin.getConfigManager().getMessage("volunteer.restore-success"));
        }
    }

    public boolean isInObserverMode(UUID playerUuid) {
        return databaseManager.getVolunteerOriginalServer(playerUuid) != null;
    }
}
