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
import java.util.Map;
import java.util.HashMap;

public class GameListener implements Listener {

    private final ChickenDinnerPlugin plugin;
    private final Map<org.bukkit.Location, Long> deathChestSpawnTimes = new HashMap<>();
    private final Map<java.util.UUID, org.bukkit.Location> deathLocations = new HashMap<>();

    public GameListener(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // 进服瞬间设置饥饿度为只差半个鸡腿，并执行一级清洗
        player.setFoodLevel(19);
        plugin.getPacketMapManager().removeMap(player);

        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("player", player.getName());
            event.setJoinMessage(plugin.getMessageManager().getMessage("extra_game.join_message", map));
            return;
        }

        Location lobbyLoc = new Location(Bukkit.getWorlds().get(0), GameConfig.LOBBY_X, GameConfig.LOBBY_Y,
                GameConfig.LOBBY_Z);

        if (plugin.getCurrentState() == GameState.LOBBY || plugin.getCurrentState() == GameState.STARTING) {
            player.setMaxHealth(40.0);
            player.setHealth(40.0);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);

            // 第一次生成新图分发
            plugin.getPacketMapManager().giveMap(player);

            org.bukkit.inventory.ItemStack mapItem = player.getInventory().getItem(0);
            if (mapItem != null && mapItem.getType() == Material.MAP) {
                player.getInventory().setItem(0, null);
                player.getInventory().setItem(4, mapItem);
            }

            org.bukkit.inventory.ItemStack paper = new org.bukkit.inventory.ItemStack(Material.PAPER);
            org.bukkit.inventory.meta.ItemMeta paperMeta = paper.getItemMeta();
            paperMeta.setDisplayName(plugin.getMessageManager().getMessage("gui.team_select"));
            paper.setItemMeta(paperMeta);
            player.getInventory().setItem(0, paper);

            org.bukkit.inventory.ItemStack feather = new org.bukkit.inventory.ItemStack(Material.FEATHER);
            org.bukkit.inventory.meta.ItemMeta featherMeta = feather.getItemMeta();
            featherMeta.setDisplayName(plugin.getMessageManager().getMessage("gui.back_to_hub"));
            feather.setItemMeta(featherMeta);
            player.getInventory().setItem(8, feather);

            player.getInventory().setHeldItemSlot(0);
            player.updateInventory();

            plugin.getPlayerManager().addPlayer(player);
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("player", player.getName());
            map.put("count", String.valueOf(plugin.getPlayerManager().getAliveCount()));
            event.setJoinMessage(plugin.getMessageManager().getMessage("extra_game.join_player", map));

            // 1. 立即执行传送
            player.teleport(lobbyLoc);

            // 2. 延时 2 ticks 执行传送，防止 1.8 登入包覆盖
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (plugin.getCurrentState() == GameState.LOBBY
                        || plugin.getCurrentState() == GameState.STARTING)) {
                    player.teleport(lobbyLoc);
                }
            }, 2L);

            // 延迟 5 ticks 执行强力二次覆写，斩断原版登入包的残存干扰
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (plugin.getCurrentState() == GameState.LOBBY
                        || plugin.getCurrentState() == GameState.STARTING)) {
                    plugin.getPacketMapManager().removeMap(player);
                    plugin.getPacketMapManager().giveMap(player);
                    player.updateInventory();
                }
            }, 5L);

            // 3. 强力兜底：延时 10 ticks 再次执行传送，确保客户端彻底加载完地图后同步坐标
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (plugin.getCurrentState() == GameState.LOBBY
                        || plugin.getCurrentState() == GameState.STARTING)) {
                    player.teleport(lobbyLoc);
                }
            }, 10L);

            // 【终极兜底方案】延时 25 贴（1.25秒）
            // 此时客户端彻底稳定进入了大厅场景。在这里下发最后一次物理覆盖包，彻底粉碎各种幽灵残影。
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && (plugin.getCurrentState() == GameState.LOBBY
                        || plugin.getCurrentState() == GameState.STARTING)) {
                    plugin.getPacketMapManager().removeMap(player);
                    plugin.getPacketMapManager().giveMap(player);
                    org.bukkit.inventory.ItemStack delayedMapItem = player.getInventory().getItem(0);
                    if (delayedMapItem != null && delayedMapItem.getType() == Material.MAP) {
                        player.getInventory().setItem(0, null);
                        player.getInventory().setItem(4, delayedMapItem);
                    }
                    player.updateInventory();
                }
            }, 25L);
        } else {
            // 如果玩家是战斗中掉线重连
            if (plugin.getPlayerManager().getAlivePlayers().contains(player.getUniqueId())) {
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setMaxHealth(40.0);

                // 掉线重连同样执行 15 ticks 延迟物理灌入覆盖
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.getPacketMapManager().removeMap(player);
                        plugin.getPacketMapManager().giveMap(player);
                        player.updateInventory();
                        player.sendMessage(plugin.getMessageManager().getMessage("radar.reconnect"));
                    }
                }, 15L);
            } else {
                plugin.getPlayerManager().setSpectator(player);
                event.setJoinMessage(null);
                player.sendMessage(plugin.getMessageManager().getMessage("player.already_started"));
                teleportSpectatorToTarget(player);
            }
        }
    }

    private void teleportSpectatorToTarget(Player player) {
        Location spawnLoc = player.getLocation();
        Player targetSpectate = findNearestAlivePlayer(spawnLoc);
        Location targetLoc = (targetSpectate != null) ? targetSpectate.getLocation().clone().add(0, 3.5, 0)
                : new Location(player.getWorld(), 0.0, 100.0, 16.0);

        player.teleport(targetLoc);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Player target = findNearestAlivePlayer(player.getLocation());
                Location loc = (target != null) ? target.getLocation().clone().add(0, 3.5, 0)
                        : new Location(player.getWorld(), 0.0, 100.0, 16.0);
                player.teleport(loc);
                if (target != null) {
                    java.util.Map<String, String> map = new java.util.HashMap<>();
                    map.put("player", target.getName());
                    player.sendMessage(plugin.getMessageManager().getMessage("spectator.auto_switch", map));
                }
            }
        }, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                Player target = findNearestAlivePlayer(player.getLocation());
                Location loc = (target != null) ? target.getLocation().clone().add(0, 3.5, 0)
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
            if (alive != null && alive.isOnline()) {
                if (!alive.getWorld().equals(origin.getWorld())) {
                    return alive; // If in different worlds, just return the first alive player found
                }
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
        plugin.getScoreboardManager().removePlayer(player);
        plugin.getPacketMapManager().removeMap(player);

        GameState state = plugin.getCurrentState();
        if (state == GameState.LOBBY || state == GameState.STARTING) {
            plugin.getTeamManager().leaveTeam(player);
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
        }

        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("player", player.getName());
        event.setQuitMessage(plugin.getMessageManager().getMessage("extra_game.quit_message", map));
        if (state == GameState.INGAME || state == GameState.FLIGHT) {
            checkWinCondition();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        final Player player = event.getEntity();

        // 修复：玩家被打死时如果正在打药，将掉落物中的正在使用的药还原
        plugin.getHealingManager().handleDeathDrops(player, event.getDrops());

        // 注册击杀
        Player killer = player.getKiller();
        if (killer != null) {
            plugin.getPlayerManager().addKill(killer);
        }

        // 防止死亡时掉落雷达地图
        event.getDrops().removeIf(item -> item != null && item.getType() == Material.MAP);

        // 防止死亡时掉落队伍帽子，防止伪装
        event.getDrops().removeIf(item -> item != null && item.getType() == Material.LEATHER_HELMET);

        if (plugin.getCurrentState() == GameState.INGAME || plugin.getCurrentState() == GameState.FLIGHT) {
            // 生成死亡盒子 (陷阱箱双箱)
            Block deathBlock = player.getLocation().getBlock();
            if (deathBlock.getY() > 0 && deathBlock.getY() < 255) {
                Block eastBlock = deathBlock.getRelative(org.bukkit.block.BlockFace.EAST);

                deathBlock.setType(Material.TRAPPED_CHEST);
                eastBlock.setType(Material.TRAPPED_CHEST);
                
                long currentTime = System.currentTimeMillis();
                deathChestSpawnTimes.put(deathBlock.getLocation(), currentTime);
                deathChestSpawnTimes.put(eastBlock.getLocation(), currentTime);

                try {
                    org.bukkit.block.Chest chestState = (org.bukkit.block.Chest) deathBlock.getState();
                    org.bukkit.inventory.Inventory inv = chestState.getInventory();

                    java.util.List<org.bukkit.inventory.ItemStack> drops = new java.util.ArrayList<>(event.getDrops());
                    event.getDrops().clear();
                    for (org.bukkit.inventory.ItemStack drop : drops) {
                        if (drop != null && drop.getType() != Material.AIR) {
                            java.util.HashMap<Integer, org.bukkit.inventory.ItemStack> left = inv.addItem(drop);
                            for (org.bukkit.inventory.ItemStack leftover : left.values()) {
                                deathBlock.getWorld().dropItemNaturally(deathBlock.getLocation(), leftover);
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }

                // 放置四周的遗物箱告示牌
                org.bukkit.block.BlockFace[] faces = { org.bukkit.block.BlockFace.NORTH,
                        org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST };
                for (org.bukkit.block.BlockFace face : faces) {
                    placeSign(deathBlock.getRelative(face), face, player.getName());
                }
                placeSign(eastBlock.getRelative(org.bukkit.block.BlockFace.EAST), org.bukkit.block.BlockFace.EAST,
                        player.getName());
                placeSign(eastBlock.getRelative(org.bukkit.block.BlockFace.NORTH), org.bukkit.block.BlockFace.NORTH,
                        player.getName());
                placeSign(eastBlock.getRelative(org.bukkit.block.BlockFace.SOUTH), org.bukkit.block.BlockFace.SOUTH,
                        player.getName());
            }

            // 先立即从存活列表移出，保证吃鸡结算的实时性
            plugin.getPlayerManager().getAlivePlayers().remove(player.getUniqueId());
            if (!plugin.getPlayerManager().getSpectators().contains(player.getUniqueId())) {
                plugin.getPlayerManager().getSpectators().add(player.getUniqueId());
            }
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("player", player.getName());
            map.put("alive", String.valueOf(plugin.getPlayerManager().getAliveCount()));
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("extra_game.eliminated_broadcast", map));

            checkWinCondition();
        }

        final org.bukkit.Location deathLoc = player.getLocation();
        deathLocations.put(player.getUniqueId(), deathLoc);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    if (player.isDead()) {
                        try {
                            player.spigot().respawn();
                        } catch (Exception e) {
                            player.setHealth(player.getMaxHealth());
                            player.setFoodLevel(19);
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
                        player.getInventory().setArmorContents(null);
                        plugin.getPacketMapManager().removeMap(player);
                        player.sendMessage(plugin.getMessageManager().getMessage("player.eliminated"));

                        org.bukkit.inventory.ItemStack eye = new org.bukkit.inventory.ItemStack(Material.EYE_OF_ENDER);
                        org.bukkit.inventory.meta.ItemMeta eyeMeta = eye.getItemMeta();
                        eyeMeta.setDisplayName(plugin.getMessageManager().getMessage("gui.spectate_player"));
                        eye.setItemMeta(eyeMeta);
                        player.getInventory().setItem(0, eye);

                        org.bukkit.inventory.ItemStack cart = new org.bukkit.inventory.ItemStack(Material.STORAGE_MINECART);
                        org.bukkit.inventory.meta.ItemMeta cartMeta = cart.getItemMeta();
                        cartMeta.setDisplayName(plugin.getMessageManager().getMessage("gui.play_again"));
                        cartMeta.setLore(java.util.Arrays.asList(plugin.getMessageManager().getMessage("gui.play_again_lore")));
                        cart.setItemMeta(cartMeta);
                        player.getInventory().setItem(7, cart);

                        org.bukkit.inventory.ItemStack bed = new org.bukkit.inventory.ItemStack(Material.BED);
                        org.bukkit.inventory.meta.ItemMeta bedMeta = bed.getItemMeta();
                        bedMeta.setDisplayName(plugin.getMessageManager().getMessage("gui.back_to_hub"));
                        bed.setItemMeta(bedMeta);
                        player.getInventory().setItem(8, bed);

                        // 传送到自己的死亡位置
                        player.teleport(deathLoc.clone().add(0, 1.5, 0));

                        // 【修复】必须在 teleport 之后稍等一下再打开死亡菜单，否则传送包会导致打开的菜单立刻被客户端强制关闭！
                        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                            @Override
                            public void run() {
                                if (player.isOnline()) {
                                    edu.mc.listener.SpectatorListener.openDeathMenu(player);
                                }
                            }
                        }, 2L);
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
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            Player attacker = null;

            if (event.getDamager() instanceof Player) {
                attacker = (Player) event.getDamager();
            } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
                org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
                if (proj.getShooter() instanceof Player) {
                    attacker = (Player) proj.getShooter();
                }
            }

            if (attacker != null) {
                if (plugin.getTeamManager().isSameTeam(victim.getUniqueId(), attacker.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        GameState state = plugin.getCurrentState();
        if ((state == GameState.INGAME || state == GameState.FLIGHT)
                && event.getBlock().getWorld().getName().equals("game_1")) {
            
            Block block = event.getBlock();
            if (block.getType() == Material.TRAPPED_CHEST || block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
                Location loc = block.getLocation();
                Long spawnTime = deathChestSpawnTimes.get(loc);
                
                // 如果打破的是告示牌，检查它附着的方块
                if (spawnTime == null && (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST)) {
                    org.bukkit.block.BlockState bState = block.getState();
                    if (bState instanceof org.bukkit.block.Sign) {
                        org.bukkit.material.Sign signData = (org.bukkit.material.Sign) bState.getData();
                        Block attached = block.getRelative(signData.getAttachedFace());
                        spawnTime = deathChestSpawnTimes.get(attached.getLocation());
                    }
                }
                
                if (spawnTime != null) {
                    if (System.currentTimeMillis() - spawnTime < 3000) {
                        event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("player.chest_protected"));
                        event.setCancelled(true);
                        return;
                    } else {
                        deathChestSpawnTimes.remove(loc); // 清理过期数据
                    }
                }
            }

            return; // 允许在 game_1 世界破坏任何方块
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getPlayer().isOp() && event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        GameState state = plugin.getCurrentState();
        if ((state == GameState.INGAME || state == GameState.FLIGHT)
                && event.getBlock().getWorld().getName().equals("game_1")) {
            return; // 允许在 game_1 世界放置方块
        }
        event.setCancelled(true);
    }

    /**
     * 禁止非游戏阶段吃东西（包括手持食物和蛋糕）
     * FoodLevelChangeEvent 无法捕获蛋糕，必须在 PlayerInteract 里单独拦截
     */
    @EventHandler
    public void onPlayerItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String name = item.getItemMeta().getDisplayName();
            if (name.equals(plugin.getDataManager().medkitName) || 
                name.equals(plugin.getDataManager().bandageName) ||
                name.equals(plugin.getDataManager().medicalBoxName)) {
                event.setCancelled(true);
                event.getPlayer().updateInventory();
                return;
            }
        }
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getWorld().getName().equals("game_1")) {
            event.blockList().clear();
        } else {
            // 在 game_1 中，只允许 TNT 和 火焰弹 破坏地形（防止苦力怕等其他怪物破坏地图）
            if (!(event.getEntity() instanceof org.bukkit.entity.TNTPrimed) && 
                !(event.getEntity() instanceof org.bukkit.entity.Fireball)) {
                event.blockList().clear();
            }

            // 【重要修复】原版恶魂火球的爆炸在 1.8 中默认没有实体溅射伤害！必须手动模拟爆炸伤害
            if (event.getEntity() instanceof org.bukkit.entity.Fireball) {
                org.bukkit.Location loc = event.getLocation();
                for (org.bukkit.entity.Entity e : event.getEntity().getNearbyEntities(4, 4, 4)) {
                    if (e instanceof org.bukkit.entity.Player) {
                        org.bukkit.entity.Player p = (org.bukkit.entity.Player) e;
                        double distance = p.getLocation().distance(loc);
                        if (distance <= 4.0) {
                            // 距离越近伤害越高。最高基础伤害设置为 10.0 (5心)。
                            // 当触发 p.damage() 时，会被下方的 onEntityDamageByExplosion 拦截并放大 2 倍！
                            // 最终最大伤害变成 20.0 (10心)，完美对齐 TNT 的强度。
                            double damage = ((4.0 - distance) / 4.0) * 10.0;
                            if (damage > 0) {
                                p.damage(damage, event.getEntity());
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByExplosion(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!event.getEntity().getWorld().getName().equals("game_1")) return;

        // 如果是玩家受到伤害，独立放大爆炸伤害
        if (event.getEntity() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Entity damager = event.getDamager();
            
            if (damager instanceof org.bukkit.entity.TNTPrimed) {
                // TNT 伤害放大 2 倍
                event.setDamage(event.getDamage() * 2.0);
            } 
            else if (damager instanceof org.bukkit.entity.Fireball) {
                // 火焰弹为了实现“坑小但伤害能媲美原本TNT”，我们将伤害独立放大 2 倍（刚好弥补 yield 差距）
                event.setDamage(event.getDamage() * 2.0);
            }
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!event.getBlock().getWorld().getName().equals("game_1")) {
            event.blockList().clear();
        }
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
        // 修复：打药期间禁止丢弃物品
        if (plugin.getHealingManager().isHealing(event.getPlayer())) {
            event.setCancelled(true);
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

        // 修复：打药期间禁止操作背包
        if (plugin.getHealingManager().isHealing(p)) {
            event.setCancelled(true);
            p.updateInventory();
            return;
        }

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
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            if (plugin.getCurrentState() == GameState.FLIGHT && plugin.getFlightManager().isOnPlane(player)) {
                plugin.getFlightManager().forceJump(player);
            }
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
                    // 【修复】玩家正在打药时，拦截一切交互以阻止右键食物开始吃东西的动画，并刷新背包
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                }

                String name = item.getItemMeta().getDisplayName();

                boolean isBandage = item.getType() == plugin.getDataManager().bandageMaterial
                        && plugin.getDataManager().bandageName.equals(name);
                boolean isMedkit = item.getType() == plugin.getDataManager().medkitMaterial
                        && plugin.getDataManager().medkitName.equals(name);
                boolean isMedicalBox = item.getType() == plugin.getDataManager().medicalBoxMaterial
                        && plugin.getDataManager().medicalBoxName.equals(name);

                if (isBandage) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        plugin.getHealingManager().updateLastInteractTime(player, System.currentTimeMillis());
                        plugin.getHealingManager().startHealing(player, false);
                    } else {
                        plugin.sendActionBar(player, plugin.getMessageManager().getMessage("extra_game.health_full"));
                    }
                    // 【修复】取消 interact 事件的同时必须同步刷新背包，防止客户端视觉上播放吃东西的动画
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                } else if (isMedkit) {
                    if (player.getHealth() < player.getMaxHealth()) {
                        plugin.getHealingManager().updateLastInteractTime(player, System.currentTimeMillis());
                        plugin.getHealingManager().startHealing(player, true);
                    } else {
                        plugin.sendActionBar(player, plugin.getMessageManager().getMessage("extra_game.health_full"));
                    }
                    // 【修复】取消 interact 事件的同时必须同步刷新背包，防止客户端视觉上播放吃东西的动画
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                } else if (isMedicalBox) {
                    if (player.getHealth() < player.getMaxHealth() || player.getFoodLevel() < 20) {
                        plugin.getHealingManager().updateLastInteractTime(player, System.currentTimeMillis());
                        plugin.getHealingManager().startHealingMedicalBox(player);
                    } else {
                        plugin.sendActionBar(player, plugin.getMessageManager().getMessage("extra_game.health_hunger_full"));
                    }
                    // 【修复】取消 interact 事件的同时必须同步刷新背包，防止客户端视觉上播放摆放方块的动画
                    event.setCancelled(true);
                    player.updateInventory();
                    return;
                }
            }

            // 投掷 TNT 逻辑
            if (item != null && item.getType() == Material.TNT) {
                event.setCancelled(true);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.setItemInHand(null);
                    }
                }
                Location eye = player.getEyeLocation();
                org.bukkit.entity.TNTPrimed tnt = player.getWorld().spawn(eye, org.bukkit.entity.TNTPrimed.class);
                tnt.setVelocity(eye.getDirection().multiply(1.2)); // 5格左右的距离，乘数1.2比较合适
                tnt.setFuseTicks(20); // 1秒爆炸
                return;
            }

            // 投掷 烈焰弹 逻辑
            if (item != null && item.getType() == Material.FIREBALL) {
                event.setCancelled(true);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.setItemInHand(null);
                    }
                }
                org.bukkit.entity.Fireball fireball = player.launchProjectile(org.bukkit.entity.Fireball.class);
                fireball.setYield(2.5F); // 设置爆炸威力为2.5（比TNT的4小，但能稳定炸出小坑）
                fireball.setIsIncendiary(true); // 造成火焰
                return;
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
            
            // 处理点击遗物箱告示牌的逻辑
            if (block != null && (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST)) {
                org.bukkit.block.Sign sign = (org.bukkit.block.Sign) block.getState();
                if (plugin.getMessageManager().getMessage("sign.tombstone").equals(sign.getLine(0))) {
                    org.bukkit.material.Sign signData = (org.bukkit.material.Sign) sign.getData();
                    Block attached = block.getRelative(signData.getAttachedFace());
                    if (attached.getType() == Material.CHEST || attached.getType() == Material.TRAPPED_CHEST) {
                        Chest chest = (Chest) attached.getState();
                        player.openInventory(chest.getInventory());
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            if (block != null && (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST)) {
                // 强制允许开箱，无视服务器自带的出生点保护(Spawn Protection)或保护插件导致普通玩家无法开箱的问题
                if (event.isCancelled() || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
                    event.setCancelled(false);
                    event.setUseInteractedBlock(org.bukkit.event.Event.Result.ALLOW);
                }
                Chest chest = (Chest) block.getState();
                org.bukkit.inventory.Inventory inv = chest.getBlockInventory();
                Location loc = chest.getLocation();

                // 如果是双箱子（DoubleChest），我们将 Location 统一规范为 DoubleChest 的公共合成 Location
                // 这可彻底杜绝玩家通过分别右键左右两半边大箱子，导致大箱子被连续刷出两次物资的 Bug
                if (inv instanceof org.bukkit.inventory.DoubleChestInventory) {
                    org.bukkit.block.DoubleChest holder = ((org.bukkit.inventory.DoubleChestInventory) inv).getHolder();
                    if (holder != null) {
                        loc = holder.getLocation();
                    }
                }



                // 恢复空投箱（TRAPPED_CHEST）跳过逻辑，防止空投高阶物资被普通物资覆盖
                if (block.getType() == Material.TRAPPED_CHEST) {
                    return;
                }

                if (!plugin.getLootManager().isChestOpened(loc)) {
                    plugin.getLootManager().populateChest(inv);
                    plugin.getLootManager().markChestOpened(loc);
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
        player.setFoodLevel(19);
        GameState state = plugin.getCurrentState();

        // 如果是游戏中死亡复活的旁观者玩家，确保其没有残留地图且为 SPECTATOR
        if (state == GameState.INGAME || state == GameState.FLIGHT || state == GameState.ENDING) {
            if (plugin.getPlayerManager().isSpectator(player)) {
                org.bukkit.Location deathLoc = deathLocations.get(player.getUniqueId());
                if (deathLoc != null) {
                    event.setRespawnLocation(deathLoc);
                }

                player.getInventory().clear();
                plugin.getPacketMapManager().removeMap(player);

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
                    plugin.getPacketMapManager().giveMap(player);
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
        GameState state = plugin.getCurrentState();
        if (state != GameState.INGAME && state != GameState.FLIGHT) {
            return;
        }

        int alive = plugin.getPlayerManager().getAliveCount();
        int initial = plugin.getGameManager().getInitialPlayerCount();
        if (alive <= 0 || (initial > 1
                && plugin.getTeamManager().isOnlyOneTeamLeft(plugin.getPlayerManager().getAlivePlayers()))) {
            plugin.getGameManager().endGame();
        }
    }

    private void placeSign(Block block, org.bukkit.block.BlockFace face, String playerName) {
        if (block.getType() == Material.AIR || block.getType() == Material.WATER
                || block.getType() == Material.STATIONARY_WATER
                || block.getType() == Material.LONG_GRASS || block.getType() == Material.SNOW
                || block.getType() == Material.DEAD_BUSH
                || block.getType() == Material.YELLOW_FLOWER || block.getType() == Material.RED_ROSE) {
            block.setType(Material.WALL_SIGN);
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.Sign) {
                org.bukkit.block.Sign sign = (org.bukkit.block.Sign) state;
                org.bukkit.material.Sign signData = (org.bukkit.material.Sign) sign.getData();
                signData.setFacingDirection(face);
                sign.setData(signData);
                sign.setLine(0, plugin.getMessageManager().getMessage("sign.tombstone"));
                sign.setLine(1, playerName);
                sign.setLine(2, plugin.getMessageManager().getMessage("sign.tombstone_dead"));
                sign.update(true, false);
            }
        }
    }
}
