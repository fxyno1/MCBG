package edu.mc.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerManager {

    private final Set<UUID> alivePlayers = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Map<UUID, Integer> killCount = new ConcurrentHashMap<>();

    public void addPlayer(Player player) {
        // 创造模式玩家（管理员/监视者）不参与统计和游玩，直接跳过
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!alivePlayers.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId())) {
            alivePlayers.add(player.getUniqueId());

            player.setGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(19);
            player.setLevel(0);
            player.setExp(0);
        }
    }

    public void addKill(Player player) {
        killCount.put(player.getUniqueId(), killCount.getOrDefault(player.getUniqueId(), 0) + 1);
    }

    public int getKills(Player player) {
        return killCount.getOrDefault(player.getUniqueId(), 0);
    }

    public void setSpectator(Player player) {
        alivePlayers.remove(player.getUniqueId());
        if (!spectators.contains(player.getUniqueId())) {
            spectators.add(player.getUniqueId());
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.sendMessage("§c你已被淘汰！现在是旁观者模式。");
    }

    public void removePlayer(Player player) {
        alivePlayers.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
    }

    public Set<UUID> getAlivePlayers() {
        return alivePlayers;
    }

    public Set<UUID> getSpectators() {
        return spectators;
    }

    public int getAliveCount() {
        return alivePlayers.size();
    }

    public boolean isAlive(Player player) {
        return alivePlayers.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public Player getWinner() {
        if (alivePlayers.size() == 1) {
            Iterator<UUID> it = alivePlayers.iterator();
            if (it.hasNext()) {
                Player winner = Bukkit.getPlayer(it.next());
                if (winner != null && winner.isOnline()) {
                    return winner;
                }
            }
        }
        return null;
    }

    public void reset() {
        alivePlayers.clear();
        spectators.clear();
        killCount.clear();
    }
}
