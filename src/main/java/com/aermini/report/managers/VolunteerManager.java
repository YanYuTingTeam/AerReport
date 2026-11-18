package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.aermini.report.database.DatabaseManager;
import com.aermini.report.utils.RedisKeys;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VolunteerManager {
    private final AerReport plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, org.bukkit.Location> originalLocations = new HashMap<>();
    private final java.util.Set<UUID> patrolSet = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public VolunteerManager(AerReport plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    public void enterObserverMode(Player player, String originalServer) {
        final UUID uuid = player.getUniqueId();
        final String playerName = player.getName();
        originalLocations.put(uuid, player.getLocation().clone());
        databaseManager.asyncRun(() -> {
            String oldServer = databaseManager.getVolunteerOriginalServer(uuid);
            if (oldServer == null) {
                databaseManager.saveVolunteerState(uuid, playerName, originalServer);
            }
        });
    }

    public boolean exitObserverMode(Player player) {
        UUID uuid = player.getUniqueId();
        boolean wasPatrol = isPatrolling(uuid);
        if (wasPatrol) exitPatrol(player);
        originalLocations.remove(uuid);
        databaseManager.asyncRun(() -> databaseManager.removeVolunteerState(uuid));
        return true;
    }

    public void restoreAfterProcess(Player player) {
    }

    public void checkAndRestoreFromObserverMode(Player player) {
        UUID uuid = player.getUniqueId();
        databaseManager.asyncRun(() -> {
            boolean pending = databaseManager.isVolunteerPendingExit(uuid);
            if (pending) {
                databaseManager.setVolunteerPendingExit(uuid, false);
            }
        });
    }

    public boolean isInObserverMode(UUID playerUuid) {
        return originalLocations.containsKey(playerUuid);
    }

    public void enterPatrol(Player player) {
        UUID uuid = player.getUniqueId();
        patrolSet.add(uuid);
        databaseManager.asyncRun(() -> {
            try {
                plugin.getRedisManager().run(j -> j.hset(RedisKeys.player(uuid), "patrol", "1"));
            } catch (Exception ignored) {
            }
            databaseManager.setVolunteerPatrol(uuid, true);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                applyPatrol(player);
            });
        });
    }

    public void exitPatrol(Player player) {
        UUID uuid = player.getUniqueId();
        patrolSet.remove(uuid);
        revertPatrol(player);
        databaseManager.asyncRun(() -> {
            try {
                plugin.getRedisManager().run(j -> j.hset(RedisKeys.player(uuid), "patrol", "0"));
            } catch (Exception ignored) {
            }
            databaseManager.setVolunteerPatrol(uuid, false);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                executeExitCommands(player);
                String returnServer = plugin.getConfigManager().getReturnServer();
                if (returnServer != null && !returnServer.isEmpty()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("server", returnServer);
                    player.sendMessage(plugin.getConfigManager().getMessage("volunteer.exit-success", placeholders));
                    plugin.getBungeeManager().sendToServer(player, returnServer);
                }
            });
        });
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        player.removeMetadata("aerreport:patrol", plugin);
        patrolSet.remove(uuid);
        originalLocations.remove(uuid);
    }

    public boolean isPatrolling(UUID uuid) {
        return patrolSet.contains(uuid);
    }

    public void removeAllPatrolMetadata() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.removeMetadata("aerreport:patrol", plugin);
        }
    }

    public boolean syncPatrolFromDb(Player player) {
        UUID uuid = player.getUniqueId();
        Boolean patrol = databaseManager.queryVolunteerPatrolOrNull(uuid);
        if (patrol != null) {
            if (plugin.isEnabled()) {
                final String value = patrol ? "1" : "0";
                try {
                    plugin.getRedisManager().run(j -> j.hset(RedisKeys.player(uuid), "patrol", value));
                } catch (Exception ignored) {
                }
            }
        } else {
            try {
                String cached = plugin.getRedisManager().call(j -> j.hget(RedisKeys.player(uuid), "patrol"), null);
                if ("1".equals(cached)) patrol = Boolean.TRUE;
                else if ("0".equals(cached)) patrol = Boolean.FALSE;
            } catch (Exception ignored) {
            }
        }
        if (patrol == null) return false;
        if (patrol) patrolSet.add(uuid);
        else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && !isPatrolling(uuid)) {
                    patrolSet.remove(uuid);
                    player.removeMetadata("aerreport:patrol", plugin);
                }
            });
        }
        return patrol;
    }

    public void applyPatrol(Player patrolPlayer) {
        if (patrolPlayer == null || !patrolPlayer.isOnline()) return;
        patrolPlayer.setGameMode(GameMode.SPECTATOR);
        patrolPlayer.setAllowFlight(true);
        patrolPlayer.setFlying(true);
        patrolPlayer.setMetadata("aerreport:patrol", new FixedMetadataValue(plugin, true));
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(patrolPlayer.getUniqueId())) continue;
            other.hidePlayer(patrolPlayer);
        }
    }

    public void revertPatrol(Player patrolPlayer) {
        if (patrolPlayer == null || !patrolPlayer.isOnline()) return;
        if (patrolPlayer.getGameMode() == GameMode.SPECTATOR) patrolPlayer.setGameMode(GameMode.SURVIVAL);
        patrolPlayer.setFlying(false);
        patrolPlayer.setAllowFlight(false);
        patrolPlayer.removeMetadata("aerreport:patrol", plugin);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(patrolPlayer.getUniqueId())) continue;
            other.showPlayer(patrolPlayer);
        }
    }

    public void schedulePatrolReapply(Player player) {
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && patrolSet.contains(uuid)) applyPatrol(player);
        }, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && patrolSet.contains(uuid)) applyPatrol(player);
        }, 40L);
    }

    public void hidePatrolsFrom(Player joiningPlayer) {
        for (UUID patrolUuid : patrolSet) {
            Player patrolPlayer = Bukkit.getPlayer(patrolUuid);
            if (patrolPlayer != null && patrolPlayer.isOnline()) {
                joiningPlayer.hidePlayer(patrolPlayer);
            }
        }
    }

    public void enforcePatrolGameMode(Player patrolPlayer) {
        if (patrolPlayer == null) return;
        if (!patrolSet.contains(patrolPlayer.getUniqueId())) return;
        if (patrolPlayer.getGameMode() != GameMode.SPECTATOR) {
            patrolPlayer.setGameMode(GameMode.SPECTATOR);
        }
    }

    private void executeExitCommands(Player player) {
        List<String> cmds = plugin.getConfigManager().getExitObserverCommands();
        for (String cmd : cmds) {
            cmd = cmd.replace("{player}", player.getName());
            if (cmd.startsWith("[CONSOLE] ")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substring("[CONSOLE] ".length()));
            } else if (cmd.startsWith("[COMMAND] ")) {
                boolean wasOp = player.isOp();
                try {
                    player.setOp(true);
                    player.performCommand(cmd.substring("[COMMAND] ".length()));
                } finally {
                    player.setOp(wasOp);
                }
            } else if (cmd.startsWith("[CMD] ")) {
                player.performCommand(cmd.substring("[CMD] ".length()));
            }
        }
    }
}
