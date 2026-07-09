package com.aermini.report.database;

import com.aermini.report.AerReport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseManager {
    private final AerReport plugin;
    private HikariDataSource dataSource;
    private String tablePrefix;

    public DatabaseManager(AerReport plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        String address = plugin.getConfigManager().getConfig().getString("database.address", "127.0.0.1");
        int port = plugin.getConfigManager().getConfig().getInt("database.port", 3306);
        String username = plugin.getConfigManager().getConfig().getString("database.username", "root");
        String password = plugin.getConfigManager().getConfig().getString("database.password", "");
        String database = plugin.getConfigManager().getConfig().getString("database.database", "minecraft");
        this.tablePrefix = plugin.getConfigManager().getConfig().getString("database.table_prefix", "aerreport_");
        HikariConfig config = new HikariConfig();
        StringBuilder jdbcUrl = new StringBuilder();
        jdbcUrl.append("jdbc:mysql://").append(address).append(":").append(port).append("/").append(database);
        jdbcUrl.append("?useSSL=false");
        jdbcUrl.append("&useUnicode=true");
        jdbcUrl.append("&characterEncoding=UTF-8");
        jdbcUrl.append("&autoReconnect=true");
        jdbcUrl.append("&failOverReadOnly=false");
        jdbcUrl.append("&maxReconnects=10");
        jdbcUrl.append("&useServerPrepStmts=false");
        jdbcUrl.append("&cachePrepStmts=true");
        jdbcUrl.append("&prepStmtCacheSize=250");
        jdbcUrl.append("&prepStmtCacheSqlLimit=2048");
        jdbcUrl.append("&serverTimezone=Asia/Shanghai");
        jdbcUrl.append("&allowPublicKeyRetrieval=true");
        jdbcUrl.append("&socketTimeout=10000");
        jdbcUrl.append("&connectTimeout=10000");
        config.setJdbcUrl(jdbcUrl.toString());
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.aermini.report.lib.mysql.cj.jdbc.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("AerReport-Pool");
        config.setConnectionTestQuery("SELECT 1");
        try {
            dataSource = new HikariDataSource(config);
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("数据库连接池初始化失败 -> " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTables() {
        // 举报记录表
        String createReportsTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "reports (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "server VARCHAR(64) NOT NULL, " +
                "reporter VARCHAR(36) NOT NULL, " +
                "reporter_name VARCHAR(64) NOT NULL, " +
                "reported VARCHAR(36) NOT NULL, " +
                "reported_name VARCHAR(64) NOT NULL, " +
                "reason_id VARCHAR(64) NOT NULL, " +
                "reason_name VARCHAR(64) NOT NULL, " +
                "report_type VARCHAR(32) NOT NULL, " +
                "report_time BIGINT NOT NULL, " +
                "report_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "server_x DOUBLE, " +
                "server_y DOUBLE, " +
                "server_z DOUBLE, " +
                "server_world VARCHAR(64), " +
                "reported_x DOUBLE, " +
                "reported_y DOUBLE, " +
                "reported_z DOUBLE, " +
                "reported_world VARCHAR(64), " +
                "chat_messages TEXT, " +
                "status ENUM('pending', 'processing', 'processed', 'fake') DEFAULT 'pending', " +
                "processing_by VARCHAR(36), " +
                "processing_time BIGINT, " +
                "processed_by VARCHAR(36), " +
                "processed_by_name VARCHAR(64), " +
                "processed_time BIGINT, " +
                "processed_reason_id VARCHAR(64), " +
                "processed_reason_name VARCHAR(64), " +
                "ban_id VARCHAR(64), " +
                "linked_report_id INT DEFAULT NULL, " +
                "INDEX idx_reporter (reporter), " +
                "INDEX idx_reported (reported), " +
                "INDEX idx_status (status), " +
                "INDEX idx_time (report_time), " +
                "INDEX idx_processing_by (processing_by)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 玩家信息表
        String createPlayersTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "players (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "name VARCHAR(64) NOT NULL, " +
                "report_count INT DEFAULT 0, " +
                "reported_count INT DEFAULT 0, " +
                "current_server VARCHAR(64), " +
                "last_online BIGINT, " +
                "is_online BOOLEAN DEFAULT FALSE, " +
                "is_banned BOOLEAN DEFAULT FALSE, " +
                "INDEX idx_name (name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 聊天记录表
        String createChatTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "chat (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "uuid VARCHAR(36) NOT NULL, " +
                "name VARCHAR(64) NOT NULL, " +
                "server VARCHAR(64) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "time BIGINT NOT NULL, " +
                "INDEX idx_uuid (uuid), " +
                "INDEX idx_time (time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 传送请求表
        String createTeleportTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "teleport (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "staff_uuid VARCHAR(36) NOT NULL, " +
                "target_uuid VARCHAR(36) NOT NULL, " +
                "target_name VARCHAR(64), " +
                "target_server VARCHAR(64) NOT NULL, " +
                "target_x DOUBLE, " +
                "target_y DOUBLE, " +
                "target_z DOUBLE, " +
                "target_world VARCHAR(64), " +
                "teleport_type ENUM('report_time', 'current'), " +
                "created_time BIGINT NOT NULL, " +
                "processed BOOLEAN DEFAULT FALSE, " +
                "INDEX idx_staff (staff_uuid), " +
                "INDEX idx_processed (processed)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 操作日志表
        String createLogsTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "report_id INT, " +
                "staff_uuid VARCHAR(36) NOT NULL, " +
                "staff_name VARCHAR(64) NOT NULL, " +
                "action VARCHAR(64) NOT NULL, " +
                "reporter_uuid VARCHAR(36), " +
                "reported_uuid VARCHAR(36), " +
                "reason_id VARCHAR(64), " +
                "linked_report_id INT, " +
                "time BIGINT NOT NULL, " +
                "INDEX idx_report (report_id), " +
                "INDEX idx_staff (staff_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        // 志愿者状态表
        String createVolunteerStateTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "volunteer_state (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "player_uuid VARCHAR(36) NOT NULL UNIQUE, " +
                "player_name VARCHAR(64) NOT NULL, " +
                "original_server VARCHAR(64), " +
                "pending_exit BOOLEAN DEFAULT FALSE, " +
                "created_time BIGINT NOT NULL, " +
                "INDEX idx_player (player_uuid)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createReportsTable);
            stmt.execute(createPlayersTable);
            stmt.execute(createChatTable);
            stmt.execute(createTeleportTable);
            stmt.execute(createLogsTable);
            stmt.execute(createVolunteerStateTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("创建数据表失败 -> " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) throw new SQLException("数据源未初始化或已关闭");
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("数据库连接池已关闭");
        }
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public ResultSet query(String sql, Object... params) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
        return stmt.executeQuery();
    }

    public int update(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            return stmt.executeUpdate();
        }
    }

    public int insertAndGetId(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
            return -1;
        }
    }

    //志愿者相关

    public void saveVolunteerState(UUID playerUuid, String playerName, String originalServer) {
        String sql = "INSERT INTO " + tablePrefix + "volunteer_state (player_uuid, player_name, original_server, created_time) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "player_name = ?, original_server = ?, pending_exit = FALSE, created_time = ?";
        long now = System.currentTimeMillis();
        try {
            update(sql, playerUuid.toString(), playerName, originalServer, now,
                    playerName, originalServer, now);
        } catch (SQLException e) {
            plugin.getLogger().severe("保存志愿者状态失败 -> " + e.getMessage());
        }
    }

    public String getVolunteerOriginalServer(UUID playerUuid) {
        String sql = "SELECT original_server FROM " + tablePrefix + "volunteer_state WHERE player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getString("original_server");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取志愿者原始服务器失败 -> " + e.getMessage());
        }
        return null;
    }

    public void setVolunteerPendingExit(UUID playerUuid, boolean pending) {
        String sql = "UPDATE " + tablePrefix + "volunteer_state SET pending_exit = ? WHERE player_uuid = ?";
        try {
            update(sql, pending, playerUuid.toString());
        } catch (SQLException e) {
            plugin.getLogger().severe("设置志愿者待退出状态失败 -> " + e.getMessage());
        }
    }

    public boolean isVolunteerPendingExit(UUID playerUuid) {
        String sql = "SELECT pending_exit FROM " + tablePrefix + "volunteer_state WHERE player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getBoolean("pending_exit");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("检查志愿者待退出状态失败 -> " + e.getMessage());
        }
        return false;
    }
    
    public void removeVolunteerState(UUID playerUuid) {
        String sql = "DELETE FROM " + tablePrefix + "volunteer_state WHERE player_uuid = ?";
        try {
            update(sql, playerUuid.toString());
        } catch (SQLException e) {
            plugin.getLogger().severe("删除志愿者状态失败 -> " + e.getMessage());
        }
    }
}
