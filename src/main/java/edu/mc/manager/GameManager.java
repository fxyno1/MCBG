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
    
    // No block tracking needed for multi-world



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
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.countdown_short"));
        }

        countdownTime--;

        if (countdownTime > 0) {
            boolean isKeyNode = (countdownTime == 10 || countdownTime <= 5);
            if (isKeyNode) {
                java.util.Map<String, String> map = new java.util.HashMap<>();
                map.put("time", String.valueOf(countdownTime));
                Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.countdown", map));
            }
            Bukkit.getOnlinePlayers().forEach(p -> {
                if (isKeyNode) {
                    java.util.Map<String, String> map = new java.util.HashMap<>();
                    map.put("time", String.valueOf(countdownTime));
                    plugin.sendTitle(p, plugin.getMessageManager().getMessage("game.title_countdown", map), plugin.getMessageManager().getMessage("game.title_countdown_sub"), 0, 20, 0);
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
            // 每次开局前创建临时克隆世界
            plugin.getWorldManager().createGameWorld();

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
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("time", String.valueOf(countdownTime));
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.matching", map));
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

            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.start"));
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

            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.landed"));
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.zone_first_radar"));
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("time", String.valueOf(GameConfig.INGAME_FIRST_WAIT));
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.zone_first_wait", map));
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
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("zone.new_zone"));
            Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, plugin.getMessageManager().getMessage("zone.title_new_zone"), plugin.getMessageManager().getMessage("zone.title_new_zone_sub"), 10, 60, 10));
        } else if (countdownTime == 60) {
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("zone.warn_1min"));
            Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, plugin.getMessageManager().getMessage("zone.title_warn_1min"), plugin.getMessageManager().getMessage("zone.title_warn_1min_sub"), 10, 40, 10));
        } else if (countdownTime == 30) {
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("zone.warn_30s"));
            Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, plugin.getMessageManager().getMessage("zone.title_warn_30s"), plugin.getMessageManager().getMessage("zone.title_warn_30s_sub"), 10, 40, 10));
        } else if (countdownTime <= 0) {
            if (zm != null && !zm.isMaxPhase()) {
                zm.shrinkToNextPhase();
                this.countdownTime = GameConfig.INGAME_BETWEEN_SHRINK_WAIT;
                Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("zone.shrinking"));
                Bukkit.getOnlinePlayers().forEach(p -> plugin.sendTitle(p, plugin.getMessageManager().getMessage("zone.title_shrinking"), plugin.getMessageManager().getMessage("zone.title_shrinking_sub"), 10, 60, 10));
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
                Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.win_broadcast"));
                java.util.Map<String, String> map = new java.util.HashMap<>();
                map.put("team", winner.chatColor + winner.name);
                Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.winner", map));
                for (java.util.UUID uuid : plugin.getTeamManager().getPlayersInTeam(winner.id)) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        plugin.sendTitle(p, plugin.getMessageManager().getMessage("game.win_title"), plugin.getMessageManager().getMessage("game.win_subtitle"), 10, 60, 10);
                    }
                }
            } else {
                Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.no_winner"));
            }
        }

        if (countdownTime <= 0) {
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("game.restarting"));

            // 重置各个管理器
            plugin.getPlayerManager().reset();
            plugin.getLootManager().reset();
            plugin.clearZoneManager();
            plugin.getFlightManager().reset();
            plugin.getAirdropManager().reset();
            plugin.getTeamManager().reset();

            // 删档：卸载并彻底删除游戏世界
            plugin.getWorldManager().deleteGameWorld();

            plugin.setCurrentState(GameState.LOBBY);
            this.countdownTime = GameConfig.LOBBY_COUNTDOWN;
            this.damageTickCounter = 0;
            this.initialPlayerCount = 0;
            this.gameTimeSeconds = 0;

            // 【修改】每局游戏结束开始下一把时，彻底清空并删除地图缓存文件，实现重新绘制
            plugin.getPacketMapManager().clearAndResetMapFiles();

            org.bukkit.World world = Bukkit.getWorlds().get(0);
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
                
                // 【开局重绘强刷】延迟 10 ticks 再次冲刷新包，并二次强制清空背包，防止跨世界传送导致背包不同步（如 Multiverse 等插件）
                final Player finalP = p;
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (finalP.isOnline() && plugin.getCurrentState() == GameState.LOBBY) {
                        finalP.getInventory().clear();
                        finalP.getInventory().setArmorContents(null);
                        
                        plugin.getPacketMapManager().removeMap(finalP);
                        plugin.getPacketMapManager().giveMap(finalP);
                        
                        org.bukkit.inventory.ItemStack delayedMap = finalP.getInventory().getItem(0);
                        if (delayedMap != null && delayedMap.getType() == org.bukkit.Material.MAP) {
                            finalP.getInventory().setItem(0, null);
                            finalP.getInventory().setItem(4, delayedMap);
                        }
                        
                        finalP.getInventory().setItem(0, paper);
                        finalP.getInventory().setItem(8, feather);
                        finalP.getInventory().setHeldItemSlot(0);
                        finalP.updateInventory();
                    }
                }, 10L);
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
                    Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("system.radar_reset"));
                }
            }, 30L);
        }
    }

    public void endGame() {
        if (plugin.getCurrentState() != GameState.ENDING) {
            plugin.setCurrentState(GameState.ENDING);
            this.countdownTime = GameConfig.ENDING_COUNTDOWN;

            // 立即停止缩圈任务并重置世界边界为默认大边界，消除红幕警告
            plugin.clearZoneManager();
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
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("system.pause"));
        } else {
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("system.resume"));
        }
        return this.isPaused;
    }

    public void forceStart() {
        if (plugin.getCurrentState() == GameState.LOBBY) {
            // 管理员强制开始，也要创建地图
            plugin.getWorldManager().createGameWorld();

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
            Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("system.force_start"));
        }
    }

    public void skipPhase() {
        this.countdownTime = 0;
        Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("system.skip_wait"));
    }

    public void setCountdownTime(int seconds) {
        this.countdownTime = seconds;
        java.util.Map<String, String> map = new java.util.HashMap<>();
        map.put("time", String.valueOf(seconds));
        Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("system.set_time", map));
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
