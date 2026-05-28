package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ScoreboardManager {

    private final ChickenDinnerPlugin plugin;
    private final Map<UUID, Scoreboard> playerScoreboards = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> lastLines = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask updateTask;

    public ScoreboardManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        updateTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.isOnline()) {
                        updateScoreboard(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // 每 10 ticks (0.5秒) 更新一次，提供顺滑的方向指针
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        playerScoreboards.clear();
        lastLines.clear();
    }

    public void removePlayer(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        lastLines.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void updateScoreboard(Player player) {
        UUID uuid = player.getUniqueId();
        Scoreboard scoreboard = playerScoreboards.get(uuid);
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = scoreboard.registerNewObjective("mcbg", "dummy");
            obj.setDisplayName("§e§l代号:吃鸡");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerScoreboards.put(uuid, scoreboard);
            player.setScoreboard(scoreboard);
        }

        Objective obj = scoreboard.getObjective("mcbg");
        if (obj == null) {
            obj = scoreboard.registerNewObjective("mcbg", "dummy");
            obj.setDisplayName("§e§l代号:吃鸡");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        List<String> newLines = new ArrayList<>();
        GameState state = plugin.getCurrentState();

        if (state == GameState.LOBBY || state == GameState.STARTING) {
            // 模式
            String modeStr = "§f模式:  §a单人";
            // 玩家: X/MAX
            String playersStr = "§f玩家:  §a" + plugin.getPlayerManager().getAliveCount() + "/30";
            // 状态
            String statusStr = "§f状态:  §a可加入...";

            newLines.add("§1");
            newLines.add(modeStr);
            newLines.add(playersStr);
            newLines.add(statusStr);
            newLines.add("§2");
            newLines.add("§a游戏即将开始");
            newLines.add("§3");
            newLines.add("  §e✿§e花雨庭§e✿");
        } else {
            // 1. 游戏时间
            String timeStr = "§f游戏时间: §a00:00";
            if (state == GameState.FLIGHT || state == GameState.INGAME) {
                int totalSeconds = plugin.getGameManager().getGameTimeSeconds();
                int min = totalSeconds / 60;
                int sec = totalSeconds % 60;
                timeStr = String.format("§f游戏时间: §a%02d:%02d", min, sec);
            } else if (state == GameState.ENDING) {
                timeStr = "§f游戏时间: §a已结束";
            }

            // 2. 击杀数
            int kills = plugin.getPlayerManager().getKills(player);
            String killsStr = "§f击杀数: §a" + kills;

            // 3. 存活玩家
            int aliveCount = plugin.getPlayerManager().getAliveCount();
            String aliveStr = "§f存活玩家: §a" + aliveCount;

            // 4. 缩圈时间
            String shrinkTimeStr = "§f缩圈时间: §a--";
            if (state == GameState.FLIGHT) {
                shrinkTimeStr = "§f缩圈时间: §a起飞中";
            } else if (state == GameState.INGAME) {
                edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
                if (zm != null) {
                    if (zm.isShrinking()) {
                        shrinkTimeStr = "§f缩圈时间: §a收缩中";
                    } else {
                        shrinkTimeStr = "§f缩圈时间: §a" + plugin.getGameManager().getCountdownTime();
                    }
                }
            } else if (state == GameState.ENDING) {
                shrinkTimeStr = "§f缩圈时间: §a已结束";
            }

            // 5. 中心位置 (安全区圆心方向)
            String centerStr = "§f中心位置: §a-";
            if (state == GameState.INGAME || state == GameState.FLIGHT) {
                edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
                if (zm != null) {
                    centerStr = "§f中心位置: " + getDirectionArrow(player, zm.getTargetX(), zm.getTargetZ());
                }
            }

            // 6. 边界大小
            String borderSizeStr = "§f边界大小: §a600";
            if (player.getWorld() != null) {
                borderSizeStr = "§f边界大小: §a" + (int) player.getWorld().getWorldBorder().getSize();
            }

            newLines.add("§1");
            newLines.add(timeStr);
            newLines.add(killsStr);
            newLines.add(aliveStr);
            newLines.add("§2");
            newLines.add(shrinkTimeStr);
            newLines.add(centerStr);
            newLines.add(borderSizeStr);
            newLines.add("§3");
            newLines.add("  §e✿§e花雨庭§e✿");
        }

        // --- 同步各个玩家头上的队伍颜色 ---
        for (Player p : Bukkit.getOnlinePlayers()) {
            Integer teamId = plugin.getTeamManager().getTeam(p.getUniqueId());
            if (teamId != null) {
                edu.mc.manager.TeamManager.TeamInfo info = plugin.getTeamManager().getTeamInfo(teamId);
                String teamName = "team_" + info.id;
                org.bukkit.scoreboard.Team sbTeam = scoreboard.getTeam(teamName);
                if (sbTeam == null) {
                    sbTeam = scoreboard.registerNewTeam(teamName);
                    sbTeam.setPrefix(info.chatColor);
                }
                if (!sbTeam.hasEntry(p.getName())) {
                    sbTeam.addEntry(p.getName());
                }
            }
        }
        // ------------------------------------

        List<String> oldLines = lastLines.get(uuid);
        if (oldLines == null) {
            oldLines = new ArrayList<>();
        }

        // 如果计分板的行数发生变化，重置所有旧行分数，防止索引错位和排序混乱
        if (oldLines.size() != newLines.size()) {
            for (String oldLine : oldLines) {
                scoreboard.resetScores(oldLine);
            }
            oldLines.clear();
        }

        // 差量更新，防止计分板闪烁
        int totalScoreLines = newLines.size();
        for (int i = 0; i < totalScoreLines; i++) {
            String newLine = newLines.get(i);
            int score = totalScoreLines - i; // 每一行的分数，比如 10, 9, 8... 1

            if (oldLines.size() <= i) {
                obj.getScore(newLine).setScore(score);
            } else {
                String oldLine = oldLines.get(i);
                if (!oldLine.equals(newLine)) {
                    scoreboard.resetScores(oldLine);
                    obj.getScore(newLine).setScore(score);
                }
            }
        }

        if (oldLines.size() > newLines.size()) {
            for (int i = newLines.size(); i < oldLines.size(); i++) {
                scoreboard.resetScores(oldLines.get(i));
            }
        }

        lastLines.put(uuid, newLines);
    }

    private String getDirectionArrow(Player player, double targetX, double targetZ) {
        Location loc = player.getLocation();
        double dx = targetX - loc.getX();
        double dz = targetZ - loc.getZ();

        if (dx * dx + dz * dz < 25.0) {
            return "§a✔"; // 已经到达中心区域
        }

        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double playerYaw = loc.getYaw();

        double relativeAngle = (angleToTarget - playerYaw) % 360;
        if (relativeAngle < 0) {
            relativeAngle += 360;
        }

        if (relativeAngle >= 337.5 || relativeAngle < 22.5) {
            return "§a⬆";
        } else if (relativeAngle >= 22.5 && relativeAngle < 67.5) {
            return "§a⬈";
        } else if (relativeAngle >= 67.5 && relativeAngle < 112.5) {
            return "§a➡";
        } else if (relativeAngle >= 112.5 && relativeAngle < 157.5) {
            return "§a⬊";
        } else if (relativeAngle >= 157.5 && relativeAngle < 202.5) {
            return "§a⬇";
        } else if (relativeAngle >= 202.5 && relativeAngle < 247.5) {
            return "§a⬋";
        } else if (relativeAngle >= 247.5 && relativeAngle < 292.5) {
            return "§a⬅";
        } else {
            return "§a⬉";
        }
    }
}
