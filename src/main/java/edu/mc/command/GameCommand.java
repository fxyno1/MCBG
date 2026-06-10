package edu.mc.command;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GameCommand implements CommandExecutor {

    private static final String PERMISSION_ADMIN = "mcbg.admin";
    private static final String PREFIX = "§6--- [吃鸡核心命令帮助] ---";
    private static final String COLOR_ERROR = "§c";
    private static final String COLOR_SUCCESS = "§a";
    private static final String COLOR_INFO = "§e";

    private final ChickenDinnerPlugin plugin;

    public GameCommand(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            sender.sendMessage(COLOR_ERROR + "你没有权限执行此命令！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(PREFIX);
            sender.sendMessage(COLOR_INFO + "/cd start - 强制开始比赛（无视人数和大厅倒计时）");
            sender.sendMessage(COLOR_INFO + "/cd pause - 暂停/恢复游戏倒计时");
            sender.sendMessage(COLOR_INFO + "/cd skip - 跳过当前的准备/倒计时阶段");
            sender.sendMessage(COLOR_INFO + "/cd stop - 强行终止并重置当前比赛");
            sender.sendMessage(COLOR_INFO + "/cd settime <秒> - 设置当前阶段的剩余秒数");
            sender.sendMessage(COLOR_INFO + "/cd resetmap - 强制刷新所有玩家的GPS雷达地图");
            sender.sendMessage(COLOR_INFO + "/cd reload - 重新载入配置文件 (config.yml)");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start":
                handleStart(sender);
                break;
            case "pause":
                handlePause(sender);
                break;
            case "skip":
                handleSkip(sender);
                break;
            case "stop":
                handleStop(sender);
                break;
            case "settime":
                handleSetTime(sender, args);
                break;
            case "resetmap":
                handleResetMap(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            default:
                sender.sendMessage(COLOR_ERROR + "未知子命令，请直接输入/cd 查看帮助。");
                break;
        }
        return true;
    }

    private void handleStart(CommandSender sender) {
        if (plugin.getCurrentState() != GameState.LOBBY) {
            sender.sendMessage(COLOR_ERROR + "游戏已经开始，无法再次强制开始！");
            return;
        }

        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        plugin.getGameManager().forceStart();
        sender.sendMessage(COLOR_SUCCESS + "已成功强制开始比赛！");
    }

    private void handlePause(CommandSender sender) {
        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        boolean paused = plugin.getGameManager().togglePause();
        sender.sendMessage(paused ? COLOR_SUCCESS + "游戏倒计时已【暂停】！" : COLOR_SUCCESS + "游戏倒计时已【恢复】！");
    }

    private void handleSkip(CommandSender sender) {
        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        plugin.getGameManager().skipPhase();
        sender.sendMessage(COLOR_SUCCESS + "已跳过当前阶段！");
    }

    private void handleStop(CommandSender sender) {
        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        plugin.getGameManager().endGame();
        sender.sendMessage(COLOR_ERROR + "已强行终止比赛，正在结算重置...");
    }

    private void handleSetTime(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(COLOR_ERROR + "使用方法：/cd settime <秒数>");
            return;
        }

        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        try {
            int seconds = Integer.parseInt(args[1]);
            plugin.getGameManager().setCountdownTime(seconds);
            sender.sendMessage(COLOR_SUCCESS + "已成功将当前倒计时设为" + seconds + " 秒！");
        } catch (NumberFormatException e) {
            sender.sendMessage(COLOR_ERROR + "输入的秒数格式不正确！");
        }
    }

    private void handleResetMap(CommandSender sender) {
        if (plugin.getPacketMapManager() == null) {
            sender.sendMessage(COLOR_ERROR + "地图管理器未初始化！");
            return;
        }

        plugin.getPacketMapManager().clearAll();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE)
                continue;

            plugin.getPacketMapManager().removeMap(p);
            plugin.getPacketMapManager().giveMap(p);
            p.updateInventory();
        }

        sender.sendMessage(COLOR_SUCCESS + "§l[系统] 已成功对全服玩家的战术GPS雷达下达了强刷清空指令！");
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        GameConfig.load(plugin.getConfig());

        // 重新设置出生点以防在配置文件中更改了出生点坐标
        org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);
        mainWorld.setSpawnLocation((int) GameConfig.LOBBY_X, (int) GameConfig.LOBBY_Y, (int) GameConfig.LOBBY_Z);

        if (plugin.getPacketMapManager() != null) {
            plugin.getPacketMapManager().reload();
            // 重新发图给所有非创造模式在线玩家
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getGameMode() == org.bukkit.GameMode.CREATIVE)
                    continue;
                plugin.getPacketMapManager().removeMap(p);
                plugin.getPacketMapManager().giveMap(p);
                p.updateInventory();
            }
        }
        sender.sendMessage(COLOR_SUCCESS + "配置文件重载成功，并已同步更新战术雷达底图及大厅坐标！");
    }
}
