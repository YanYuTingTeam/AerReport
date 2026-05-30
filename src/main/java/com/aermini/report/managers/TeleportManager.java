package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.aermini.report.database.DatabaseManager;
import com.aermini.report.models.TeleportRequest;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportManager {
    private final AerReport plugin;
    private final DatabaseManager db;
    private final Map<UUID, GameMode> originalGameModes = new HashMap<>();

    public TeleportManager(AerReport plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    public void createTeleportRequest(String staffUuid, String targetUuid, String targetName,
                                     String targetServer, double x, double y, double z, 
                                     String world, String teleportType, boolean isSameServer) {
        long time = System.currentTimeMillis();
        String sql = "INSERT INTO " + db.getTablePrefix() + "teleport " +
                "(staff_uuid, target_uuid, target_name, target_server, target_x, target_y, target_z, " +
                "target_world, teleport_type, created_time, processed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            db.update(sql, staffUuid, targetUuid, targetName, targetServer, x, y, z, world, teleportType, time, false);
            // 跨服传送时执行. 同服传送在processTeleportRequest中执行
            if (!isSameServer) {
                Player staff = plugin.getServer().getPlayer(UUID.fromString(staffUuid));
                if (staff != null && staff.isOnline()) {
                    saveAndSetSpectatorMode(staff);
                    executeTeleportCommands(staff, targetName);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("创建传送请求失败 -> " + e.getMessage());
        }
    }
    
    public void saveAndSetSpectatorMode(Player player) {
        if (!originalGameModes.containsKey(player.getUniqueId())) {
            originalGameModes.put(player.getUniqueId(), player.getGameMode());
        }
        player.setGameMode(GameMode.SPECTATOR);
    }

    public void restoreGameMode(Player player) {
        GameMode original = originalGameModes.remove(player.getUniqueId());
        if (original != null) player.setGameMode(original);
    }
    
    public boolean isInObserverMode(UUID playerUuid) {
        return originalGameModes.containsKey(playerUuid);
    }

    public void checkAndProcessTeleportRequest(UUID staffUuid) {
        TeleportRequest request = getPendingRequest(staffUuid.toString());
        if (request != null) {
            processTeleportRequest(request);
            markRequestProcessed(request.getId());
        }
    }

    private TeleportRequest getPendingRequest(String staffUuid) {
        String sql = "SELECT * FROM " + db.getTablePrefix() + "teleport " +
                "WHERE staff_uuid = ? AND processed = ? ORDER BY created_time DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, staffUuid);
            stmt.setBoolean(2, false);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return TeleportRequest.fromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取传送请求失败 -> " + e.getMessage());
        }
        return null;
    }

    public void processTeleportRequest(Player player) {
        TeleportRequest request = getPendingRequest(player.getUniqueId().toString());
        if (request != null) {
            processTeleportRequest(request);
            markRequestProcessed(request.getId());
        }
    }

    private void processTeleportRequest(TeleportRequest request) {
        Player staff = Bukkit.getPlayer(UUID.fromString(request.getStaffUuid()));
        if (staff == null || !staff.isOnline()) return;
        String targetName = request.getTargetName();
        saveAndSetSpectatorMode(staff);
        executeTeleportCommands(staff, targetName);
        if (request.getTeleportType().equals("report_time") || 
            request.getTeleportType().equals("current")) {
            
            // 传送到现在位置
            if (request.getTeleportType().equals("current")) {
                Player target = Bukkit.getPlayer(UUID.fromString(request.getTargetUuid()));
                if (target != null && target.isOnline()) {
                    staff.teleport(target.getLocation());
                    staff.sendMessage(plugin.getConfigManager().getMessage("teleport.success-current"));
                    return;
                }
            }
            
            // 传送到举报时位置
            if (request.getTargetX() != 0 || request.getTargetY() != 0 || request.getTargetZ() != 0) {
                World world = Bukkit.getWorld(request.getTargetWorld());
                if (world != null) {
                    Location loc = new Location(world, 
                            request.getTargetX(), 
                            request.getTargetY(), 
                            request.getTargetZ());
                    staff.teleport(loc);
                    staff.sendMessage(plugin.getConfigManager().getMessage("teleport.success-report-time"));
                } else {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("world", request.getTargetWorld());
                    staff.sendMessage(plugin.getConfigManager().getMessage("teleport.world-not-found", placeholders));
                }
            } else {
                staff.sendMessage(plugin.getConfigManager().getMessage("teleport.fail"));
            }
        }
    }
    
    private void executeTeleportCommands(Player player, String targetPlayer) {
        // [COMMAND] op | [CMD] player | [CONSOLE]
        java.util.List<String> commands = plugin.getConfigManager().getSpecCommands();
        if (commands == null || commands.isEmpty()) return;
        for (String cmd : commands) {
            String formattedCmd = cmd;
            boolean consoleExecute = false;
            if (formattedCmd.startsWith("[CONSOLE] ")) {
                consoleExecute = true;
                formattedCmd = formattedCmd.substring(10);
            } else if (formattedCmd.startsWith("[COMMAND] ")) {
                formattedCmd = formattedCmd.substring(10);
            } else if (formattedCmd.startsWith("[CMD] ")) {
                formattedCmd = formattedCmd.substring(6);
            }
            formattedCmd = formattedCmd.replace("{player}", player.getName());
            formattedCmd = formattedCmd.replace("{tplayer}", targetPlayer);
            if (consoleExecute) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), formattedCmd);
            } else {
                player.performCommand(formattedCmd);
            }
        }
    }

    private void markRequestProcessed(int requestId) {
        String sql = "UPDATE " + db.getTablePrefix() + "teleport SET processed = ? WHERE id = ?";
        try {
            db.update(sql, true, requestId);
        } catch (SQLException e) {
            plugin.getLogger().severe("标记传送请求失败 -> " + e.getMessage());
        }
    }
    
    public void executeCommandsForStaff(Player player) {
        String reportedName = plugin.getReportManager().getProcessingReport(player.getUniqueId().toString());
        if (reportedName != null) executeTeleportCommands(player, reportedName);
    }

    public void cleanupExpiredRequests() {
        long expireTime = System.currentTimeMillis() - 3600000; // 1h留存
        String sql = "DELETE FROM " + db.getTablePrefix() + "teleport WHERE created_time < ? OR processed = ?";
        try {
            db.update(sql, expireTime, true);
        } catch (SQLException e) {
            plugin.getLogger().severe("清理传送请求失败 -> " + e.getMessage());
        }
    }
}
