package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class GameManager {

    private final ChickenDinnerPlugin plugin;

    private int countdownTime;
    private int damageTickCounter = 0;
    private BukkitRunnable gameTimer;
    private boolean isPaused = false;

    private final int minPlayers = 2;

    public GameManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        this.countdownTime = 30;
        startGameLoop();
    }

    private void startGameLoop() {
        this.gameTimer = new BukkitRunnable() {
            @Override
            public void run() {
                if (isPaused) return;
                
                switch (plugin.getCurrentState()) {
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

        if (currentPlayers < minPlayers) {
            this.countdownTime = 30;
            return;
        }

        countdownTime--;

        if (countdownTime > 0) {
            // 仅在关键节点广播，避免每秒刷屏
            if (countdownTime == 30 || countdownTime == 20 || countdownTime == 10
                    || countdownTime <= 5) {
                Bukkit.broadcastMessage("§e[游戏广播] 距离比赛开始还剩 §c" + countdownTime + " §e秒！");
            }
            Bukkit.getOnlinePlayers().forEach(p -> {
                plugin.sendTitle(p, "§c" + countdownTime, "§e准备开战!", 0, 20, 0);
                p.playSound(p.getLocation(), org.bukkit.Sound.ORB_PICKUP, 1F, 1F);
                
                // 只给存活（非旁观）玩家发放雷达地图，不强制切换手持槽
                if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                        && (p.getInventory().getItem(0) == null
                            || p.getInventory().getItem(0).getType() != org.bukkit.Material.MAP)) {
                    p.getInventory().setItem(0, plugin.createRadarMap(p.getWorld()));
                    p.updateInventory();
                }
            });
        }

        if (countdownTime <= 0) {
            plugin.setCurrentState(GameState.STARTING);
            this.countdownTime = 3;
        }
    }

    private void handleStartingTick() {
        countdownTime--;
        
        // 匹配航线等待阶段保证所有存活玩家背包里有雷达地图（不强切手持槽）
        Bukkit.getOnlinePlayers().forEach(p -> {
            if (p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && (p.getInventory().getItem(0) == null
                        || p.getInventory().getItem(0).getType() != org.bukkit.Material.MAP)) {
                p.getInventory().setItem(0, plugin.createRadarMap(p.getWorld()));
                p.updateInventory();
            }
        });

        if (countdownTime > 0) {
            Bukkit.broadcastMessage("§a[游戏广播] 正在匹配航线... 准备起飞: §6" + countdownTime);
        } else {
            plugin.setCurrentState(GameState.FLIGHT);
            this.countdownTime = 60;
            
            plugin.getFlightManager().startFlight(plugin.getPlayerManager().getAlivePlayers());
            
            plugin.initZoneManager();
            
            Bukkit.broadcastMessage("§e[游戏广播] 比赛正式开始！你有 1 分钟的安全时间搜刮物资，毒圈随后开始收缩！");
        }
    }

    private void handleFlightTick() {
        countdownTime--;
        if (countdownTime <= 0) {
            plugin.setCurrentState(GameState.INGAME);
            this.countdownTime = 150; // 150 秒的首圈安全搜刮期开始！
            
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
        if (plugin.getPlayerManager().getAliveCount() <= 1) {
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

        countdownTime--;
        
        if (countdownTime == 60) {
            Bukkit.broadcastMessage("§e[安全区] 距离毒圈收缩还有 1 分钟！");
        } else if (countdownTime == 30) {
            Bukkit.broadcastMessage("§e[安全区] 距离毒圈收缩还有 30 秒！");
        } else if (countdownTime == 10) {
            Bukkit.broadcastMessage("§c[安全区] 距离毒圈收缩还有 10 秒！");
        } else if (countdownTime <= 0) {
            edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
            if (zm != null && !zm.isMaxPhase()) {
                zm.shrinkToNextPhase();
                this.countdownTime = 150;
            } else {
                this.countdownTime = 9999;
            }
        }
    }

    private void handleEndingTick() {
        countdownTime--;
        
        if (countdownTime == 9) {
            Player winner = plugin.getPlayerManager().getWinner();
            if (winner != null) {
                Bukkit.broadcastMessage("§a§l大吉大利，今晚吃鸡！");
                Bukkit.broadcastMessage("§e获胜者是: §6" + winner.getName());
                plugin.sendTitle(winner, "§6大吉大利", "§e今晚吃鸡", 10, 60, 10);
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
            
            plugin.setCurrentState(GameState.LOBBY);
            this.countdownTime = 30;
            this.damageTickCounter = 0;
            
            org.bukkit.World world = Bukkit.getWorlds().get(0);
            world.getWorldBorder().reset();
            
            org.bukkit.Location lobbyLoc = new org.bukkit.Location(world, 1387.5, 226.5, 21.5);
            for (Player p : Bukkit.getOnlinePlayers()) {
                // 清理玩家状态
                p.getInventory().clear();
                p.getInventory().setItem(0, plugin.createRadarMap(world));
                p.getInventory().setHeldItemSlot(0);
                p.updateInventory();
                p.getInventory().setArmorContents(null);
                p.setFoodLevel(20);
                p.setHealth(p.getMaxHealth());
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
        }
    }

    public void endGame() {
        if (plugin.getCurrentState() != GameState.ENDING) {
            plugin.setCurrentState(GameState.ENDING);
            this.countdownTime = 10;
            
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
            this.countdownTime = 3;
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
}
