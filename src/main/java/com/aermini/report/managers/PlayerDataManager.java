package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "aermonitor:main");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "aermonitor:main",
                (channel, player, message) -> {
                    if (!"aermonitor:main".equals(channel)) return;
                    try {
                        DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
                        String jsonStr = in.readUTF();
                        JsonObject resp = new JsonParser().parse(jsonStr).getAsJsonObject();

                        if (resp.has("uuid") && resp.has("server")) {
                            String uuidStr = resp.get("uuid").getAsString();
                            UUID uuid = UUID.fromString(uuidStr);
                            JsonObject server = resp.getAsJsonObject("server");
                            String serverName = server.has("name") && !server.get("name").isJsonNull()
                                    ? server.get("name").getAsString() : null;
                            Consumer<String> callback = serverCallbacks.remove(uuid);
                            if (callback != null) {
                                callback.accept(serverName);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("处理aermonitor:main响应失败 -> " + e.getMessage());
                    }
                });
    }

    public void getPlayerServer(UUID playerUuid, Consumer<String> callback) {
        serverCallbacks.put(playerUuid, callback);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            JsonObject req = new JsonObject();
            req.addProperty("event", "getPlayerInfo");
            req.addProperty("uuid", playerUuid.toString());
            out.writeUTF(req.toString());

            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Player sender = Bukkit.getOnlinePlayers().iterator().next();
                sender.sendPluginMessage(plugin, "aermonitor:main", b.toByteArray());
            } else {
                serverCallbacks.remove(playerUuid);
                callback.accept(null);
                return;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("发送aermonitor:main查询失败 -> " + e.getMessage());
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

    public void notifyStaff(String permission, String message) {
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);

            JsonObject req = new JsonObject();
            req.addProperty("event", "notifyStaff");
            req.addProperty("permission", permission);
            req.addProperty("message", message);
            out.writeUTF(req.toString());

            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                Player sender = Bukkit.getOnlinePlayers().iterator().next();
                sender.sendPluginMessage(plugin, "aermonitor:main", b.toByteArray());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("发送notifyStaff失败 -> " + e.getMessage());
        }
    }
}
