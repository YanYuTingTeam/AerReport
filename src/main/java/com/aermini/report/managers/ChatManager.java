package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.aermini.report.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatManager {
    private final AerReport plugin;
    private final DatabaseManager db;
    private final ConcurrentHashMap<UUID, List<String>> recentMessages;
    private final ExecutorService asyncDbPool;
    private static final int MAX_MESSAGES = 5;

    public ChatManager(AerReport plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
        this.recentMessages = new ConcurrentHashMap<>();
        this.asyncDbPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "AerReport-ChatDB");
            t.setDaemon(true);
            return t;
        });
    }

    public void recordMessage(UUID uuid, String name, String message) {
        List<String> messages = recentMessages.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());
        messages.add(message);
        if (messages.size() > MAX_MESSAGES) messages.remove(0); // 保留最近n条. 在config中配置
        asyncDbPool.execute(() -> {
            long time = System.currentTimeMillis();
            String server = plugin.getServerName();
            String sql = "INSERT INTO " + db.getTablePrefix() + "chat " +
                    "(uuid, name, server, message, time) VALUES (?, ?, ?, ?, ?)";
            try {
                db.update(sql, uuid.toString(), name, server, message, time);
            } catch (SQLException e) {
                plugin.getLogger().severe("记录聊天失败 -> " + e.getMessage());
            }
        });
    }

    public String getRecentMessages(UUID uuid) {
        List<String> messages = recentMessages.get(uuid);
        if (messages == null || messages.isEmpty()) messages=getRecentMessagesFromDB(uuid);
        if (messages.isEmpty()) return "";
        return String.join("\n", messages);
    }

    private List<String> getRecentMessagesFromDB(UUID uuid) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT message FROM " + db.getTablePrefix() + "chat " +
                "WHERE uuid = ? ORDER BY time DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, MAX_MESSAGES);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(rs.getString("message"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("获取聊天失败 -> " + e.getMessage());
        }
        return messages;
    }

    public void cleanupOldMessages() {
        // 消息只留7天
        long expireTime = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
        String sql = "DELETE FROM " + db.getTablePrefix() + "chat WHERE time < ?";
        try {
            db.update(sql, expireTime);
        } catch (SQLException e) {
            plugin.getLogger().severe("清理聊天失败 -> " + e.getMessage());
        }
    }
}
