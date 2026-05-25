package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
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

    public void prepareFlightPath() {
        if (startPoint != null && endPoint != null)
            return; // 避免重复生成

        World world = Bukkit.getWorlds().get(0);
        double angle = Math.random() * Math.PI * 2;

        double startX = GameConfig.MAP_CENTER_X + Math.cos(angle) * GameConfig.FLIGHT_RADIUS;
        double startZ = GameConfig.MAP_CENTER_Z + Math.sin(angle) * GameConfig.FLIGHT_RADIUS;

        double endX = GameConfig.MAP_CENTER_X - Math.cos(angle) * GameConfig.FLIGHT_RADIUS;
        double endZ = GameConfig.MAP_CENTER_Z - Math.sin(angle) * GameConfig.FLIGHT_RADIUS;

        startPoint = new Location(world, startX, GameConfig.FLIGHT_ALTITUDE, startZ);
        endPoint = new Location(world, endX, GameConfig.FLIGHT_ALTITUDE, endZ);

        flightDir = endPoint.toVector().subtract(startPoint.toVector())
                .multiply(1.0 / GameConfig.FLIGHT_DURATION_TICKS);
    }

    public void startFlight(java.util.Collection<UUID> alivePlayers) {
        World world = Bukkit.getWorlds().get(0);
        if (startPoint == null || endPoint == null) {
            prepareFlightPath();
        }

        playersOnPlane.clear();
        parachutingPlayers.clear();

        ItemStack parachuteItem = new ItemStack(Material.FEATHER);
        ItemMeta meta = parachuteItem.getItemMeta();
        meta.setDisplayName("§a§l[按 Shift 键 / 潜行跳伞]");
        parachuteItem.setItemMeta(meta);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playersOnPlane.add(uuid);
                p.teleport(startPoint);
                p.setGameMode(org.bukkit.GameMode.SURVIVAL); // 强制设为生存模式，保证落地后能正常开箱、丢弃/拾取物品
                p.setAllowFlight(true);
                p.setFlying(true);
                p.setFlySpeed(0f);
                p.setWalkSpeed(0f);

                p.getInventory().clear();
                plugin.getPacketMapManager().giveMap(p);
                // 重置雷达地图到第5格(slot 4)
                org.bukkit.inventory.ItemStack mapItem = p.getInventory().getItem(0);
                if (mapItem != null && mapItem.getType() == Material.MAP) {
                    p.getInventory().setItem(0, null);
                    p.getInventory().setItem(4, mapItem);
                }
                
                // 将跳伞羽毛放到最后一格
                p.getInventory().setItem(8, parachuteItem);
                p.getInventory().setHeldItemSlot(4); // 默认手持中间的雷达地图
                
                // 确保防具（彩色皮革）仍然穿着
                Integer teamId = plugin.getTeamManager().getTeam(uuid);
                if (teamId != null) {
                    edu.mc.manager.TeamManager.TeamInfo info = plugin.getTeamManager().getTeamInfo(teamId);
                    if (info != null) {
                        plugin.getTeamManager().equipTeamArmor(p, info);
                    }
                }
                
                p.updateInventory();
            }
        }

        Bukkit.broadcastMessage("§e[航线] 飞机已起飞！请按 Shift 键（潜行）进行跳伞！");

        // 延时 5 tick 后一次性刷新所有在线玩家的可见性，解决隐形 Bug
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p1 : Bukkit.getOnlinePlayers()) {
                for (Player p2 : Bukkit.getOnlinePlayers()) {
                    if (p1 != p2 && p1.isOnline() && p2.isOnline()) {
                        p1.hidePlayer(p2);
                        p1.showPlayer(p2);
                    }
                }
            }
        }, 5L);

        flightTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks += GameConfig.FLIGHT_TASK_INTERVAL_TICKS;
                boolean planeActive = ticks <= GameConfig.FLIGHT_DURATION_TICKS;

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
                        p.getInventory().setItem(8, null); // 移除跳伞羽毛
                        p.getInventory().setItem(7, null); // 移除物品栏里的队伍颜色帽子
                        p.getInventory().setHeldItemSlot(4);
                        
                        // 跳伞时隐藏彩色衣服，但保留头上的帽子
                        p.getInventory().setChestplate(null);
                        p.getInventory().setLeggings(null);
                        p.getInventory().setBoots(null);
                        
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
                        p.setFlying(false); // 落地后必须关闭飞行状态
                        p.setAllowFlight(false); // 落地后必须关闭允许飞行权限，恢复正常地面行走交互

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
                        // 每 2 tick 更新一次速度（更高频率使旁观者看到的人物动作更流畅）
                        Vector look = p.getLocation().getDirection();
                        look.setY(GameConfig.PARACHUTE_FALL_SPEED);
                        look.setX(look.getX() * GameConfig.PARACHUTE_GLIDE_MULTIPLIER);
                        look.setZ(look.getZ() * GameConfig.PARACHUTE_GLIDE_MULTIPLIER);
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
        flightTask.runTaskTimer(plugin, 1L, GameConfig.FLIGHT_TASK_INTERVAL_TICKS);
    }

    public void forceJump(Player p) {
        if (playersOnPlane.remove(p.getUniqueId())) {
            parachutingPlayers.add(p.getUniqueId());
            p.setFlying(false);
            p.setAllowFlight(false);
            p.setWalkSpeed(0.2f);
            p.setFlySpeed(0.1f);
            p.getInventory().setItem(8, null); // 仅移除最后一格的跳伞羽毛
            p.getInventory().setItem(7, null); // 移除物品栏里的队伍颜色帽子
            p.getInventory().setHeldItemSlot(4); // 重新切换到中间槽，向玩家展示 GPS 地图
            
            // 跳伞时隐藏彩色衣服，但保留头上的帽子
            p.getInventory().setChestplate(null);
            p.getInventory().setLeggings(null);
            p.getInventory().setBoots(null);
            
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
