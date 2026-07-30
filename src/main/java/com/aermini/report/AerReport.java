package com.aermini.report;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.aermini.report.commands.AerReportCommand;
import com.aermini.report.commands.ReportCommand;
import com.aermini.report.commands.VolunCommand;
import com.aermini.report.commands.VolunteerCommand;
import com.aermini.report.commands.VolunteerNCommand;
import com.aermini.report.database.DatabaseManager;
import com.aermini.report.gui.GUIManager;
import com.aermini.report.listeners.ChatListener;
import com.aermini.report.listeners.GUIListener;
import com.aermini.report.managers.BungeeManager;
import com.aermini.report.managers.ChatManager;
import com.aermini.report.managers.ConfigManager;
import com.aermini.report.managers.PlayerDataManager;
import com.aermini.report.managers.ReportManager;
import com.aermini.report.managers.TeleportManager;
import com.aermini.report.managers.VolunteerManager;

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

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
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
        if (databaseManager != null) databaseManager.close();
        if (bungeeManager != null) bungeeManager.destroy();
        getLogger().info("AerReport 已禁用");
    }

    private void registerCommands() {
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("volunteer").setExecutor(new VolunteerCommand(this));
        getCommand("volunteern").setExecutor(new VolunteerNCommand(this));
        getCommand("volun").setExecutor(new VolunCommand(this));
        getCommand("aerreport").setExecutor(new AerReportCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
    }

    private void startTasks() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            teleportManager.cleanupExpiredRequests();
            chatManager.cleanupOldMessages();
            reportManager.cleanupExpiredProcessing();
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
}
