package edu.mc.listener;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
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
        plugin.resetPlayerMap(player.getUniqueId());

        // 创造模式玩家（管理员）不参与游戏重置、不分发地图，直接保留状态并发送普通提示
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            event.setJoinMessage("§7[管理员] " + player.getName() + " 进入了服务器。");
            return;
        }

        Location lobbyLoc = new Location(Bukkit.getWorlds().get(0), GameConfig.LOBBY_X, GameConfig.LOBBY_Y,
                GameConfig.LOBBY_Z);

        if (plugin.getCurrentState() == GameState.LOBBY || plugin.getCurrentState() == GameState.STARTING) {
            player.setMaxHealth(40.0);
            player.setHealth(40.0);
            player.getInventory().clear();
            player.getInventory().setItem(0, plugin.createRadarMap(player));
            player.getInventory().setHeldItemSlot(0);
            player.updateInventory();

            plugin.getPlayerManager().addPlayer(player);
            event.setJoinMessage(
                    "§e" + player.getName() + " §a加入了游戏(" + plugin.getPlayerManager().getAliveCount() + " 人)");

            // 1. 立即执行传送
            player.teleport(lobbyLoc);

            // 2. 延时 2 ticks 执行传送，防止 1.8 登入包覆盖
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (plugin.getCurrentState() == GameState.LOBBY
                        || plugin.getCurrentState() == GameState.STARTING)) {
                    player.teleport(lobbyLoc);
                }
            }, 2L);

            // 3. 强力兜底：延时 10 ticks 再次执行传送，确保客户端彻底加载完地图后同步坐标
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (plugin.getCurrentState() == GameState.LOBBY
                        || plugin.getCurrentState() == GameState.STARTING)) {
                    player.teleport(lobbyLoc);
                }
            }, 10L);
        } else {
            // 如果玩家本身就在存活列表中（说明是活人玩家掉线重连），保持生存模式，不要切成旁观者
            if (plugin.getPlayerManager().getAlivePlayers().contains(player.getUniqueId())) {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setMaxHealth(40.0);
                // 确保其背包里有雷达地图
                if (player.getInventory().getItem(0) == null
                        || player.getInventory().getItem(0).getType() != org.bukkit.Material.MAP) {
                    player.getInventory().setItem(0, plugin.createRadarMap(player));
                    player.updateInventory();
                }
                player.sendMessage("§a[游戏提示] 欢迎回来！你已重新连接到比赛。");
            } else {
                plugin.getPlayerManager().setSpectator(player);
                event.setJoinMessage(null);
                player.sendMessage("§c游戏已经开始，你现在处于旁观者模式。");

                // 立即传送并多次延时传送，确保旁观者成功传送至观战位置
                teleportSpectatorToTarget(player);
            }
        }
    }

    private void teleportSpectatorToTarget(Player player) {
        Location spawnLoc = player.getLocation();
        Player targetSpectate = findNearestAlivePlayer(spawnLoc);
        Location targetLoc = (targetSpectate != null) ? targetSpectate.getLocation()
                : new Location(player.getWorld(), 0.0, 100.0, 16.0);

        player.teleport(targetLoc);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Player target = findNearestAlivePlayer(player.getLocation());
                Location loc = (target != null) ? target.getLocation()
                        : new Location(player.getWorld(), 0.0, 100.0, 16.0);
                player.teleport(loc);
                if (target != null) {
                    player.sendMessage("§a已自动为您切换至最近的存活玩家 " + target.getName() + " 进行观战！");
                }
            }
        }, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Player target = findNearestAlivePlayer(player.getLocation());
                Location loc = (target != null) ? target.getLocation()
                        : new Location(player.getWorld(), 0.0, 100.0, 16.0);
                player.teleport(loc);
            }
        }, 15L);
    }

    private Player findNearestAlivePlayer(Location origin) {
        Player targetSpectate = null;
        double minDistance = Double.MAX_VALUE;
        for (java.util.UUID aliveId : plugin.getPlayerManager().getAlivePlayers()) {
            Player alive = Bukkit.getPlayer(aliveId);
            if (alive != null && alive.isOnline() && alive.getWorld().equals(origin.getWorld())) {
                double dist = alive.getLocation().distanceSquared(origin);
                if (dist < minDistance) {
                    minDistance = dist;
                    targetSpectate = alive;
                }
            }
        }
        return targetSpectate;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getPlayerManager().removePlayer(player);
        plugin.resetPlayerMap(player.getUniqueId());
        event.setQuitMessage("§e" + player.getName() + " §c退出了游戏");

        if (plugin.getCurrentState() == GameState.INGAME || plugin.getCurrentState() == GameState.FLIGHT) {
            checkWinCondition();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();

        // 防止死亡时掉落雷达地图
        event.getDrops().removeIf(item -> item != null && item.getType() == Material.MAP);

        if (plugin.getCurrentState() == GameState.INGAME || plugin.getCurrentState() == GameState.FLIGHT) {
            // 先立即从存活列表移出，保证吃鸡结算的实时性
            plugin.getPlayerManager().getAlivePlayers().remove(player.getUniqueId());
            if (!plugin.getPlayerManager().getSpectators().contains(player.getUniqueId())) {
                plugin.getPlayerManager().getSpectators().add(player.getUniqueId());
            }
            Bukkit.broadcastMessage(
                    "§c" + player.getName() + " §e被淘汰了！剩余存活: §a" + plugin.getPlayerManager().getAliveCount());

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
                    // 同时包含 ENDING 状态：防止游戏在 2 tick 延迟内结算完毕后玩家以 SURVIVAL 身份出现
                    GameState currentState = plugin.getCurrentState();
                    if (currentState == GameState.INGAME || currentState == GameState.FLIGHT
                            || currentState == GameState.ENDING) {
                        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                        player.getInventory().clear();
                        // 立即清除该玩家的地图渲染缓存，防止其下一局拿到地图时显示上一把死亡位置的残留画面
                        plugin.resetPlayerMap(player.getUniqueId());
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

    /**
     * 禁止非游戏阶段吃东西（包括手持食物和蛋糕）
     * FoodLevelChangeEvent 无法捕获蛋糕，必须在 PlayerInteract 里单独拦截
     */
    @EventHandler
    public void onPlayerItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
        }
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
        if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
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
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player p = (Player) event.getWhoClicked();
        if (p.isOp() && p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        GameState state = plugin.getCurrentState();

        if (state == GameState.FLIGHT && plugin.getFlightManager().isOnPlane(p)) {
            event.setCancelled(true);
            return;
        }

        if (state == GameState.FLIGHT || state == GameState.INGAME) {
            org.bukkit.event.inventory.ClickType click = event.getClick();
            org.bukkit.inventory.Inventory clickedInv = event.getClickedInventory();

            if (clickedInv != null) {
                if (clickedInv.equals(p.getInventory())) {
                    if (click.isShiftClick()
                            && event.getCurrentItem() != null
                            && event.getCurrentItem().getType() == Material.MAP) {
                        org.bukkit.inventory.Inventory topInv = event.getView().getTopInventory();
                        if (topInv != null && !topInv.equals(p.getInventory())) {
                            event.setCancelled(true);
                            return;
                        }
                    }
                } else {
                    if (event.getCursor() != null && event.getCursor().getType() == Material.MAP) {
                        event.setCancelled(true);
                        return;
                    }
                    if (click == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                        int hotbarSlot = event.getHotbarButton();
                        if (hotbarSlot >= 0 && hotbarSlot < 9) {
                            org.bukkit.inventory.ItemStack hotbarItem = p.getInventory().getItem(hotbarSlot);
                            if (hotbarItem != null && hotbarItem.getType() == Material.MAP) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
            Bukkit.getLogger().info(
                    "[MCBG DEBUG] Pickup cancelled for " + event.getPlayer().getName() + " because state is " + state);
        } else {
            Bukkit.getLogger()
                    .info("[MCBG DEBUG] Pickup allowed for " + event.getPlayer().getName() + " (state: " + state + ")");
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            return;
        }
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

        // 打药逻辑（绷带/急救包/医疗箱）
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                // 如果当前玩家正在打药，再次右键任何手持药品直接触发取消
                if (plugin.getHealingManager().isHealing(player)) {
                    long now = System.currentTimeMillis();
                    long elapsed = now - plugin.getHealingManager().getStartTime(player);
                    long lastInteract = plugin.getHealingManager().getLastInteractTime(player);
                    long timeSinceLastInteract = now - lastInteract;

                    plugin.getHealingManager().updateLastInteractTime(player, now);

                    if (elapsed > 500 && timeSinceLastInteract > 400) { // 必须间隔至少 500 毫秒且不能是连续按住右键才允许取消
                        plugin.getHealingManager().cancelHealing(player);
                    }
                    if (item.getType() == Material.CHEST) {
                        event.setCancelled(true);
                    }
                    return;
                }

                String name = item.getItemMeta().getDisplayName();
                if (name.contains("绷带")) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        plugin.getHealingManager().updateLastInteractTime(player, System.currentTimeMillis());
                        plugin.getHealingManager().startHealing(player, false);
                    } else {
                        plugin.sendActionBar(player, "§c你的生命值已满！");
                    }
                    event.setCancelled(true);
                    return;
                } else if (name.contains("急救包")) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        plugin.getHealingManager().updateLastInteractTime(player, System.currentTimeMillis());
                        plugin.getHealingManager().startHealing(player, true);
                    } else {
                        plugin.sendActionBar(player, "§c你的生命值已满！");
                    }
                    event.setCancelled(true);
                    return;
                } else if (name.contains("医疗箱")) {
                    if (player.getHealth() < player.getMaxHealth() || player.getFoodLevel() < 20) {
                        plugin.getHealingManager().updateLastInteractTime(player, System.currentTimeMillis());
                        plugin.getHealingManager().startHealingMedicalBox(player);
                    } else {
                        plugin.sendActionBar(player, "§c你的生命值和饥饿值均已满！");
                    }
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // 蛋糕是方块交互式食物，右键吃一格不触发 ItemConsume，必须在此单独拦截
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.CAKE_BLOCK) {
            GameState cakeState = plugin.getCurrentState();
            if (cakeState != GameState.INGAME && cakeState != GameState.FLIGHT) {
                event.setCancelled(true);
                return;
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
                // 强制允许开箱，无视服务器自带的出生点保护(Spawn Protection)或保护插件导致普通玩家无法开箱的问题
                if (event.isCancelled() || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
                    event.setCancelled(false);
                    event.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
                }
                Chest chest = (Chest) block.getState();
                org.bukkit.inventory.Inventory inv = chest.getInventory();
                Location loc = chest.getLocation();

                // 如果是双箱子（DoubleChest），我们将 Location 统一规范为 DoubleChest 的公共合成 Location
                // 这可彻底杜绝玩家通过分别右键左右两半边大箱子，导致大箱子被连续刷出两次物资的 Bug
                if (inv instanceof org.bukkit.inventory.DoubleChestInventory) {
                    org.bukkit.block.DoubleChest holder = ((org.bukkit.inventory.DoubleChestInventory) inv).getHolder();
                    if (holder != null) {
                        loc = holder.getLocation();
                    }
                }

                // 如果是空投箱（TRAPPED_CHEST），直接跳过物资填充（因为生成时已设定好专属高阶物资）
                if (block.getType() == Material.TRAPPED_CHEST) {
                    return;
                }

                if (!plugin.getLootManager().isChestOpened(loc)) {
                    plugin.getLootManager().populateChest(inv);
                    plugin.getLootManager().markChestOpened(loc);
                    if (inv instanceof org.bukkit.inventory.DoubleChestInventory) {
                        org.bukkit.block.DoubleChest holder = ((org.bukkit.inventory.DoubleChestInventory) inv)
                                .getHolder();
                        if (holder != null) {
                            Chest left = (Chest) holder.getLeftSide();
                            Chest right = (Chest) holder.getRightSide();
                            if (left != null)
                                left.update(true);
                            if (right != null)
                                right.update(true);
                        }
                    } else {
                        chest.update(true);
                    }
                }
            }
        }
    }

    /**
     * 强力兜底：玩家在死亡界面点击“复活”后，再次彻底清理其背包和地图状态
     */
    @EventHandler
    public void onPlayerRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        GameState state = plugin.getCurrentState();

        // 如果是游戏中死亡复活的旁观者玩家，确保其没有残留地图且为 SPECTATOR
        if (state == GameState.INGAME || state == GameState.FLIGHT || state == GameState.ENDING) {
            if (plugin.getPlayerManager().isSpectator(player)) {
                player.getInventory().clear();
                plugin.resetPlayerMap(player.getUniqueId());

                // 延迟 1 tick 强制设置模式，防止 Spigot 内置复活包重置 GameMode
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                    }
                }, 1L);
            }
        } else if (state == GameState.LOBBY) {
            // 如果玩家在死亡界面一直等到游戏结束回到大厅才点复活，确保重置其手持地图并传送回大厅
            event.setRespawnLocation(new Location(Bukkit.getWorlds().get(0), GameConfig.LOBBY_X, GameConfig.LOBBY_Y,
                    GameConfig.LOBBY_Z));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && plugin.getCurrentState() == GameState.LOBBY) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.getInventory().clear();
                    player.getInventory().setItem(0, plugin.createRadarMap(player));
                    player.getInventory().setHeldItemSlot(0);
                    player.updateInventory();
                }
            }, 1L);
        }
    }

    /**
     * 监听玩家游戏模式切换：创造模式玩家不能计入统计，防止 OP 监考老师霸占游戏名额
     */
    @EventHandler
    public void onPlayerGameModeChange(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        org.bukkit.GameMode newMode = event.getNewGameMode();

        if (newMode == org.bukkit.GameMode.CREATIVE) {
            // 切换到创造模式：立刻视作“出局”，从存活列表中移除，并检查吃鸡判定
            plugin.getPlayerManager().getAlivePlayers().remove(player.getUniqueId());
            checkWinCondition();
        } else if (newMode == org.bukkit.GameMode.SURVIVAL || newMode == org.bukkit.GameMode.ADVENTURE) {
            // 只有在大厅阶段切换回来时，才允许重新加回活人列表参与游戏
            if (plugin.getCurrentState() == GameState.LOBBY) {
                if (!plugin.getPlayerManager().getAlivePlayers().contains(player.getUniqueId())) {
                    plugin.getPlayerManager().addPlayer(player);
                }
            }
        }
    }

    private void checkWinCondition() {
        int alive = plugin.getPlayerManager().getAliveCount();
        int initial = plugin.getGameManager().getInitialPlayerCount();
        if (alive <= 0 || (initial > 1 && alive <= 1)) {
            plugin.getGameManager().endGame();
        }
    }
}
