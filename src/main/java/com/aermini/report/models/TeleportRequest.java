package com.aermini.report.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 传送请求数据模型
 */
public class TeleportRequest {

    private int id;
    private String staffUuid;
    private String targetUuid;
    private String targetName; // 目标玩家名
    private String targetServer;
    private double targetX;
    private double targetY;
    private double targetZ;
    private String targetWorld;
    private String teleportType; // report_time, current
    private long createdTime;
    private boolean processed;

    public TeleportRequest() {}

    /**
     * 从ResultSet构建
     */
    public static TeleportRequest fromResultSet(ResultSet rs) throws SQLException {
        TeleportRequest request = new TeleportRequest();
        request.setId(rs.getInt("id"));
        request.setStaffUuid(rs.getString("staff_uuid"));
        request.setTargetUuid(rs.getString("target_uuid"));
        request.setTargetName(rs.getString("target_name")); // 新增
        request.setTargetServer(rs.getString("target_server"));
        request.setTargetX(rs.getDouble("target_x"));
        request.setTargetY(rs.getDouble("target_y"));
        request.setTargetZ(rs.getDouble("target_z"));
        request.setTargetWorld(rs.getString("target_world"));
        request.setTeleportType(rs.getString("teleport_type"));
        request.setCreatedTime(rs.getLong("created_time"));
        request.setProcessed(rs.getBoolean("processed"));
        return request;
    }

    public int getId() {return id;}
    public String getStaffUuid() {return staffUuid;}
    public String getTargetUuid() {return targetUuid;}
    public String getTargetName() {return targetName;}
    public String getTargetServer() {return targetServer;}
    public double getTargetX() {return targetX;}
    public double getTargetY() {return targetY;}
    public double getTargetZ() {return targetZ;}
    public String getTargetWorld() {return targetWorld;}
    public String getTeleportType() {return teleportType;}
    public long getCreatedTime() {return createdTime;}
    public boolean isProcessed() {return processed;}

    public void setId(int id) {this.id=id;}
    public void setStaffUuid(String staffUuid) {this.staffUuid=staffUuid;}
    public void setTargetUuid(String targetUuid) {this.targetUuid=targetUuid;}
    public void setTargetName(String targetName) {this.targetName=targetName;}
    public void setTargetServer(String targetServer) {this.targetServer=targetServer;}
    public void setTargetX(double targetX) {this.targetX=targetX;}
    public void setTargetY(double targetY) {this.targetY=targetY;}
    public void setTargetZ(double targetZ) {this.targetZ=targetZ;}
    public void setTargetWorld(String targetWorld) {this.targetWorld=targetWorld;}
    public void setTeleportType(String teleportType) {this.teleportType=teleportType;}
    public void setCreatedTime(long createdTime) {this.createdTime=createdTime;}
    public void setProcessed(boolean processed) {this.processed=processed;}
}
