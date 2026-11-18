package com.aermini.report.utils;

import java.util.UUID;

public final class RedisKeys {
    private static final String DEFAULT_PREFIX = "aerreport:";

    private static volatile String prefix = DEFAULT_PREFIX;

    private RedisKeys() {
    }

    public static void setPrefix(String value) {
        if (value == null || value.trim().isEmpty()) {
            prefix = DEFAULT_PREFIX;
            return;
        }
        prefix = value;
    }

    public static String cool(UUID uuid) {
        return prefix + "cool:" + uuid;
    }

    public static String online(UUID uuid) {
        return prefix + "online:" + uuid;
    }

    public static String player(UUID uuid) {
        return prefix + "player:" + uuid;
    }

    public static String report(int reportId) {
        return prefix + "report:" + reportId;
    }

    public static String pendingCount() {
        return prefix + "pending:count";
    }

    public static String panelVersion() {
        return prefix + "panel:version";
    }

    public static String panel(String type, int version, int page) {
        return prefix + "panel:" + type + ":" + version + ":" + page;
    }

    public static String dedup(String reporter, String reported, String reason) {
        return prefix + "dedup:" + reporter + ":" + reported + ":" + reason;
    }

    public static String eventsChannel() {
        return prefix + "events";
    }
}
