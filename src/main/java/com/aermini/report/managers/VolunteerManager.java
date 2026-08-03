package com.aermini.report.managers;

import com.aermini.report.AerReport;
import com.aermini.report.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

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
        originalLocations.put(player.getUniqueId(), player.getLocation().clone());
        if (databaseManager.getVolunteerOriginalServer(player.getUniqueId()) == null) {
            databaseManager.asyncRun(() ->
                    databaseManager.saveVolunteerState(player.getUniqueId(), player.getName(), originalServer));
        }
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
        databaseManager.asyncRun(() -> databaseManager.setVolunteerPatrol(uuid, true));
        Bukkit.getScheduler().runTask(plugin, () -> applyPatrol(player));
    }

    public void exitPatrol(Player player) {
        UUID uuid = player.getUniqueId();
        patrolSet.remove(uuid);
        databaseManager.asyncRun(() -> databaseManager.setVolunteerPatrol(uuid, false));
        revertPatrol(player);
        executeExitCommands(player);
        String returnServer = plugin.getConfigManager().getReturnServer();
        if (returnServer != null && !returnServer.isEmpty()) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("server", returnServer);
            player.sendMessage(plugin.getConfigManager().getMessage("volunteer.exit-success", placeholders));
            plugin.getBungeeManager().sendToServer(player, returnServer);
        }
    }

    public boolean isPatrolling(UUID uuid) {
        return patrolSet.contains(uuid);
    }

    public boolean syncPatrolFromDb(Player player) {
        UUID uuid = player.getUniqueId();
        boolean patrol = databaseManager.isVolunteerPatrol(uuid);
        if (patrol) patrolSet.add(uuid);
        else patrolSet.remove(uuid);
        return patrol;
    }

    public void applyPatrol(Player patrolPlayer) {
        if (patrolPlayer == null || !patrolPlayer.isOnline()) return;
        patrolPlayer.setGameMode(GameMode.SPECTATOR);
        patrolPlayer.setAllowFlight(true);
        patrolPlayer.setFlying(true);
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
