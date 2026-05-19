package edu.mc.listener;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.block.Chest;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.Location;

public class GameListener implements Listener {

    private final ChickenDinnerPlugin plugin;

    public GameListener(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        
        if (plugin.getCurrentState() == GameState.LOBBY) {
            player.getInventory().clear();
            player.getInventory().setItem(0, plugin.createRadarMap(player.getWorld()));
            player.getInventory().setHeldItemSlot(0);
            player.updateInventory();
            
            plugin.getPlayerManager().addPlayer(player);
            event.setJoinMessage("§e" + player.getName() + " §a加入了游戏(" + plugin.getPlayerManager().getAliveCount() + " 人)");
            
            // 延时 1 tick 执行传送，彻底避免 Spigot 1.8 客户端登入包覆盖/重置传送坐标的 Bug
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        Location lobbyLoc = new Location(player.getWorld(), 1387.5, 226.5, 21.5);
                        player.teleport(lobbyLoc);
                    }
                }
            }, 1L);
        } else {
            plugin.getPlayerManager().setSpectator(player);
            event.setJoinMessage(null);
            player.sendMessage("§c游戏已经开始，你现在处于旁观者模式。");
            
            // 延时 1 tick 寻找并观战最近的存活玩家，防止卡死或留在荒野边界外
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        Player targetSpectate = null;
                        double minDistance = Double.MAX_VALUE;
                        Location spawnLoc = player.getLocation();
                        for (java.util.UUID aliveId : plugin.getPlayerManager().getAlivePlayers()) {
                            Player alive = Bukkit.getPlayer(aliveId);
                            if (alive != null && alive.isOnline() && alive.getWorld().equals(player.getWorld())) {
                                double dist = alive.getLocation().distanceSquared(spawnLoc);
                                if (dist < minDistance) {
                                    minDistance = dist;
                                    targetSpectate = alive;
                                }
                            }
                        }
                        if (targetSpectate != null) {
                            player.teleport(targetSpectate.getLocation());
                            player.sendMessage("§a已自动为您切换至最近的存活玩家 " + targetSpectate.getName() + " 进行观战！");
                        } else {
                            // 若当前无存活玩家，则默认传送到吃鸡岛屿中心高度观战
                            Location center = new Location(player.getWorld(), 0.0, 100.0, 16.0);
                            player.teleport(center);
                        }
                    }
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerManager().removePlayer(player);
        event.setQuitMessage("§e" + player.getName() + " §c退出了游戏");
        
        if (plugin.getCurrentState() == GameState.INGAME || plugin.getCurrentState() == GameState.FLIGHT) {
            checkWinCondition();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();
        
        if (plugin.getCurrentState() == GameState.INGAME || plugin.getCurrentState() == GameState.FLIGHT) {
            // 先立即从存活列表移出，保证吃鸡结算的实时性
            plugin.getPlayerManager().getAlivePlayers().remove(player.getUniqueId());
            if (!plugin.getPlayerManager().getSpectators().contains(player.getUniqueId())) {
                plugin.getPlayerManager().getSpectators().add(player.getUniqueId());
            }
            Bukkit.broadcastMessage("§c" + player.getName() + " §e被淘汰了！剩余存活: §a" + plugin.getPlayerManager().getAliveCount());
            
            checkWinCondition();
        }
        
        final org.bukkit.Location deathLoc = player.getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    if (player.isDead()) {
                        try {
                            player.spigot().respawn();
                        } catch (Exception e) {
                            player.setHealth(player.getMaxHealth());
                            player.setFoodLevel(20);
                            player.teleport(deathLoc);
                        }
                    }
                    
                    // 玩家复活成实体后再设置旁观模式，避免 1.8 客户端死屏或游戏模式设置丢失的 Bug
                    // 同时包含 ENDING 状态：防止游戏在 2 tick 延迟内结算完毕后玩家以 ADVENTURE 身份出现
                    GameState currentState = plugin.getCurrentState();
                    if (currentState == GameState.INGAME || currentState == GameState.FLIGHT || currentState == GameState.ENDING) {
                        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                        player.getInventory().clear();
                        player.sendMessage("§c你已被淘汰！现在是观察者模式。");
                        
                        // 寻找最近的存活玩家并传送过去观战
                        Player targetSpectate = null;
                        double minDistance = Double.MAX_VALUE;
                        for (java.util.UUID aliveId : plugin.getPlayerManager().getAlivePlayers()) {
                            Player alive = Bukkit.getPlayer(aliveId);
                            if (alive != null && alive.isOnline() && alive.getWorld().equals(player.getWorld())) {
                                double dist = alive.getLocation().distanceSquared(deathLoc);
                                if (dist < minDistance) {
                                    minDistance = dist;
                                    targetSpectate = alive;
                                }
                            }
                        }
                        if (targetSpectate != null) {
                            player.teleport(targetSpectate.getLocation());
                            player.sendMessage("§a已自动为您切换至最近的存活玩家 " + targetSpectate.getName() + " 进行观战！");
                        }
                    }
                }
            }
        }, 2L);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            GameState state = plugin.getCurrentState();
            if (state == GameState.LOBBY 
                    || state == GameState.STARTING 
                    || state == GameState.ENDING // 结算结算阶段（显示获胜者）全服无敌，防止赢家意外暴毙
                    || plugin.getFlightManager().isOnPlane(p)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
            return;
        }
        // 禁止丢弃雷达地图
        if (event.getItemDrop().getItemStack().getType() == Material.MAP) {
            event.setCancelled(true);
            return;
        }
        // 如果在飞机上，禁止丢弃羽毛跳伞道具
        if (state == GameState.FLIGHT && plugin.getFlightManager().isOnPlane(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            // 在飞机上时锁定背包，禁止玩家丢弃或挪动跳伞羽毛或雷达地图
            if (plugin.getCurrentState() == GameState.FLIGHT && plugin.getFlightManager().isOnPlane(p)) {
                event.setCancelled(true);
                return;
            }
            
            // 比赛进行中，禁止挪动第一格（Slot 0）的雷达地图，确保其充当常驻战术雷达
            if (plugin.getCurrentState() == GameState.FLIGHT || plugin.getCurrentState() == GameState.INGAME) {
                if (event.getSlot() == 0 || (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.MAP)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        org.bukkit.inventory.ItemStack item = player.getItemInHand();

        // 跳伞用的羽毛
        if (item != null && item.getType() == Material.FEATHER) {
            if (item.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName() && meta.getDisplayName().contains("跳伞")) {
                    if (plugin.getCurrentState() == GameState.FLIGHT) {
                        plugin.getFlightManager().forceJump(player);
                    }
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 防止踩坏农田
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.SOIL) {
                event.setCancelled(true);
                return;
            }
        }
        
        // 非比赛阶段禁止开箱子
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                Material type = event.getClickedBlock().getType();
                if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.ENDER_CHEST) {
                    event.setCancelled(true);
                }
            }
            return;
        }
        
        // 开箱子逻辑
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)) {
                Chest chest = (Chest) block.getState();
                if (!plugin.getLootManager().isChestOpened(chest.getLocation())) {
                    plugin.getLootManager().populateChest(chest.getBlockInventory());
                    plugin.getLootManager().markChestOpened(chest.getLocation());
                }
            }
        }
    }

    private void checkWinCondition() {
        if (plugin.getPlayerManager().getAliveCount() <= 1) {
            plugin.getGameManager().endGame();
        }
    }
}
