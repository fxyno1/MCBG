package edu.mc.command;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HubCommand implements CommandExecutor {

    private final ChickenDinnerPlugin plugin;

    public HubCommand(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("system.only_player"));
            return true;
        }

        Player player = (Player) sender;

        // 游戏大厅坐标
        Location lobbyLoc = new Location(Bukkit.getWorlds().get(0), GameConfig.LOBBY_X, GameConfig.LOBBY_Y,
                GameConfig.LOBBY_Z);

        GameState state = plugin.getCurrentState();

        // 如果在比赛准备起飞、飞行或进行阶段，且玩家是存活状态，则需要进行退出/淘汰处理
        if (state == GameState.STARTING || state == GameState.FLIGHT || state == GameState.INGAME) {
            if (plugin.getPlayerManager().isAlive(player)) {
                // 清理可能存在的飞行状态
                plugin.getFlightManager().removePlayerFromFlight(player);

                // 将玩家设为旁观者（会自动处理游戏模式和背包清理）
                plugin.getPlayerManager().setSpectator(player);

                // 广播玩家中途退赛的消息
                java.util.Map<String, String> map = new java.util.HashMap<>();
                map.put("player", player.getName());
                map.put("alive", String.valueOf(plugin.getPlayerManager().getAliveCount()));
                Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("extra_game.eliminated_broadcast", map));

                // 检查是否仅剩最后一人，是则结束比赛
                if (plugin.getPlayerManager().getAliveCount() <= 1) {
                    plugin.getGameManager().endGame();
                }
            } else if (plugin.getPlayerManager().isSpectator(player)) {
                // 如果已经是旁观者，确保也清理飞行相关的残留状态
                plugin.getFlightManager().removePlayerFromFlight(player);
            }
        }

        // 尝试发送 BungeeCord 跨服传送请求（适用于群组服部署模式）
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(b);
            out.writeUTF("Connect");
            out.writeUTF("lobby"); // 发送到 BungeeCord 中配置的 "lobby" 大厅服务器
            player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        } catch (Exception e) {
            // 静态服环境下（单服模式），通道消息会被忽略，直接走下方本地传送 fallback
        }

        // 无论何种状态，最终传送玩家回大厅 (作为单服模式的主逻辑，或跨服模式的网络延迟兜底 fallback)
        player.teleport(lobbyLoc);
        player.sendMessage(plugin.getMessageManager().getMessage("player.hub_success"));
        return true;
    }
}
