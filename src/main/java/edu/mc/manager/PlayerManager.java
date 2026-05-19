package edu.mc.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerManager {

    private final Set<UUID> alivePlayers = new LinkedHashSet<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();

    public void addPlayer(Player player) {
        if (!alivePlayers.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId())) {
            alivePlayers.add(player.getUniqueId());
            
            player.setGameMode(GameMode.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);
            player.setLevel(0);
            player.setExp(0);
        }
    }

    public void setSpectator(Player player) {
        alivePlayers.remove(player.getUniqueId());
        if (!spectators.contains(player.getUniqueId())) {
            spectators.add(player.getUniqueId());
        }
        
        player.setGameMode(GameMode.SPECTATOR);
        player.getInventory().clear();
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
    }
}
