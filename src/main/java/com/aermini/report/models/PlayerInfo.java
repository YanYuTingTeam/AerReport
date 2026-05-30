package com.aermini.report.models;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.sql.ResultSet;
import java.sql.SQLException;

public class PlayerInfo {
    private String uuid;
    private String name;
    private int reportCount; // 举报次数
    private int reportedCount; // 被举报次数
    private String currentServer;
    private long lastOnline;
    private boolean isOnline;
    private boolean isBanned;

    public PlayerInfo() {}
    public static PlayerInfo fromResultSet(ResultSet rs) throws SQLException {
        PlayerInfo info = new PlayerInfo();
        info.setUuid(rs.getString("uuid"));
        info.setName(rs.getString("name"));
        info.setReportCount(rs.getInt("report_count"));
        info.setReportedCount(rs.getInt("reported_count"));
        info.setCurrentServer(rs.getString("current_server"));
        info.setLastOnline(rs.getLong("last_online"));
        info.setOnline(rs.getBoolean("is_online"));
        info.setBanned(rs.getBoolean("is_banned"));
        return info;
    }

    public String getStatusText() {
        if (isBanned) return "封禁";
        Player player = Bukkit.getPlayerExact(name);
        if (player != null && player.isOnline()) return "在线(本服)";
        if (isOnline) {
            if (currentServer != null && !currentServer.isEmpty()) return "在线(" + currentServer + ")";
            return "在线";
        }
        return "离线";
    }

    public String getUuid() {return uuid;}
    public String getName() {return name;}
    public int getReportCount() {return reportCount;}
    public int getReportedCount() {return reportedCount;}
    public String getCurrentServer() {return currentServer;}
    public long getLastOnline() {return lastOnline;}
    public boolean isOnline() {return isOnline;}
    public boolean isBanned() {return isBanned;}

    public void setUuid(String uuid) {this.uuid=uuid;}
    public void setName(String name) {this.name=name;}
    public void setReportCount(int reportCount) {this.reportCount=reportCount;}
    public void setReportedCount(int reportedCount) {this.reportedCount=reportedCount;}
    public void setCurrentServer(String currentServer) {this.currentServer=currentServer;}
    public void setLastOnline(long lastOnline) {this.lastOnline=lastOnline;}
    public void setOnline(boolean online) {isOnline=online;}
    public void setBanned(boolean banned) {isBanned=banned;}
}
