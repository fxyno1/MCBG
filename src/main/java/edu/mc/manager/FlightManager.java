package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlightManager {

    private final ChickenDinnerPlugin plugin;
    private BukkitRunnable flightTask;
    private Location startPoint;
    private Location endPoint;
    private Vector flightDir;

    private final Set<UUID> playersOnPlane = new HashSet<>();
    private final Set<UUID> parachutingPlayers = new HashSet<>();

    public FlightManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void startFlight(java.util.Collection<UUID> alivePlayers) {
        World world = Bukkit.getWorlds().get(0);
        double angle = Math.random() * Math.PI * 2;
        double radius = 220.0; 
        
        double startX = 0 + Math.cos(angle) * radius;
        double startZ = 16 + Math.sin(angle) * radius;
        
        double endX = 0 - Math.cos(angle) * radius;
        double endZ = 16 - Math.sin(angle) * radius;

        startPoint = new Location(world, startX, 150, startZ);
        endPoint = new Location(world, endX, 150, endZ);

        // 恢复平缓原速：由于 Minecraft 的速度是持续应用的，无需×4放大。1.0/600.0 是最合适的航班移动速度。
        flightDir = endPoint.toVector().subtract(startPoint.toVector()).multiply(1.0 / 600.0);

        playersOnPlane.clear();
        parachutingPlayers.clear();

        ItemStack parachuteItem = new ItemStack(Material.FEATHER);
        ItemMeta meta = parachuteItem.getItemMeta();
        meta.setDisplayName("§a§l[右键跳伞]");
        parachuteItem.setItemMeta(meta);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playersOnPlane.add(uuid);
                p.teleport(startPoint);
                p.setAllowFlight(true);
                p.setFlying(true);
                p.setFlySpeed(0f);
                p.setWalkSpeed(0f);
                
                p.getInventory().clear();
                p.getInventory().setItem(0, plugin.createRadarMap(world));
                p.getInventory().setItem(4, parachuteItem);
                p.getInventory().setHeldItemSlot(0); // 默认手持第一格，直接展示雷达地图！
                p.updateInventory();
            }
        }

        Bukkit.broadcastMessage("§e[航线] 飞机已起飞！请在背包中右键羽毛进行跳伞！");

        // 延时 5 ticks 强制刷新所有在线玩家的视野可见性，彻底解决 Spigot 1.8 大跨度同时传送带来的隐形 Bug
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                for (Player p1 : Bukkit.getOnlinePlayers()) {
                    for (Player p2 : Bukkit.getOnlinePlayers()) {
                        if (p1 != p2 && p1.isOnline() && p2.isOnline()) {
                            p1.hidePlayer(p2);
                            p1.showPlayer(p2);
                        }
                    }
                }
            }
        }, 5L);

        flightTask = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks += 4;
                boolean planeActive = ticks <= 600;

                // 处理仍在机舱内的玩家
                // 使用 Iterator 安全移除，避免每 tick new HashSet<>() 产生的临时对象 GC 压力
                java.util.Iterator<UUID> planeIter = playersOnPlane.iterator();
                while (planeIter.hasNext()) {
                    UUID uuid = planeIter.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        planeIter.remove();
                        continue;
                    }
                    if (planeActive) {
                        p.setVelocity(flightDir);
                    } else {
                        planeIter.remove();
                        parachutingPlayers.add(uuid);
                        p.setFlying(false);
                        p.setAllowFlight(false);
                        p.setWalkSpeed(0.2f);
                        p.setFlySpeed(0.1f);
                        p.getInventory().setItem(4, null);
                        p.getInventory().setHeldItemSlot(0);
                        p.updateInventory();
                        p.setFallDistance(0f);
                        p.sendMessage("§a[跳伞] 离开机舱！移动鼠标控制滑翔方向！");
                    }
                }

                // 处理正在滑翔跳伞的玩家
                java.util.Iterator<UUID> paraIter = parachutingPlayers.iterator();
                while (paraIter.hasNext()) {
                    UUID uuid = paraIter.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) {
                        paraIter.remove();
                        continue;
                    }

                    // 增强版落地检测：检查下方方块 + 速度 + 位置变化
                    boolean isOnGround = p.isOnGround();
                    
                    // 辅助检测：检查下方1-2格是否有实体方块或液体
                    org.bukkit.block.Block b1 = p.getLocation().getBlock().getRelative(0, -1, 0);
                    org.bukkit.block.Block b2 = p.getLocation().getBlock().getRelative(0, -2, 0);
                    boolean b1Hit = b1.getType().isSolid() || b1.isLiquid();
                    boolean b2Hit = b2.getType().isSolid() || b2.isLiquid();
                    
                    boolean velocityNearZero = p.getVelocity().length() < 0.1;
                    
                    if (isOnGround || (b1Hit && velocityNearZero) || b2Hit) {
                        paraIter.remove();
                        p.sendMessage("§a[降落] 成功着陆！开始搜刮物资吧！");
                        p.setFallDistance(0f);
                        p.setNoDamageTicks(60);
                        p.setWalkSpeed(0.2f);
                        p.setFlySpeed(0.1f);

                        // 延迟 2 ticks 强制刷新当前落地玩家与其他玩家的互相可见性，解决 Spigot 1.8.8 跨度传送后的隐形 Bug
                        final Player finalP = p;
                        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                            @Override
                            public void run() {
                                if (finalP.isOnline()) {
                                    for (Player other : Bukkit.getOnlinePlayers()) {
                                        if (finalP != other && other.isOnline()) {
                                            finalP.hidePlayer(other);
                                            finalP.showPlayer(other);
                                            other.hidePlayer(finalP);
                                            other.showPlayer(finalP);
                                        }
                                    }
                                }
                            }
                        }, 2L);
                    } else {
                        // 每 4 tick 更新一次速度
                        Vector look = p.getLocation().getDirection();
                        look.setY(-0.2); // 较缓的下落速率，滞空时间更长
                        look.setX(look.getX() * 1.5); // 水平滑翔速度提升至 1.5 倍，飞得极快、极远
                        look.setZ(look.getZ() * 1.5);
                        p.setVelocity(look);
                        p.setFallDistance(0f);
                    }
                }

                if (!planeActive && playersOnPlane.isEmpty() && parachutingPlayers.isEmpty()) {
                    // 所有玩家都已离开飞机且落地完毕，取消任务
                    this.cancel();
                }
            }
        };
        flightTask.runTaskTimer(plugin, 1L, 4L);
    }

    public void forceJump(Player p) {
        if (playersOnPlane.remove(p.getUniqueId())) {
            parachutingPlayers.add(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setWalkSpeed(0.2f);
            p.setFlySpeed(0.1f);
            p.getInventory().setItem(4, null); // 仅移除第 5 格的跳伞羽毛，保留 Slot 0 中的雷达地图！
            p.getInventory().setHeldItemSlot(0); // 重新切换到 Slot 0，向玩家展示 GPS 地图
            p.updateInventory();
            p.setFallDistance(0f);
            p.sendMessage("§a[跳伞] 离开机舱！移动鼠标控制滑翔方向！");
        }
    }

    public boolean isOnPlane(Player p) {
        return playersOnPlane.contains(p.getUniqueId());
    }

    public void removePlayerFromFlight(Player p) {
        playersOnPlane.remove(p.getUniqueId());
        parachutingPlayers.remove(p.getUniqueId());
    }

    public Location getStartPoint() {
        return startPoint;
    }

    public Location getEndPoint() {
        return endPoint;
    }

    public void reset() {
        if (flightTask != null) {
            flightTask.cancel();
            flightTask = null;
        }
        playersOnPlane.clear();
        parachutingPlayers.clear();
        startPoint = null;
        endPoint = null;
    }
}
