package com.aermini.report.managers;

import com.aermini.report.AerReport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PlayerDataManager {
    private final AerReport plugin;
    private Map<String, Long> playerJoinTimes = new ConcurrentHashMap<>();

    public PlayerDataManager(AerReport plugin) {
        this.plugin = plugin;
        setupForwarding();
    }
    
    public void recordPlayerJoin(String playerName) {
        playerJoinTimes.put(playerName, System.currentTimeMillis());
    }
    
    public long getPlayerJoinTime(String playerName) {
        return playerJoinTimes.getOrDefault(playerName, 0L);
    }
    
    public void clearPlayerJoinTime(String playerName) {
        playerJoinTimes.remove(playerName);
    }

    private void setupForwarding() {
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord",
                (channel, player, message) -> {
                    if (!channel.equals("BungeeCord")) return;
                    try {
                        java.io.ByteArrayInputStream byteIn = new java.io.ByteArrayInputStream(message);
                        DataInputStream in = new DataInputStream(byteIn);
                        String subchannel = in.readUTF();
                        if (subchannel.equals("AerReport")) {
                            // subchannel + dataLength + data
                            short dataLength = in.readShort();
                            byte[] data = new byte[dataLength];
                            in.readFully(data);
                            try (DataInputStream dataIn = new DataInputStream(new java.io.ByteArrayInputStream(data))) {
                                String action = dataIn.readUTF();
                                if (action.equals("PlayerServer")) {
                                    String playerName = dataIn.readUTF();
                                    String server = dataIn.readUTF();
                                    handlePlayerServerResponse(playerName, server);
                                }
                            } catch (IOException e) {
                                plugin.getLogger().severe("解析消息失败 -> " + e.getMessage());
                            }
                        }
                    } catch (IOException e) {
                        plugin.getLogger().severe("处理消息失败 -> " + e.getMessage());
                    }
                });
    }

    private java.util.Map<String, Consumer<String>> callbacks = new java.util.concurrent.ConcurrentHashMap<>();

    public void getPlayerServer(String playerName, Consumer<String> callback) {
        callbacks.put(playerName, callback);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        try {
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF("AerReport");
            
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("GetPlayerServer");
            msgOut.writeUTF(playerName);
            
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().severe("获取玩家服务器失败 -> " + e.getMessage());
            callback.accept(null);
            return;
        }
        
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            Player sender = Bukkit.getOnlinePlayers().iterator().next();
            sender.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } else {
            callback.accept(null);
        }
        // timeout
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (callbacks.containsKey(playerName)) {
                callbacks.remove(playerName);
                callback.accept(null);
            }
        }, 60L);
    }

    private void handlePlayerServerResponse(String playerName, String server) {
        Consumer<String> callback = callbacks.remove(playerName);
        if (callback != null)  callback.accept(server);
    }
}
