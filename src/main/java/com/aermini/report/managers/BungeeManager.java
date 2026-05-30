package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

// 读书人不叫抄tigerreport代码 叫参考 叫借鉴
public class BungeeManager implements PluginMessageListener {
    private final AerReport plugin;
    private String localServerName = null;
    private boolean initialized = false;
    private List<Consumer<String>> serverNameCallbacks = new ArrayList<>();
    private Set<String> knownServers = new HashSet<>(); // 已知服务器列表 通知发到这些子服
    private boolean serversCollected = false;
    public static final String SUB_CHANNEL = "AerReport";
    public static final String MSG_REPORT_ALERT = "REPORT_ALERT";
    private List<BiConsumer<String, String[]>> messageHandlers = new ArrayList<>(); // 跨服通知回调
    private List<byte[]> pendingNotifications = new ArrayList<>(); // 待发送的通知队列

    public BungeeManager(AerReport plugin) {
        this.plugin = plugin;
        init();
    }

    private void init() {
        try {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
            plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
            initialized = true;
            plugin.getLogger().info("BungeeCord通道已注册");
        } catch (Exception e) {
            plugin.getLogger().severe("无法注册BungeeCord通道 -> " + e.getMessage());
            initialized = false;
        }
    }

    // 玩家加入时尝试获取子服信息
    public void onPlayerJoin(Player player) {
        if (!initialized) return;
        if (localServerName == null) collectServerName();
        if (!serversCollected) collectServers();
    }

    public void collectServerName() {
        if (!initialized) {
            plugin.getLogger().warning("BungeeCord未初始化 at collect");
            return;
        }
        
        Player player = getRandomPlayer();
        if (player == null) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServer");
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    public void collectServers() {
        if (!initialized) return;
        Player player = getRandomPlayer();
        if (player == null) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GetServers");
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    private Player getRandomPlayer() {
        for (Player p : Bukkit.getOnlinePlayers()) return p;
        return null;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            switch (subchannel) {
                case "GetServer":
                    localServerName = in.readUTF();
                    plugin.getLogger().info("获取到服务器名称: " + localServerName);
                    for (Consumer<String> callback : serverNameCallbacks) {
                        callback.accept(localServerName); // 回调
                    }
                    serverNameCallbacks.clear();
                    updateAllPlayersServer();
                    break;
                case "GetServers":
                    String serversStr = in.readUTF();
                    if (serversStr != null && !serversStr.isEmpty()) {
                        String[] servers = serversStr.split(", ");
                        knownServers.clear();
                        for (String server : servers) {
                            if (server != null && !server.isEmpty()) {
                                knownServers.add(server);
                            }
                        }
                        serversCollected = true;
                        plugin.getLogger().info("获取到服务器列表: " + knownServers);
                        sendPendingNotifications();
                    }
                    break;
                case SUB_CHANNEL:
                    // 收到消息
                    handlePluginNotification(in);
                    break;
                    
                default:
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("处理消息失败 -> " + e.getMessage());
        }
    }
    
    private void updateAllPlayersServer() {
        String serverName = localServerName != null ? localServerName : "unknown";
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.getReportManager().updatePlayerInfo(
                    p.getUniqueId(),
                    p.getName(),
                    serverName,
                    true
            );
        }
    }
    
    private void handlePluginNotification(ByteArrayDataInput in) {
        try {
            short dataLength = in.readShort();
            byte[] data = new byte[dataLength];
            in.readFully(data);
            
            try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
                String msgType = dis.readUTF();
                if (msgType.equals(MSG_REPORT_ALERT)) {
                    long notifyTime = dis.readLong();
                    String reportedName = dis.readUTF();
                    String reason = dis.readUTF();
                    String server = dis.readUTF();
                    String reportId = dis.readUTF();
                    plugin.getLogger().info("[调试] 收到跨服举报通知: reported=" + reportedName + ", reason=" + reason + ", server=" + server + ", reportId=" + reportId);
                    // reportedName, reason, server, reportId, notifyTime
                    for (BiConsumer<String, String[]> handler : messageHandlers) {
                        handler.accept(msgType, new String[]{reportedName, reason, server, reportId, String.valueOf(notifyTime)});
                    }
                } else if (msgType.equals("GetPlayerServer")) {
                    String playerName = dis.readUTF();
                    Player target = Bukkit.getPlayerExact(playerName);
                    if (target != null && target.isOnline()) sendPlayerServerResponse(playerName, localServerName); // 响应
                }
            } catch (IOException e) {
                plugin.getLogger().severe("解析消息失败 -> " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("处理消息失败 -> " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendPlayerServerResponse(String playerName, String server) {
        Player sender = getRandomPlayer();
        if (sender == null) return;
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF(SUB_CHANNEL);
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(byteOut);
            dataOut.writeUTF("PlayerServer");
            dataOut.writeUTF(playerName);
            dataOut.writeUTF(server);
            
            byte[] msgData = byteOut.toByteArray();
            out.writeShort(msgData.length);
            out.write(msgData);
            
            sender.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("发送响应失败 -> " + e.getMessage());
        }
    }

    public String getServerName() {
        return localServerName!=null? localServerName:"unknown";
    }

    public void getServerName(Consumer<String> callback) {
        if (localServerName != null) {
            callback.accept(localServerName);
        } else {
            serverNameCallbacks.add(callback);
            collectServerName();
        }
    }

    public void broadcastReportAlert(String reportedName, String reason, String server, int reportId) {
        if (!initialized) return;
        Player sender = getRandomPlayer();
        if (sender == null) return;
        long notifyTime = System.currentTimeMillis();
        byte[] notificationData = buildNotificationData(MSG_REPORT_ALERT, notifyTime, 
                reportedName, reason, server, String.valueOf(reportId));
        if (notificationData == null) return;
        
        if (serversCollected && !knownServers.isEmpty()) {
            for (String targetServer : knownServers) {
                if (targetServer.equals(localServerName)) continue; // 跳过本服
                sendNotificationToServer(sender, targetServer, notificationData);
            }
        } else {
            pendingNotifications.add(notificationData);
            collectServers();
        }
    }
    
    private byte[] buildNotificationData(String msgType, long notifyTime, String... args) {
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(byteOut);

            dataOut.writeUTF(msgType);
            dataOut.writeLong(notifyTime);
            for (String arg : args) {
                dataOut.writeUTF(arg);
            }
            
            return byteOut.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().severe("构建数据失败 -> " + e.getMessage());
            return null;
        }
    }
    
    private void sendNotificationToServer(Player sender, String targetServer, byte[] data) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Forward");
            out.writeUTF(targetServer);
            out.writeUTF(SUB_CHANNEL);
            out.writeShort(data.length);
            out.write(data);
            
            sender.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("发送通知到服务器 " + targetServer + " 失败 -> " + e.getMessage());
        }
    }
    
    private void sendPendingNotifications() {
        if (pendingNotifications.isEmpty()) return;
        Player sender = getRandomPlayer();
        if (sender == null) return;
        plugin.getLogger().info("[调试] 发送 " + pendingNotifications.size() + " 条待发送通知...");
        for (byte[] data : pendingNotifications) {
            for (String targetServer : knownServers) {
                if (targetServer.equals(localServerName)) continue; // 跳过本服
                sendNotificationToServer(sender, targetServer, data);
            }
        }
        pendingNotifications.clear();
    }

    public void sendToServer(Player player, String server) {
        if (!initialized) return;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    public void registerMessageHandler(BiConsumer<String, String[]> handler) {
        messageHandlers.add(handler);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void destroy() {
        if (initialized) {
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, "BungeeCord");
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, "BungeeCord", this);
            initialized = false;
        }
    }
}
