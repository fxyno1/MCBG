package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class GameManager {

    private final ChickenDinnerPlugin plugin;

    private int countdownTime;
    private int gameTimeSeconds = 0;

    public int getGameTimeSeconds() {
        return gameTimeSeconds;
    }

    public int getCountdownTime() {
        return countdownTime;
    }
    private int damageTickCounter = 0;
    private BukkitRunnable gameTimer;
    private boolean isPaused = false;
    private int initialPlayerCount = 0;
    
    private final java.util.List<org.bukkit.Location> placedDeathBlocks = new java.util.ArrayList<>();
    private final java.util.Set<org.bukkit.Location> playerPlacedBlocks = new java.util.HashSet<>();

    public void addPlayerPlacedBlock(org.bukkit.Location loc) {
        playerPlacedBlocks.add(loc);
    }

    public void removePlayerPlacedBlock(org.bukkit.Location loc) {
        playerPlacedBlocks.remove(loc);
    }

    public boolean isPlayerPlacedBlock(org.bukkit.Location loc) {
        return playerPlacedBlocks.contains(loc);
    }

    public void clearPlayerPlacedBlocks() {
        for (org.bukkit.Location loc : playerPlacedBlocks) {
            org.bukkit.block.Block b = loc.getBlock();
            if (b.getType() != org.bukkit.Material.AIR) {
                b.setType(org.bukkit.Material.AIR);
            }
        }
        playerPlacedBlocks.clear();
    }

    public void addDeathBlock(org.bukkit.Location loc) {
        placedDeathBlocks.add(loc);
    }

    public void clearDeathBlocks() {
        for (org.bukkit.Location loc : placedDeathBlocks) {
            org.bukkit.block.Block b = loc.getBlock();
            if (b.getType() == org.bukkit.Material.TRAPPED_CHEST || b.getType() == org.bukkit.Material.WALL_SIGN) {
                if (b.getType() == org.bukkit.Material.TRAPPED_CHEST) {
                    org.bukkit.block.BlockState state = b.getState();
                    if (state instanceof org.bukkit.block.Chest) {
                        ((org.bukkit.block.Chest) state).getInventory().clear();
                    }
                }
                b.setType(org.bukkit.Material.AIR);
            }
        }
        placedDeathBlocks.clear();
    }



    public GameManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        this.countdownTime = GameConfig.LOBBY_COUNTDOWN;
        startGameLoop();
    }

    private void startGameLoop() {
        this.gameTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (isPaused)
                    return;

                GameState state = plugin.getCurrentState();
                if (state == GameState.FLIGHT || state == GameState.INGAME) {
                    gameTimeSeconds++;
                }

                switch (state) {
                    case LOBBY:
                        handleLobbyTick();
                        break;
                    case STARTING:
                        handleStartingTick();
                        break;
                    case FLIGHT:
                        handleFlightTick();
                        break;
                    case INGAME:
                        handleInGameTick();
                        break;
                    case ENDING:
                        handleEndingTick();
                        break;
                }
            }
        };
        this.gameTimer.runTaskTimer(plugin, 0L, 20L);
    }

    private void handleLobbyTick() {
        int currentPlayers = plugin.getPlayerManager().getAliveCount();

        if (currentPlayers < 6) {
            this.countdownTime = 30;
            Bukkit.getOnlinePlayers().forEach(p -> {
                plugin.sendActionBar(p, "§e等待玩家加入... §a" + currentPlayers + "§f/§a30");
            });
            return;
        }

        // 人数满 30 人时缩短倒计时至 15 秒
        if (currentPlayers >= 30 && this.countdownTime > 15) {
            this.countdownTime = 15;
            Bukkit.broadcastMessage("§a[游戏广播] 人数已达 30 人，倒计时缩短至 15 秒！");
        }

        countdownTime--;

        if (countdownTime > 0) {
            boolean isKeyNode = (countdownTime == 10 || countdownTime <= 5);
            if (isKeyNode) {
                Bukkit.broadcastMessage("§e[游戏广播] 距离比赛开始还剩 §c" + countdownTime + " §e秒！");
            }
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (isKeyNode) {
                    plugin.sendTitle(p, "§c" + countdownTime, "§e准备开战!", 0, 20, 0);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ORB_PICKUP, 1F, 1F);
                }

                plugin.sendActionBar(p, "§a即将开始: §e" + countdownTime + "秒");

                if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                        && (p.getInventory().getItem(4) == null
                                || p.getInventory().getItem(4).getType() != org.bukkit.Material.MAP)) {
                    plugin.getPacketMapManager().giveMap(p);
                    org.bukkit.inventory.ItemStack mapItem = p.getInventory().getItem(0);
                    if (mapItem != null && mapItem.getType() == org.bukkit.Material.MAP) {
                        p.getInventory().setItem(0, null);
                        p.getInventory().setItem(4, mapItem);
                    }
                    p.updateInventory();
                }
            });
        }

        if (countdownTime <= 0) {
            plugin.setCurrentState(GameState.STARTING);
            // 提前生成随机飞行航线，让存活玩家在 STARTING_COUNTDOWN 秒“准备起飞”的匹配阶段就能在雷达地图上看到并开始策划落点！
            plugin.getFlightManager().prepareFlightPath();

            // 随机分配未选队玩家
            plugin.getTeamManager().autoAssignUnassignedPlayers(plugin.getPlayerManager().getAlivePlayers());

            // 强刷一次所有在线玩家的雷达地图，确保航线在开始匹配时立即被渲染
            plugin.getPacketMapManager().clearAll();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    continue;
                }
                plugin.getPacketMapManager().removeMap(p);
                plugin.getPacketMapManager().giveMap(p);
                org.bukkit.inventory.ItemStack mapItem = p.getInventory().getItem(0);
                if (mapItem != null && mapItem.getType() == org.bukkit.Material.MAP) {
                    p.getInventory().setItem(0, null);
                    p.getInventory().setItem(4, mapItem);
                }
                p.updateInventory();
            }

            this.countdownTime = GameConfig.STARTING_COUNTDOWN;
        }
    }

    private void handleStartingTick() {
        countdownTime--;

        // 匹配航线等待阶段保证所有存活玩家背包里有雷达地图（不强切手持槽）
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && (p.getInventory().getItem(4) == null
                            || p.getInventory().getItem(4).getType() != org.bukkit.Material.MAP)) {
                plugin.getPacketMapManager().giveMap(p);
                org.bukkit.inventory.ItemStack mapItem = p.getInventory().getItem(0);
                if (mapItem != null && mapItem.getType() == org.bukkit.Material.MAP) {
                    p.getInventory().setItem(0, null);
                    p.getInventory().setItem(4, mapItem);
                }
                p.updateInventory();
            }
        });

        if (countdownTime > 0) {
            Bukkit.broadcastMessage("§a[游戏广播] 正在匹配航线... 准备起飞: §6" + countdownTime);
        } else {
            plugin.setCurrentState(GameState.FLIGHT);
            this.countdownTime = GameConfig.FLIGHT_PHASE_COUNTDOWN;

            plugin.getFlightManager().startFlight(plugin.getPlayerManager().getAlivePlayers());
            this.initialPlayerCount = plugin.getPlayerManager().getAliveCount();

            // 为所有参赛玩家强力设置最大生命值为 40.0 并回满血
            for (java.util.UUID pid : plugin.getPlayerManager().getAlivePlayers()) {
                Player p = Bukkit.getPlayer(pid);
                if (p != null && p.isOnline()) {
                    p.setMaxHealth(40.0);
                    p.setHealth(40.0);
                }
            }

            plugin.initZoneManager();

            Bukkit.broadcastMessage("§e[游戏广播] 比赛正式开始！你有 1 分钟的安全时间搜刮物资，毒圈随后开始收缩！");
        }
    }

    private void handleFlightTick() {
        countdownTime--;
        if (countdownTime <= 0) {
            plugin.setCurrentState(GameState.INGAME);
            this.countdownTime = GameConfig.INGAME_FIRST_WAIT;

            // 安全调用：确保 zoneManager 已初始化，并且在此刻（全部落地）时才首次生成第一波安全区（白圈）！
            edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
            if (zm != null) {
                zm.generateNextZone(); // 生成第一波安全区（直径 400）
            }

            Bukkit.broadcastMessage("§e[安全区] 飞机航线已结束，全部玩家已安全落地！");
            Bukkit.broadcastMessage("§a§l[雷达系统] 第一波安全区（白色区域）已在您的 GPS 雷达中公布！");
            Bukkit.broadcastMessage("§e[安全区] 毒圈将在 150 秒后开始向白圈进行首次收缩！");
        }
    }

    private void handleInGameTick() {
        int alive = plugin.getPlayerManager().getAliveCount();
        if (alive <= 0 || (this.initialPlayerCount > 1 && plugin.getTeamManager().isOnlyOneTeamLeft(plugin.getPlayerManager().getAlivePlayers()))) {
            endGame();
            return;
        }

        damageTickCounter++;
        if (damageTickCounter >= 2) {
            damageTickCounter = 0;
            edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
            if (zm != null) {
                zm.applyZoneDamage();
            }
        }

        edu.mc.manager.ZoneManager zm = plugin.getZoneManager();

        // 【关键修复】当前波次毒圈仍在收缩动画中时，暂停下一波倒数与提示
        // 防止出现"距离下次收缩还有60秒"但上一波收缩还没结束的矛盾情况
        if (zm != null && zm.isShrinking()) {
            return;
        }

        countdownTime--;

        // 优化：只在关键时间点提醒，不刷屏
        if (countdownTime == GameConfig.INGAME_BETWEEN_SHRINK_WAIT - 1) { // 刚刚产生下一圈时（扣了1秒）
            Bukkit.broadcastMessage("§e[安全区] 新的安全区已在雷达地图上标记，毒圈将在一段时间后开始收缩！");
            Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, "§a新安全区已刷新", "§e请查看战术雷达！", 10, 60, 10));
        } else if (countdownTime == 60) {
            Bukkit.broadcastMessage("§e[安全区] 距离毒圈收缩还有 §c1分钟 §e！");
            Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, "§c毒圈逼近", "§e距离缩圈还有 1 分钟", 10, 40, 10));
        } else if (countdownTime == 30) {
            Bukkit.broadcastMessage("§c[警告] 距离毒圈收缩还有 §e30秒 §c！");
            Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, "§c危险警告", "§e毒圈将在 30 秒后收缩", 10, 40, 10));
        } else if (countdownTime <= 0) {
            if (zm != null && !zm.isMaxPhase()) {
                zm.shrinkToNextPhase();
                this.countdownTime = GameConfig.INGAME_BETWEEN_SHRINK_WAIT;
                Bukkit.broadcastMessage("§c[安全区] 毒圈开始收缩，请尽快进入白圈！");
                Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, "§4毒圈开始收缩", "§c快跑！", 10, 60, 10));
            } else {
                this.countdownTime = 9999;
            }
        }
    }

    private void handleEndingTick() {
        countdownTime--;

        if (countdownTime == 9) {
            edu.mc.manager.TeamManager.TeamInfo winner = plugin.getTeamManager().getWinningTeam(plugin.getPlayerManager().getAlivePlayers());
            if (winner != null) {
                Bukkit.broadcastMessage("§a§l大吉大利，今晚吃鸡！");
                Bukkit.broadcastMessage("§e获胜队伍是: " + winner.chatColor + winner.name);
                for (java.util.UUID uuid : plugin.getTeamManager().getPlayersInTeam(winner.id)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        plugin.sendTitle(p, "§6大吉大利", "§e今晚吃鸡", 10, 60, 10);
                    }
                }
            } else {
                Bukkit.broadcastMessage("§7游戏结束，没有胜者。");
            }
        }

        if (countdownTime <= 0) {
            Bukkit.broadcastMessage("§a游戏即将开始新的一局！");

            // 重置各个管理器
            plugin.getPlayerManager().reset();
            plugin.getLootManager().reset();
            edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
            if (zm != null) {
                zm.reset();
            }
            plugin.getFlightManager().reset();
            plugin.getAirdropManager().reset();
            plugin.getTeamManager().reset();
            this.clearDeathBlocks();
            this.clearPlayerPlacedBlocks();

            // 清理地面上的掉落物（如战利品、丢弃的装备和物品等）
            org.bukkit.World world = Bukkit.getWorlds().get(0);
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Item) {
                    entity.remove();
                }
            }

            plugin.setCurrentState(GameState.LOBBY);
            this.countdownTime = GameConfig.LOBBY_COUNTDOWN;
            this.damageTickCounter = 0;
            this.initialPlayerCount = 0;
            this.gameTimeSeconds = 0;

            // 【修改】每局游戏结束开始下一把时，彻底清空并删除地图缓存文件，实现重新绘制
            plugin.getPacketMapManager().clearAndResetMapFiles();

            world.getWorldBorder().reset();

            org.bukkit.Location lobbyLoc = new org.bukkit.Location(world,
                    GameConfig.LOBBY_X, GameConfig.LOBBY_Y, GameConfig.LOBBY_Z);
            // ... 在 GameManager.java 的 handleEndingTick() 回归大厅的遍历玩家循环中：
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    continue;
                }

                p.setGameMode(org.bukkit.GameMode.SURVIVAL);
                plugin.getPacketMapManager().removeMap(p);

                p.getInventory().clear();

                // 第一次塞入地图（动态绑定或新建）
                plugin.getPacketMapManager().giveMap(p);
                
                org.bukkit.inventory.ItemStack mapItem = p.getInventory().getItem(0);
                if (mapItem != null && mapItem.getType() == org.bukkit.Material.MAP) {
                    p.getInventory().setItem(0, null);
                    p.getInventory().setItem(4, mapItem);
                }
                
                org.bukkit.inventory.ItemStack paper = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PAPER);
                org.bukkit.inventory.meta.ItemMeta paperMeta = paper.getItemMeta();
                paperMeta.setDisplayName("§a选队");
                paper.setItemMeta(paperMeta);
                p.getInventory().setItem(0, paper);

                org.bukkit.inventory.ItemStack feather = new org.bukkit.inventory.ItemStack(org.bukkit.Material.FEATHER);
                org.bukkit.inventory.meta.ItemMeta featherMeta = feather.getItemMeta();
                featherMeta.setDisplayName("§c退出大厅");
                feather.setItemMeta(featherMeta);
                p.getInventory().setItem(8, feather);

                p.getInventory().setHeldItemSlot(0);
                p.updateInventory();
                // 【开局重绘强刷】延迟 5 ticks 再次冲刷新包
                final Player finalP = p;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (finalP.isOnline() && plugin.getCurrentState() == GameState.LOBBY) {
                        plugin.getPacketMapManager().removeMap(finalP);
                        plugin.getPacketMapManager().giveMap(finalP);
                        finalP.updateInventory();
                    }
                }, 5L);
                p.getInventory().setArmorContents(null);
                // ... 下方保持原样 ...
                p.setFoodLevel(19);
                p.setMaxHealth(40.0); // 确保重置回大厅时也是 40.0 最大血量
                p.setHealth(40.0);
                p.setFireTicks(0);
                p.setFallDistance(0f);
                p.setWalkSpeed(0.2f);
                p.setFlySpeed(0.1f);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setLevel(0);
                p.setExp(0);

                p.teleport(lobbyLoc);
                plugin.getPlayerManager().addPlayer(p);
            }

            // 延迟 30 ticks (约 1.5秒) 等待所有玩家传送并进入大厅后，强力执行一次类似 /cd resetmap 的全局地图重置逻辑
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getCurrentState() == GameState.LOBBY) {
                    plugin.getPacketMapManager().clearAll();
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                            continue;
                        }
                        plugin.getPacketMapManager().removeMap(p);
                        plugin.getPacketMapManager().giveMap(p);
                        p.updateInventory();
                    }
                    Bukkit.broadcastMessage("§a§l[系统] 已自动重置并刷新所有玩家的战术GPS雷达地图！");
                }
            }, 30L);
        }
    }

    public void endGame() {
        if (plugin.getCurrentState() != GameState.ENDING) {
            plugin.setCurrentState(GameState.ENDING);
            this.countdownTime = GameConfig.ENDING_COUNTDOWN;

            // 立即停止缩圈任务并重置世界边界为默认大边界，消除红幕警告
            edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
            if (zm != null) {
                zm.reset();
            }
            org.bukkit.World world = Bukkit.getWorlds().get(0);
            world.getWorldBorder().reset();
        }
    }

    public void stopTimer() {
        if (this.gameTimer != null) {
            this.gameTimer.cancel();
        }
    }

    public boolean togglePause() {
        this.isPaused = !this.isPaused;
        if (isPaused) {
            Bukkit.broadcastMessage("§c[系统] 比赛倒计时已暂停！");
        } else {
            Bukkit.broadcastMessage("§a[系统] 比赛倒计时已恢复！");
        }
        return this.isPaused;
    }

    public void forceStart() {
        if (plugin.getCurrentState() == GameState.LOBBY) {
            plugin.setCurrentState(GameState.STARTING);
            plugin.getFlightManager().prepareFlightPath();
            plugin.getTeamManager().autoAssignUnassignedPlayers(plugin.getPlayerManager().getAlivePlayers());

            // 强刷一次所有在线玩家的雷达地图，确保航线在开始匹配时立即被渲染
            plugin.getPacketMapManager().clearAll();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    continue;
                }
                plugin.getPacketMapManager().removeMap(p);
                plugin.getPacketMapManager().giveMap(p);
                org.bukkit.inventory.ItemStack mapItem = p.getInventory().getItem(0);
                if (mapItem != null && mapItem.getType() == org.bukkit.Material.MAP) {
                    p.getInventory().setItem(0, null);
                    p.getInventory().setItem(4, mapItem);
                }
                p.updateInventory();
            }

            this.countdownTime = GameConfig.STARTING_COUNTDOWN;
            Bukkit.broadcastMessage("§a[系统] 管理员强制开启了比赛！正在生成航线...");
        }
    }

    public void skipPhase() {
        this.countdownTime = 0;
        Bukkit.broadcastMessage("§a[系统] 管理员跳过了当前阶段的等待！");
    }

    public void setCountdownTime(int seconds) {
        this.countdownTime = seconds;
        Bukkit.broadcastMessage("§a[系统] 管理员调整当前阶段倒计时为: §e" + seconds + "§a秒！");
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    public int getInitialPlayerCount() {
        return this.initialPlayerCount;
    }
}
// Force IDE refresh
