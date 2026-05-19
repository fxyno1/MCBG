package edu.mc.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ScatterManager {

    private final Random random = new Random();

    public void scatterPlayers(List<UUID> players, Location center, double maxRadius) {
        World world = center.getWorld();
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                Location safeLoc = findSafeLocation(center, maxRadius);
                
                player.setFallDistance(0.0f);
                player.teleport(safeLoc);
                player.setNoDamageTicks(60);
                
                player.sendMessage("§a你已安全着陆，拥有 3 秒无敌时间，寻找物资准备战斗吧！");
            }
        }
    }

    private Location findSafeLocation(Location center, double maxRadius) {
        World world = center.getWorld();
        int maxTries = 50;
        for (int i = 0; i < maxTries; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double r = random.nextDouble() * maxRadius;

            int x = center.getBlockX() + (int) (Math.cos(angle) * r);
            int z = center.getBlockZ() + (int) (Math.sin(angle) * r);

            int y = world.getHighestBlockYAt(x, z);

            if (y <= 0) {
                continue;
            }

            Location loc = new Location(world, x, y + 2, z);

            org.bukkit.Material type = world.getBlockAt(x, y - 1, z).getType();
            if (type != org.bukkit.Material.WATER && type != org.bukkit.Material.STATIONARY_WATER &&
                type != org.bukkit.Material.LAVA && type != org.bukkit.Material.STATIONARY_LAVA &&
                type != org.bukkit.Material.CACTUS && type != org.bukkit.Material.LEAVES) {
                return loc;
            }
        }

        int fallbackY = world.getHighestBlockYAt(center.getBlockX(), center.getBlockZ());
        return new Location(world, center.getBlockX(), fallbackY > 0 ? fallbackY + 2 : 100, center.getBlockZ());
    }
}
