package com.aermini.report.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ReportRecord {
    private int id;
    private String server;
    private String reporterUuid;
    private String reporterName;
    private String reportedUuid;
    private String reportedName;
    private String reasonId;
    private String reasonName;
    private String reportType;
    private long reportTime;
    private double serverX;
    private double serverY;
    private double serverZ;
    private String serverWorld;
    private double reportedX;
    private double reportedY;
    private double reportedZ;
    private String reportedWorld;
    private String chatMessages;
    private String status;
    private String processingBy;
    private long processingTime;
    private String processedBy;
    private String processedByName;
    private long processedTime;
    private String processedReasonId;
    private String processedReasonName;
    private String banId;
    private Integer linkedReportId;
    private int reportCount; // 此前被举报次数
    private int reportCounts; // 总
    // 合并组字段: 同 reported+report_type 的 pending/processing 合并展示时填充
    private String mergedReporterNames;
    private String mergedReasonNames;
    private int mergedCount;
    private String mergedReportIds;

    public ReportRecord() {}
    public static ReportRecord fromResultSet(ResultSet rs) throws SQLException {
        ReportRecord record = new ReportRecord();
        record.setId(rs.getInt("id"));
        record.setServer(rs.getString("server"));
        record.setReporterUuid(rs.getString("reporter"));
        record.setReporterName(rs.getString("reporter_name"));
        record.setReportedUuid(rs.getString("reported"));
        record.setReportedName(rs.getString("reported_name"));
        record.setReasonId(rs.getString("reason_id"));
        record.setReasonName(rs.getString("reason_name"));
        record.setReportType(rs.getString("report_type"));
        record.setReportTime(rs.getLong("report_time"));
        record.setServerX(rs.getDouble("server_x"));
        record.setServerY(rs.getDouble("server_y"));
        record.setServerZ(rs.getDouble("server_z"));
        record.setServerWorld(rs.getString("server_world"));
        try {
            record.setReportedX(rs.getDouble("reported_x"));
            record.setReportedY(rs.getDouble("reported_y"));
            record.setReportedZ(rs.getDouble("reported_z"));
            record.setReportedWorld(rs.getString("reported_world"));
        } catch (SQLException e) {
            record.setReportedX(0);
            record.setReportedY(0);
            record.setReportedZ(0);
            record.setReportedWorld(null);
        }
        record.setChatMessages(rs.getString("chat_messages"));
        record.setStatus(rs.getString("status"));
        record.setProcessingBy(rs.getString("processing_by"));
        
        try {
            record.setProcessingTime(rs.getLong("processing_time"));
        } catch (SQLException e) {
            record.setProcessingTime(0);
        }
        
        record.setProcessedBy(rs.getString("processed_by"));
        
        try {
            record.setProcessedByName(rs.getString("processed_by_name"));
        } catch (SQLException e) {
            record.setProcessedByName(null);
        }
        
        try {
            record.setProcessedTime(rs.getLong("processed_time"));
        } catch (SQLException e) {
            record.setProcessedTime(0);
        }
        
        record.setProcessedReasonId(rs.getString("processed_reason_id"));
        
        try {
            record.setProcessedReasonName(rs.getString("processed_reason_name"));
        } catch (SQLException e) {
            record.setProcessedReasonName(null);
        }
        
        try {
            record.setBanId(rs.getString("ban_id"));
        } catch (SQLException e) {
            record.setBanId(null);
        }
        
        try {
            int linkedId = rs.getInt("linked_report_id");
            record.setLinkedReportId(rs.wasNull() ? null : linkedId);
        } catch (SQLException e) {
            record.setLinkedReportId(null);
        }
        
        return record;
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
        return sdf.format(new Date(reportTime));
    }

    public String getTimeWithSuffix() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(reportTime)) + ".0";
    }

    public String getFormattedProcessedTime() {
        if (processedTime == 0) {
            return "未知";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(processedTime));
    }

    public boolean isProcessing() {
        return "processing".equals(status);
    }

    public boolean isPending() {
        return "pending".equals(status);
    }

    public boolean isProcessed() {
        return "processed".equals(status);
    }

    public boolean isFake() {
        return "fake".equals(status);
    }

    public int getId() {return id;}
    public String getServer() {return server;}
    public String getReporterUuid() {return reporterUuid;}
    public String getReporterName() {return reporterName;}
    public String getReportedUuid() {return reportedUuid;}
    public String getReportedName() {return reportedName;}
    public String getReasonId() {return reasonId;}
    public String getReasonName() {return reasonName;}
    public String getReportType() {return reportType;}
    public long getReportTime() {return reportTime;}
    public double getServerX() {return serverX;}
    public double getServerY() {return serverY;}
    public double getServerZ() {return serverZ;}
    public String getServerWorld() {return serverWorld;}
    public double getReportedX() {return reportedX;}
    public double getReportedY() {return reportedY;}
    public double getReportedZ() {return reportedZ;}
    public String getReportedWorld() {return reportedWorld;}
    public String getChatMessages() {return chatMessages;}
    public String getStatus() {return status;}
    public String getProcessingBy() {return processingBy;}
    public long getProcessingTime() {return processingTime;}
    public String getProcessedBy() {return processedBy;}
    public String getProcessedByName() {return processedByName;}
    public long getProcessedTime() {return processedTime;}
    public String getProcessedReasonId() {return processedReasonId;}
    public String getProcessedReasonName() {return processedReasonName;}
    public String getBanId() {return banId;}
    public Integer getLinkedReportId() {return linkedReportId;}
    public int getReportCount() {return reportCount;}
    public int getReportCounts() {return reportCounts;}
    public String getMergedReporterNames() {return mergedReporterNames;}
    public String getMergedReasonNames() {return mergedReasonNames;}
    public int getMergedCount() {return mergedCount;}
    public String getMergedReportIds() {return mergedReportIds;}

    // 还要开记事本的救赎感
    public void setId(int id) {this.id=id;}
    public void setServer(String server) {this.server=server;}
    public void setReporterUuid(String reporterUuid) {this.reporterUuid=reporterUuid;}
    public void setReporterName(String reporterName) {this.reporterName=reporterName;}
    public void setReportedUuid(String reportedUuid) {this.reportedUuid=reportedUuid;}
    public void setReportedName(String reportedName) {this.reportedName=reportedName;}
    public void setReasonId(String reasonId) {this.reasonId=reasonId;}
    public void setReasonName(String reasonName) {this.reasonName=reasonName;}
    public void setReportType(String reportType) {this.reportType=reportType;}
    public void setReportTime(long reportTime) {this.reportTime=reportTime;}
    public void setServerX(double serverX) {this.serverX=serverX;}
    public void setServerY(double serverY) {this.serverY=serverY;}
    public void setServerZ(double serverZ) {this.serverZ=serverZ;}
    public void setServerWorld(String serverWorld) {this.serverWorld=serverWorld;}
    public void setReportedX(double reportedX) {this.reportedX=reportedX;}
    public void setReportedY(double reportedY) {this.reportedY=reportedY;}
    public void setReportedZ(double reportedZ) {this.reportedZ=reportedZ;}
    public void setReportedWorld(String reportedWorld) {this.reportedWorld=reportedWorld;}
    public void setChatMessages(String chatMessages) {this.chatMessages=chatMessages;}
    public void setStatus(String status) {this.status=status;}
    public void setProcessingBy(String processingBy) {this.processingBy=processingBy;}
    public void setProcessingTime(long processingTime) {this.processingTime=processingTime;}
    public void setProcessedBy(String processedBy) {this.processedBy=processedBy;}
    public void setProcessedByName(String processedByName) {this.processedByName=processedByName;}
    public void setProcessedTime(long processedTime) {this.processedTime=processedTime;}
    public void setProcessedReasonId(String processedReasonId) {this.processedReasonId=processedReasonId;}
    public void setProcessedReasonName(String processedReasonName) {this.processedReasonName=processedReasonName;}
    public void setBanId(String banId) {this.banId=banId;}
    public void setLinkedReportId(Integer linkedReportId) {this.linkedReportId=linkedReportId;}
    public void setReportCount(int reportCount) {this.reportCount=reportCount;}
    public void setReportCounts(int reportCounts) {this.reportCounts=reportCounts;}
    public void setMergedReporterNames(String v) {this.mergedReporterNames=v;}
    public void setMergedReasonNames(String v) {this.mergedReasonNames=v;}
    public void setMergedCount(int v) {this.mergedCount=v;}
    public void setMergedReportIds(String v) {this.mergedReportIds=v;}
}
