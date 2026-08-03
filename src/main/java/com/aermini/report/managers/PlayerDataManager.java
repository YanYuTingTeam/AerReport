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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PlayerDataManager {
    private final AerReport plugin;
    private Map<String, Long> playerJoinTimes = new ConcurrentHashMap<>();
    private Map<UUID, List<Consumer<String>>> serverCallbacks = new ConcurrentHashMap<>();

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
                            List<Consumer<String>> callbacks = serverCallbacks.remove(uuid);
                            if (callbacks != null) {
                                synchronized (callbacks) {
                                    for (Consumer<String> cb : callbacks) cb.accept(serverName);
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("处理aermonitor:main响应失败 -> " + e.getMessage());
                    }
                });
    }

    public void getPlayerServer(UUID playerUuid, Consumer<String> callback) {
        List<Consumer<String>> callbacks = serverCallbacks.computeIfAbsent(playerUuid,
                k -> Collections.synchronizedList(new ArrayList<>()));
        boolean needSend;
        synchronized (callbacks) {
            needSend = callbacks.isEmpty();
            callbacks.add(callback);
        }
        if (!needSend) return;
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
                List<Consumer<String>> cbs = serverCallbacks.remove(playerUuid);
                if (cbs != null) synchronized (cbs) { for (Consumer<String> cb : cbs) cb.accept(null); }
                return;
            }
        } catch (IOException e) {
            plugin.getLogger().severe("发送aermonitor:main查询失败 -> " + e.getMessage());
            List<Consumer<String>> cbs = serverCallbacks.remove(playerUuid);
            if (cbs != null) synchronized (cbs) { for (Consumer<String> cb : cbs) cb.accept(null); }
            return;
        }
        // timeout 40ticks(2s)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            List<Consumer<String>> cbs = serverCallbacks.remove(playerUuid);
            if (cbs != null) synchronized (cbs) { for (Consumer<String> cb : cbs) cb.accept(null); }
        }, 40L);
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
