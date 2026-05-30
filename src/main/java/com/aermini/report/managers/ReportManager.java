package com.aermini.report.managers;

import com.aermini.report.managers.ConfigManager.ReasonConfig;
import com.aermini.report.AerReport;
import com.aermini.report.database.DatabaseManager;
import com.aermini.report.models.PlayerInfo;
import com.aermini.report.models.ReportRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReportManager {

    private final AerReport plugin;
    private final DatabaseManager db;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Integer> playerPages; // vt页码
    private final Map<UUID, Integer> staffPages; // vtn页码
    private final Map<UUID, ReportRecord> selectedReports; // 选中的举报记录
    // reportID -> staffUUID
    private final Map<Integer, String> processingReports = new HashMap<>();
    public ReportManager(AerReport plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.cooldowns = new HashMap<>();
        this.playerPages = new HashMap<>();
        this.staffPages = new HashMap<>();
        this.selectedReports = new HashMap<>();
    }

    public boolean isInCooldown(UUID uuid) {
        if (!cooldowns.containsKey(uuid)) return false;
        long lastTime = cooldowns.get(uuid);
        long cooldown = plugin.getConfigManager().getCooldown();
        return System.currentTimeMillis() - lastTime < cooldown;
    }

    public void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    public long getRemainingCooldown(UUID uuid) {
        if (!cooldowns.containsKey(uuid)) return 0;
        long lastTime = cooldowns.get(uuid);
        long cooldown = plugin.getConfigManager().getCooldown();
        long remaining = cooldown - (System.currentTimeMillis() - lastTime);
        return remaining > 0 ? remaining : 0;
    }

    /**
     * 获取未处理举报次数
     * @param reporterUuid 举报者UUID
     * @param reportedUuid 被举报者UUID
     * @return 未处理的举报次数
     */
    public int getPendingReportCountByReporter(String reporterUuid, String reportedUuid) {
        String sql = "SELECT COUNT(*) FROM " + db.getTablePrefix() + "reports " +
                "WHERE reporter = ? AND reported = ? AND status IN ('pending', 'processing')";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reporterUuid);
            stmt.setString(2, reportedUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取数据失败 -> " + e.getMessage());
        }
        return 0;
    }

    public boolean canReport(String reporterUuid, String reportedUuid) {
        int maxRepeat = plugin.getConfigManager().getMaxRepeat();
        if (maxRepeat <= 0) return true; // 不限制举报
        int currentCount = getPendingReportCountByReporter(reporterUuid, reportedUuid);
        return currentCount < maxRepeat;
    }

    public int createReport(Player reporter, String reportedUuid, String reportedName, 
                           String reasonId, String reasonName, String reportType, 
                           String chatMessages) {
        String server = plugin.getServerName();
        String reporterUuid = reporter.getUniqueId().toString();
        String reporterName = reporter.getName();
        Location loc = reporter.getLocation();
        long reportTime = System.currentTimeMillis();
        
        double reportedX = 0, reportedY = 0, reportedZ = 0;
        String reportedWorld = null;
        Player reportedPlayer = plugin.getServer().getPlayer(reportedName);
        if (reportedPlayer != null && reportedPlayer.isOnline()) {
            Location reportedLoc = reportedPlayer.getLocation();
            reportedX = reportedLoc.getX();
            reportedY = reportedLoc.getY();
            reportedZ = reportedLoc.getZ();
            reportedWorld = reportedLoc.getWorld().getName();
        }
        
        String sql = "INSERT INTO " + db.getTablePrefix() + "reports " +
                "(server, reporter, reporter_name, reported, reported_name, reason_id, reason_name, " +
                "report_type, report_time, server_x, server_y, server_z, server_world, " +
                "reported_x, reported_y, reported_z, reported_world, chat_messages, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')";
        try {
            return db.insertAndGetId(sql, server, reporterUuid, reporterName, reportedUuid, reportedName,
                    reasonId, reasonName, reportType, reportTime, loc.getX(), loc.getY(), loc.getZ(),
                    loc.getWorld().getName(), reportedX, reportedY, reportedZ, reportedWorld, chatMessages);
        } catch (SQLException e) {
            plugin.getLogger().severe("创建举报失败 -> " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public List<ReportRecord> getPendingReports(int page, int pageSize) {
        List<ReportRecord> reports = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM " + db.getTablePrefix() + "reports WHERE status IN ('pending', 'processing') " +
                "ORDER BY report_time ASC LIMIT ? OFFSET ?";
        
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pageSize);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReportRecord record = ReportRecord.fromResultSet(rs);
                    record.setReportCount(getReportedCountBeforeTime(record.getReportedUuid(), record.getReportTime()));
                    record.setReportCounts(getTotalReportedCount(record.getReportedUuid()));
                    reports.add(record);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取举报列表失败 -> " + e.getMessage());
            e.printStackTrace();
        }
        return reports;
    }

    public int getPendingReportCount() {
        String sql = "SELECT COUNT(*) FROM " + db.getTablePrefix() + "reports WHERE status IN ('pending', 'processing')";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("获取举报数量失败 -> " + e.getMessage());
        }
        return 0;
    }
    
    public List<ReportRecord> getProcessedReports(int page, int pageSize) {
        List<ReportRecord> reports = new ArrayList<>();
        int offset = (page - 1) * pageSize;
        String sql = "SELECT * FROM " + db.getTablePrefix() + "reports WHERE status = 'processed' " +
                "ORDER BY processed_time DESC LIMIT ? OFFSET ?";
        
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, pageSize);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ReportRecord record = ReportRecord.fromResultSet(rs);
                    record.setReportCount(getReportedCountBeforeTime(record.getReportedUuid(), record.getReportTime()));
                    record.setReportCounts(getTotalReportedCount(record.getReportedUuid()));
                    reports.add(record);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取已处理举报失败 -> " + e.getMessage());
            e.printStackTrace();
        }
        return reports;
    }
    
    public int getProcessedReportCount() {
        String sql = "SELECT COUNT(*) FROM " + db.getTablePrefix() + "reports WHERE status = 'processed'";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取已处理举报失败 -> " + e.getMessage());
        }
        return 0;
    }

    public int getReportedCountBeforeTime(String reportedUuid, long beforeTime) {
        String sql = "SELECT COUNT(*) FROM " + db.getTablePrefix() + "reports " +
                "WHERE reported = ? AND report_time <= ? AND status != 'fake'";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reportedUuid);
            stmt.setLong(2, beforeTime);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取被举报次数失败 -> " + e.getMessage());
        }
        return 0;
    }

    public int getTotalReportedCount(String reportedUuid) {
        String sql = "SELECT COUNT(*) FROM " + db.getTablePrefix() + "reports " +
                "WHERE reported = ? AND status != 'fake'";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reportedUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取总被举报次数失败 -> " + e.getMessage());
        }
        return 0;
    }

    public ReportRecord getReport(int reportId) {
        String sql = "SELECT * FROM " + db.getTablePrefix() + "reports WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reportId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ReportRecord record = ReportRecord.fromResultSet(rs);
                    record.setReportCount(getReportedCountBeforeTime(record.getReportedUuid(), record.getReportTime()));
                    record.setReportCounts(getTotalReportedCount(record.getReportedUuid()));
                    return record;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取举报记录失败 -> " + e.getMessage());
        }
        return null;
    }

    public boolean startProcessing(int reportId, String staffUuid, String staffName) {
        String checkSql = "SELECT status, processing_by FROM " + db.getTablePrefix() + "reports WHERE id = ?"; // 检查是否已被处理
        try (Connection conn = db.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, reportId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    String processingBy = rs.getString("processing_by");
                    if ("processing".equals(status) && processingBy != null && !processingBy.equals(staffUuid)) {
                        return false; // 已被处理
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("检查举报状态失败 -> " + e.getMessage());
            return false;
        }
        long processingTime = System.currentTimeMillis();
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'processing', " +
                "processing_by = ?, processing_time = ? WHERE id = ?";
        try {
            db.update(sql, staffUuid, processingTime, reportId);
            processingReports.put(reportId, staffUuid);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("设置处理状态失败 -> " + e.getMessage());
            return false;
        }
    }

    public void cancelProcessing(int reportId, String staffUuid) {
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'pending', " +
                "processing_by = NULL, processing_time = NULL WHERE id = ? AND processing_by = ?";
        try {
            db.update(sql, reportId, staffUuid);
            processingReports.remove(reportId);
        } catch (SQLException e) {
            plugin.getLogger().severe("取消处理状态失败 -> " + e.getMessage());
        }
    }

    public String getProcessingBy(int reportId) {
        String sql = "SELECT processing_by FROM " + db.getTablePrefix() + "reports WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, reportId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("processing_by");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取处理志愿者失败 -> " + e.getMessage());
        }
        return null;
    }

    public boolean isBeingProcessedByOther(int reportId, String staffUuid) {
        String processingBy = getProcessingBy(reportId);
        return processingBy != null && !processingBy.equals(staffUuid);
    }
    
    public boolean isProcessingBy(int reportId, String staffUuid) {
        String processingBy = getProcessingBy(reportId);
        return staffUuid.equals(processingBy);
    }

    public String getProcessingByName(int reportId) {
        String processingUuid = getProcessingBy(reportId);
        if (processingUuid == null) return null;
        Player player = Bukkit.getPlayer(UUID.fromString(processingUuid));
        if (player != null) return player.getName();
        String sql = "SELECT name FROM " + db.getTablePrefix() + "players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, processingUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("name");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取处理者名称失败 -> " + e.getMessage());
        }
        return processingUuid;
    }

    public ReportRecord getProcessingReportRecord(String staffUuid) {
        String sql = "SELECT * FROM " + db.getTablePrefix() + "reports WHERE processing_by = ? AND status = 'processing'";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, staffUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return ReportRecord.fromResultSet(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取正在处理的举报失败 -> " + e.getMessage());
        }
        return null;
    }

    /**
     * 获取指定玩家的待处理举报 pending/processing
     * @param reportedUuid 被举报者的UUID
     * @return 最近的待处理举报记录，如果没有则返回null
     */
    public ReportRecord getPendingReportByReported(String reportedUuid) {
        String sql = "SELECT * FROM " + db.getTablePrefix() + "reports " +
                "WHERE reported_uuid = ? AND status IN ('pending', 'processing') " +
                "ORDER BY report_time DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reportedUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ReportRecord record = ReportRecord.fromResultSet(rs);
                    record.setReportCount(getReportedCountBeforeTime(record.getReportedUuid(), record.getReportTime()));
                    record.setReportCounts(getTotalReportedCount(record.getReportedUuid()));
                    return record;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取待处理举报失败 -> " + e.getMessage());
        }
        return null;
    }

    public void processReport(int reportId, String staffUuid, String staffName, String reasonId, String reasonName) {
        long processTime = System.currentTimeMillis();
        ReportRecord record = getReport(reportId);
        String banId = null;
        if (record != null) banId = getLatestBanId(record.getReportedUuid(), processTime); // 取封禁编号
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'processed', " +
                "processed_by = ?, processed_by_name = ?, processed_time = ?, " +
                "processed_reason_id = ?, processed_reason_name = ?, " +
                "ban_id = ?, processing_by = NULL WHERE id = ?";
        try {
            db.update(sql, staffUuid, staffName, processTime, reasonId, reasonName, banId, reportId);
            if (record != null) {
                linkOtherReports(record.getReportedUuid(), reportId);
                logAction(reportId, staffUuid, staffName, "process", record.getReporterUuid(), 
                        record.getReportedUuid(), reasonId, null);
            }
            processingReports.remove(reportId);
        } catch (SQLException e) {
            plugin.getLogger().severe("处理举报失败 -> " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getLatestBanId(String reportedUuid, long processTime) {
        String litebansDb = plugin.getConfig().getString("database.litebans_database", "");
        String tableName;
        if (litebansDb != null && !litebansDb.isEmpty()) {
            tableName = litebansDb + ".litebans_bans";
        } else {
            tableName = "litebans_bans";
        }
        
        String sql = "SELECT id FROM " + tableName + " WHERE uuid = ? AND time >= ? ORDER BY id DESC LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            String uuidNoDash = reportedUuid.replace("-", "");
            long searchTime = processTime - 300000;
            stmt.setString(1, uuidNoDash);
            stmt.setLong(2, searchTime);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return String.valueOf(rs.getInt("id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("查询封禁ID失败 -> " + e.getMessage());
        }
        return null;
    }
    
    public void processReport(int reportId, String staffUuid, String staffName, String reasonId) {
        String reasonName = reasonId;
        if (plugin.getConfigManager() != null) {
            ReasonConfig reason = plugin.getConfigManager().getReason(reasonId);
            if (reason != null) reasonName = reason.getName();
        }
        processReport(reportId, staffUuid, staffName, reasonId, reasonName);
    }

    public void markAsFake(int reportId, String staffUuid, String staffName) {
        long processTime = System.currentTimeMillis();
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'fake', " +
                "processed_by = ?, processed_by_name = ?, processed_time = ?, processing_by = NULL WHERE id = ?";
        try {
            db.update(sql, staffUuid, staffName, processTime, reportId);
            ReportRecord record = getReport(reportId);
            if (record != null) {
                logAction(reportId, staffUuid, staffName, "fake", record.getReporterUuid(), 
                        record.getReportedUuid(), "虚假举报", null);
            }
            processingReports.remove(reportId);
        } catch (SQLException e) {
            plugin.getLogger().severe("判定虚假举报失败 -> " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void batchMarkAsFake(String reportedUuid, int excludeReportId, String staffUuid, String staffName) {
        long processTime = System.currentTimeMillis();
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'fake', " +
                "processed_by = ?, processed_time = ?, linked_report_id = ?, processing_by = NULL " +
                "WHERE reported = ? AND status IN ('pending', 'processing') AND id != ?";
        try {
            int count = db.update(sql, staffUuid, processTime, excludeReportId, reportedUuid, excludeReportId);
            plugin.getLogger().info("批量判定虚假举报: reportedUuid=" + reportedUuid + 
                    ", excludeReportId=" + excludeReportId + ", 更新了 " + count + " 条记录");
        } catch (SQLException e) {
            plugin.getLogger().severe("批量判定虚假举报失败 -> " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void linkOtherReports(String reportedUuid, int reportId) {
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET linked_report_id = ?, " +
                "status = 'processed', processing_by = NULL WHERE reported = ? AND status IN ('pending', 'processing') AND id != ?";
        try {
            db.update(sql, reportId, reportedUuid, reportId);
        } catch (SQLException e) {
            plugin.getLogger().severe("关联其他举报失败 -> " + e.getMessage());
        }
    }

    public List<ReportRecord> getOtherPendingReports(String reportedUuid, int excludeReportId) {
        List<ReportRecord> reports = new ArrayList<>();
        String sql = "SELECT * FROM " + db.getTablePrefix() + "reports WHERE reported = ? " +
                "AND status IN ('pending', 'processing') AND id != ? ORDER BY report_time DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, reportedUuid);
            stmt.setInt(2, excludeReportId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    reports.add(ReportRecord.fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取其他举报失败 -> " + e.getMessage());
        }
        return reports;
    }

    private void logAction(int reportId, String staffUuid, String staffName, String action,
                          String reporterUuid, String reportedUuid, String reasonId, Integer linkedReportId) {
        long time = System.currentTimeMillis();
        String sql = "INSERT INTO " + db.getTablePrefix() + "logs " +
                "(report_id, staff_uuid, staff_name, action, reporter_uuid, reported_uuid, " +
                "reason_id, linked_report_id, time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {
            db.update(sql, reportId, staffUuid, staffName, action, reporterUuid, reportedUuid, 
                    reasonId, linkedReportId, time);
        } catch (SQLException e) {
            plugin.getLogger().severe("记录操作日志失败 -> " + e.getMessage());
        }
    }

    public PlayerInfo getOrCreatePlayerInfo(UUID uuid, String name) {
        String sql = "SELECT * FROM " + db.getTablePrefix() + "players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return PlayerInfo.fromResultSet(rs);
            }
            String insertSql = "INSERT INTO " + db.getTablePrefix() + "players " +
                    "(uuid, name, current_server, last_online, is_online) VALUES (?, ?, ?, ?, ?)";
            db.update(insertSql, uuid.toString(), name, plugin.getServerName(), 
                    System.currentTimeMillis(), true);
            PlayerInfo info = new PlayerInfo();
            info.setUuid(uuid.toString());
            info.setName(name);
            info.setReportCount(0);
            info.setReportedCount(0);
            info.setCurrentServer(plugin.getServerName());
            info.setOnline(true);
            return info;
        } catch (SQLException e) {
            plugin.getLogger().severe("获取玩家信息失败 -> " + e.getMessage());
        }
        return null;
    }

    public void updatePlayerInfo(UUID uuid, String name, String server, boolean online) {
        String sql = "INSERT INTO " + db.getTablePrefix() + "players " +
                "(uuid, name, current_server, last_online, is_online) VALUES (?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = ?, current_server = ?, last_online = ?, is_online = ?";
        try {
            long time = System.currentTimeMillis();
            db.update(sql, uuid.toString(), name, server, time, online, name, server, time, online);
        } catch (SQLException e) {
            plugin.getLogger().severe("更新玩家信息失败 -> " + e.getMessage());
        }
    }

    public void incrementReportCount(UUID uuid, String name) {
        String sql = "INSERT INTO " + db.getTablePrefix() + "players " +
                "(uuid, name, report_count) VALUES (?, ?, 1) " +
                "ON DUPLICATE KEY UPDATE report_count = report_count + 1";
        try {
            db.update(sql, uuid.toString(), name);
        } catch (SQLException e) {
            plugin.getLogger().severe("更新举报次数失败 -> " + e.getMessage());
        }
    }

    public void incrementReportedCount(UUID uuid, String name) {
        String sql = "INSERT INTO " + db.getTablePrefix() + "players " +
                "(uuid, name, reported_count) VALUES (?, ?, 1) " +
                "ON DUPLICATE KEY UPDATE reported_count = reported_count + 1";
        try {
            db.update(sql, uuid.toString(), name);
        } catch (SQLException e) {
            plugin.getLogger().severe("更新被举报次数失败 -> " + e.getMessage());
        }
    }
    
    public String getProcessingReport(String staffUuid) {
        String sql = "SELECT reported_name FROM " + db.getTablePrefix() + "reports " +
                "WHERE processing_by = ? AND status = 'processing' LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, staffUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("reported_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取处理中举报失败 -> " + e.getMessage());
        }
        return null;
    }

    public void cleanupExpiredProcessing() {
        long expireTime = System.currentTimeMillis() - 5 * 60 * 1000;
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'pending', " +
                "processing_by = NULL, processing_time = NULL WHERE status = 'processing' AND processing_time < ?";
        try {
            db.update(sql, expireTime);
        } catch (SQLException e) {
            plugin.getLogger().severe("清理过期处理失败 -> " + e.getMessage());
        }
    }

    public int resetAllProcessing() {
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'pending', " +
                "processing_by = NULL, processing_time = NULL WHERE status = 'processing'";
        try {
            return db.update(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("重置处理中状态失败 -> " + e.getMessage());
            return 0;
        }
    }

    public int resetProcessingByStaff(String staffUuid) {
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'pending', " +
                "processing_by = NULL, processing_time = NULL WHERE processing_by = ?";
        try {
            return db.update(sql, staffUuid);
        } catch (SQLException e) {
            plugin.getLogger().severe("重置志愿者处理状态失败 -> " + e.getMessage());
            return 0;
        }
    }

    public int resetAllReports() {
        String sql = "UPDATE " + db.getTablePrefix() + "reports SET status = 'pending', " +
                "processing_by = NULL, processing_time = NULL, processed_by = NULL, " +
                "processed_time = NULL, processed_reason_id = NULL";
        try {
            return db.update(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("重置所有举报失败 -> " + e.getMessage());
            return 0;
        }
    }

    public int getPlayerPage(UUID uuid) {return playerPages.getOrDefault(uuid, 1);}
    public int getStaffPage(UUID uuid) {return staffPages.getOrDefault(uuid, 1);}
    public ReportRecord getSelectedReport(UUID uuid) {return selectedReports.get(uuid);}

    public void setPlayerPage(UUID uuid, int page) {playerPages.put(uuid, page);}
    public void setStaffPage(UUID uuid, int page) {staffPages.put(uuid, page);}
    public void setSelectedReport(UUID uuid, ReportRecord record) {selectedReports.put(uuid, record);}
    public void removeSelectedReport(UUID uuid) {selectedReports.remove(uuid);}
    
    public int fullResetAllData() {
        int totalCount = 0;
        try {
            String reportsSql = "DELETE FROM " + db.getTablePrefix() + "reports";
            int reportsCount = db.update(reportsSql);
            totalCount += reportsCount;
            plugin.getLogger().info("已清空 " + reportsCount + " 条举报记录");

            String playersSql = "DELETE FROM " + db.getTablePrefix() + "players";
            int playersCount = db.update(playersSql);
            totalCount += playersCount;
            plugin.getLogger().info("已清空 " + playersCount + " 条玩家数据");

            String logsSql = "DELETE FROM " + db.getTablePrefix() + "logs";
            int logsCount = db.update(logsSql);
            totalCount += logsCount;
            plugin.getLogger().info("已清空 " + logsCount + " 条操作日志");

            try {
                db.update("ALTER TABLE " + db.getTablePrefix() + "reports AUTO_INCREMENT = 1");
                db.update("ALTER TABLE " + db.getTablePrefix() + "players AUTO_INCREMENT = 1");
                db.update("ALTER TABLE " + db.getTablePrefix() + "logs AUTO_INCREMENT = 1");
            } catch (SQLException e) {}
            cooldowns.clear();
            playerPages.clear();
            staffPages.clear();
            selectedReports.clear();
            processingReports.clear();
        } catch (SQLException e) {
            plugin.getLogger().severe("full reset失败 -> " + e.getMessage());
            e.printStackTrace();
        }
        
        return totalCount;
    }
}
