package com.aermini.report;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.aermini.report.commands.AerReportCommand;
import com.aermini.report.commands.ReportCommand;
import com.aermini.report.commands.VolunteerCommand;
import com.aermini.report.commands.VolunteerNCommand;
import com.aermini.report.database.DatabaseManager;
import com.aermini.report.database.RedisManager;
import com.aermini.report.database.RedisSubscriber;
import com.aermini.report.gui.GUIManager;
import com.aermini.report.listeners.ChatListener;
import com.aermini.report.listeners.GUIListener;
import com.aermini.report.listeners.VexViewKeyListener;
import com.aermini.report.managers.BungeeManager;
import com.aermini.report.managers.ChatManager;
import com.aermini.report.managers.ConfigManager;
import com.aermini.report.managers.PlayerDataManager;
import com.aermini.report.managers.ReportManager;
import com.aermini.report.managers.TeleportManager;
import com.aermini.report.managers.VolunteerManager;
import com.aermini.report.utils.RedisKeys;

import redis.clients.jedis.Pipeline;
import redis.clients.jedis.params.SetParams;

public class AerReport extends JavaPlugin {
    private static AerReport instance;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ReportManager reportManager;
    private GUIManager guiManager;
    private BungeeManager bungeeManager;
    private TeleportManager teleportManager;
    private ChatManager chatManager;
    private PlayerDataManager playerDataManager;
    private VolunteerManager volunteerManager;
    private RedisManager redisManager;
    private RedisSubscriber redisSubscriber;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        redisManager = new RedisManager(this);
        redisManager.initialize();
        redisSubscriber = new RedisSubscriber(this, redisManager);
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        reportManager = new ReportManager(this);
        guiManager = new GUIManager(this);
        bungeeManager = new BungeeManager(this);
        teleportManager = new TeleportManager(this);
        chatManager = new ChatManager(this);
        playerDataManager = new PlayerDataManager(this);
        volunteerManager = new VolunteerManager(this);

        registerCommands();
        registerListeners();
        startTasks();
        getLogger().info("========================================");
        getLogger().info("  AerReport 玩家举报系统 v1.0.0");
        getLogger().info("  作者: AerMini");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (volunteerManager != null) volunteerManager.removeAllPatrolMetadata();
        if (chatManager != null) chatManager.close();
        if (redisSubscriber != null) redisSubscriber.close();
        if (redisManager != null) redisManager.close();
        if (databaseManager != null) databaseManager.close();
        if (bungeeManager != null) bungeeManager.destroy();
        getLogger().info("AerReport 已禁用");
    }

    private void registerCommands() {
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("vt").setExecutor(new VolunteerCommand(this));
        getCommand("vtn").setExecutor(new VolunteerNCommand(this));
        getCommand("aerreport").setExecutor(new AerReportCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        if (getServer().getPluginManager().getPlugin("VexView") != null) {
            getServer().getPluginManager().registerEvents(new VexViewKeyListener(this), this);
            getLogger().info("检测到 VexView, 已注册 P 键快捷打开面板");
        }
    }

    private void startTasks() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            teleportManager.cleanupExpiredRequests();
            chatManager.cleanupOldMessages();
        }, 72000L, 72000L);

        // 提醒待处理举报
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            int pending = reportManager.getPendingReportCount();
            if (pending > 0) {
                String message = configManager.getMessage("notification")
                        .replace("{waiting}", String.valueOf(pending));
                net.md_5.bungee.api.chat.TextComponent clickableMessage =
                        new net.md_5.bungee.api.chat.TextComponent(message);
                clickableMessage.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/volunteer"));
                clickableMessage.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                        net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.TextComponent[]{
                                new net.md_5.bungee.api.chat.TextComponent("§7打开志愿者面板")}));
                getServer().getOnlinePlayers().stream()
                        .filter(p -> p.hasPermission("aerreport.staff.main"))
                        .forEach(p -> p.spigot().sendMessage(clickableMessage));
            }
        }, 6000L, 6000L);
        // 获取子服名
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bungeeManager.getServerName().equals("unknown")) {
                    getLogger().info("已获取到服务器名称: " + bungeeManager.getServerName() + "，停止周期检查");
                    cancel();
                    return;
                }
                if (bungeeManager.isInitialized()) {
                    bungeeManager.collectServerName();
                }
            }
        }.runTaskTimer(this, 60L, 60L);

        // Redis 在线状态心跳
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            try {
                Bukkit.getScheduler().runTask(this, () -> {
                    final List<Player> players = new ArrayList<>(getServer().getOnlinePlayers());
                    if (players.isEmpty()) return;
                    String rawServer = getServerName();
                    final String serverName = (rawServer == null || rawServer.trim().isEmpty()) ? "unknown" : rawServer;
                    Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                        redisManager.run(j -> {
                            Pipeline p = j.pipelined();
                            for (Player p0 : players) {
                                p.set(RedisKeys.online(p0.getUniqueId()), serverName, SetParams.setParams().ex(90));
                            }
                            p.sync();
                        });
                    });
                });
            } catch (Exception e) {
                getLogger().warning("Redis 在线状态心跳失败 -> " + e.getMessage());
            }
        }, 600L, 600L);
    }

    public static AerReport getInstance() {
        return instance;
    }

    public String getServerName() {
        return bungeeManager.getServerName();
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ReportManager getReportManager() { return reportManager; }
    public GUIManager getGUIManager() { return guiManager; }
    public BungeeManager getBungeeManager() { return bungeeManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public ChatManager getChatManager() { return chatManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public VolunteerManager getVolunteerManager() { return volunteerManager; }
    public RedisManager getRedisManager() { return redisManager; }
    public RedisSubscriber getRedisSubscriber() { return redisSubscriber; }

    public boolean isPatrolling(java.util.UUID playerUuid) {
        return volunteerManager.isPatrolling(playerUuid);
    }
}
