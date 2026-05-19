package edu.mc.command;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class GameCommand implements CommandExecutor {

    private final ChickenDinnerPlugin plugin;

    public GameCommand(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcbg.admin")) {
            sender.sendMessage("§c你没有权限执行此命令！");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6--- [吃鸡核心命令帮助] ---");
            sender.sendMessage("§e/cd start - 强制开始比赛（无视人数和大厅倒计时）");
            sender.sendMessage("§e/cd pause - 暂停/恢复游戏倒计时");
            sender.sendMessage("§e/cd skip - 跳过当前的准备/倒计时阶段");
            sender.sendMessage("§e/cd stop - 强行终止并重置当前比赛");
            sender.sendMessage("§e/cd settime <秒> - 设置当前阶段的剩余秒数");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start":
                if (plugin.getCurrentState() != GameState.LOBBY) {
                    sender.sendMessage("§c游戏已经开始，无法再次强制开始！");
                    return true;
                }
                plugin.getGameManager().forceStart();
                sender.sendMessage("§a已成功强制开始比赛！");
                break;
            case "pause":
                boolean paused = plugin.getGameManager().togglePause();
                if (paused) {
                    sender.sendMessage("§a游戏倒计时已【暂停】！");
                } else {
                    sender.sendMessage("§a游戏倒计时已【恢复】！");
                }
                break;
            case "skip":
                plugin.getGameManager().skipPhase();
                sender.sendMessage("§a已跳过当前阶段！");
                break;
            case "stop":
                plugin.getGameManager().endGame();
                sender.sendMessage("§c已强行终止比赛，正在结算重置...");
                break;
            case "settime":
                if (args.length < 2) {
                    sender.sendMessage("§c使用方法：/cd settime <秒数>");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[1]);
                    plugin.getGameManager().setCountdownTime(seconds);
                    sender.sendMessage("§a已成功将当前倒计时设为" + seconds + " 秒！");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c输入的秒数格式不正确！");
                }
                break;
            default:
                sender.sendMessage("§c未知子命令，请直接输入/cd 查看帮助。");
                break;
        }
        return true;
    }
}
