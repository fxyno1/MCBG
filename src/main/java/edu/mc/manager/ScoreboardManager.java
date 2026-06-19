package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

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
    private FileConfiguration config;

    public ScoreboardManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.exists()) {
            plugin.saveResource("scoreboard.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }
    
    private String getStr(String path) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', config.getString(path, ""));
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
        String mainTitle = getStr("title");
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = scoreboard.registerNewObjective("mcbg", "dummy");
            obj.setDisplayName(mainTitle);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            playerScoreboards.put(uuid, scoreboard);
            player.setScoreboard(scoreboard);
        }

        Objective obj = scoreboard.getObjective("mcbg");
        if (obj == null) {
            obj = scoreboard.registerNewObjective("mcbg", "dummy");
            obj.setDisplayName(mainTitle);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        // Force title update if changed
        if (!obj.getDisplayName().equals(mainTitle)) {
            obj.setDisplayName(mainTitle);
        }

        List<String> newLines = new ArrayList<>();
        GameState state = plugin.getCurrentState();

        if (state == GameState.LOBBY || state == GameState.STARTING) {
            // 模式
            String modeStr = getStr("lines.mode.solo");
            // 玩家: X/MAX
            String playersStr = getStr("lines.wait").replace("{players}", String.valueOf(plugin.getPlayerManager().getAliveCount())).replace("{max_players}", "30");
            // 状态
            String statusStr = getStr("lines.status.waiting");

            newLines.add("§1");
            newLines.add(modeStr);
            newLines.add(playersStr);
            newLines.add(statusStr);
            newLines.add("§2");
            for (String footerLine : config.getStringList("lines.footer")) {
                newLines.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', footerLine));
            }
        } else {
            // 1. 游戏时间 (using a hardcoded generic timer for now, or adapt later if there's a specific format in scoreboard.yml. Since we didn't add game time format in yml, I'll keep the logic but translate it if we added it, wait I didn't add it in scoreboard.yml, let me just add it implicitly or hardcode the logic for time).
            int totalSeconds = plugin.getGameManager().getGameTimeSeconds();
            int min = totalSeconds / 60;
            int sec = totalSeconds % 60;
            String timeStr = "§f游戏时间: §a" + String.format("%02d:%02d", min, sec);
            if (state == GameState.ENDING) {
                timeStr = "§f游戏时间: §a已结束";
            }

            // 2. 击杀数
            int kills = plugin.getPlayerManager().getKills(player);
            String killsStr = getStr("lines.kills").replace("{kills}", String.valueOf(kills));

            // 3. 存活玩家
            int aliveCount = plugin.getPlayerManager().getAliveCount();
            String aliveStr = getStr("lines.alive").replace("{alive}", String.valueOf(aliveCount));

            // 4. 缩圈时间
            String shrinkTimeStr = "§f缩圈时间: §a--";
            if (state == GameState.FLIGHT) {
                shrinkTimeStr = "§f缩圈时间: §a起飞中";
            } else if (state == GameState.INGAME) {
                edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
                if (zm != null) {
                    if (zm.isShrinking()) {
                        shrinkTimeStr = getStr("lines.zone.shrinking");
                    } else {
                        shrinkTimeStr = getStr("lines.zone.wait").replace("{time}", String.valueOf(plugin.getGameManager().getCountdownTime()));
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
            for (String footerLine : config.getStringList("lines.footer")) {
                newLines.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', footerLine));
            }
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
            } else {
                // 如果玩家不再有队伍，必须从记分板队伍中移除他们，否则头顶的颜色会一直残留
                org.bukkit.scoreboard.Team sbTeam = scoreboard.getEntryTeam(p.getName());
                if (sbTeam != null) {
                    sbTeam.removeEntry(p.getName());
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
