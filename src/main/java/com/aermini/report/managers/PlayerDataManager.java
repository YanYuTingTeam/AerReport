package com.aermini.report.managers;

import com.aermini.report.AerReport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PlayerDataManager {
    private final AerReport plugin;
    private Map<String, Long> playerJoinTimes = new ConcurrentHashMap<>();
    private Map<UUID, Consumer<String>> serverCallbacks = new ConcurrentHashMap<>();

    public PlayerDataManager(AerReport plugin) {
        this.plugin = plugin;
        setupChannel();
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

    private void setupChannel() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "AerServerPulse");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "AerServerPulse",
                (channel, player, message) -> {
                    if (!"AerServerPulse".equals(channel)) return;
                    try {
                        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
                        String action = in.readUTF();
                        if ("resp".equals(action)) {
                            String uuidStr = in.readUTF();
                            String server = in.readUTF();
                            UUID uuid = UUID.fromString(uuidStr);
                            Consumer<String> callback = serverCallbacks.remove(uuid);
                            if (callback != null) {
                                callback.accept("null".equals(server) ? null : server);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("处理AerServerPulse响应失败 -> " + e.getMessage());
                    }
                });
    }

    public void getPlayerServer(UUID playerUuid, Consumer<String> callback) {
        serverCallbacks.put(playerUuid, callback);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF("query");
            out.writeUTF(playerUuid.toString());
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Player sender = Bukkit.getOnlinePlayers().iterator().next();
                sender.sendPluginMessage(plugin, "AerServerPulse", b.toByteArray());
            } else {
                serverCallbacks.remove(playerUuid);
                callback.accept(null);
                return;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("发送AerServerPulse查询失败 -> " + e.getMessage());
            serverCallbacks.remove(playerUuid);
            callback.accept(null);
            return;
        }
        // timeout 200ticks(10s)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            Consumer<String> cb = serverCallbacks.remove(playerUuid);
            if (cb != null) cb.accept(null);
        }, 200L);
    }
}
