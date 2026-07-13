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

public class GameCommand implements CommandExecutor, org.bukkit.command.TabCompleter {

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
            sender.sendMessage(plugin.getMessageManager().getMessage("system.no_permission"));
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
            sender.sendMessage(COLOR_INFO + "/cd cleansigns <存档名> - 清理指定存档中箱子周围的告示牌");
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
            case "cleansigns":
                handleCleanSigns(sender, args);
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
        sender.sendMessage(plugin.getMessageManager().getMessage("system.force_start"));
    }

    private void handlePause(CommandSender sender) {
        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        boolean paused = plugin.getGameManager().togglePause();
        sender.sendMessage(paused ? plugin.getMessageManager().getMessage("system.pause")
                : plugin.getMessageManager().getMessage("system.resume"));
    }

    private void handleSkip(CommandSender sender) {
        if (plugin.getGameManager() == null) {
            sender.sendMessage(COLOR_ERROR + "游戏管理器未初始化！");
            return;
        }

        plugin.getGameManager().skipPhase();
        sender.sendMessage(plugin.getMessageManager().getMessage("system.skip_wait"));
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
            java.util.Map<String, String> map = new java.util.HashMap<>();
            map.put("time", String.valueOf(seconds));
            sender.sendMessage(plugin.getMessageManager().getMessage("system.set_time", map));
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

        sender.sendMessage(plugin.getMessageManager().getMessage("system.radar_reset"));
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

    private void handleCleanSigns(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(COLOR_ERROR + "请输入要清理的存档名称！例如: /cd cleansigns world_backup");
            // 直接列出所有存档
            java.io.File serverFolder = plugin.getServer().getWorldContainer();
            if (serverFolder.exists() && serverFolder.isDirectory()) {
                java.util.List<String> validWorlds = new java.util.ArrayList<>();
                for (java.io.File f : serverFolder.listFiles()) {
                    if (f.isDirectory() && new java.io.File(f, "level.dat").exists()) {
                        validWorlds.add(f.getName());
                    }
                }
                sender.sendMessage(COLOR_INFO + "当前可用存档有: " + String.join(", ", validWorlds));
            }
            return;
        }

        String targetWorldName = args[1];
        java.io.File targetFolder = new java.io.File(plugin.getServer().getWorldContainer(), targetWorldName);
        if (!targetFolder.exists() || !new java.io.File(targetFolder, "level.dat").exists()) {
            sender.sendMessage(COLOR_ERROR + "未找到存档 " + targetWorldName + " 或该文件夹不是一个有效的世界！");
            return;
        }

        sender.sendMessage(COLOR_INFO + "正在准备清理存档 [" + targetWorldName + "] 中的箱子告示牌...");
        sender.sendMessage(COLOR_ERROR + "注意：系统将强行加载该存档中的所有已有区块！这会造成服务器卡顿，请稍候...");

        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.World backupWorld = Bukkit.getWorld(targetWorldName);

            // 如果世界尚未被加载，我们再进行删除锁文件并加载的操作
            if (backupWorld == null) {
                java.io.File uidFile = new java.io.File(plugin.getServer().getWorldContainer(),
                        targetWorldName + "/uid.dat");
                if (uidFile.exists()) {
                    uidFile.delete();
                }
                java.io.File sessionFile = new java.io.File(plugin.getServer().getWorldContainer(),
                        targetWorldName + "/session.lock");
                if (sessionFile.exists()) {
                    sessionFile.delete();
                }

                org.bukkit.WorldCreator creator = new org.bukkit.WorldCreator(targetWorldName);
                backupWorld = Bukkit.createWorld(creator);
            }

            if (backupWorld == null) {
                sender.sendMessage(COLOR_ERROR + "无法加载 " + targetWorldName + " 世界！");
                return;
            }

            backupWorld.setAutoSave(false);
            int chestCount = 0;
            int signCount = 0;

            java.io.File regionFolder = new java.io.File(targetFolder, "region");
            if (regionFolder.exists() && regionFolder.isDirectory()) {
                java.io.File[] mcaFiles = regionFolder.listFiles((dir, name) -> name.endsWith(".mca"));
                if (mcaFiles != null) {
                    for (java.io.File mca : mcaFiles) {
                        String[] parts = mca.getName().split("\\.");
                        if (parts.length >= 3) {
                            int rx = 0, rz = 0;
                            try {
                                rx = Integer.parseInt(parts[1]);
                                rz = Integer.parseInt(parts[2]);
                            } catch (NumberFormatException e) {
                                continue;
                            }
                            int startCx = rx << 5;
                            int startCz = rz << 5;
                            int endCx = startCx + 31;
                            int endCz = startCz + 31;

                            int processedChunks = 0;
                            for (int cx = startCx; cx <= endCx; cx++) {
                                for (int cz = startCz; cz <= endCz; cz++) {
                                    try {
                                        org.bukkit.Chunk chunk = backupWorld.getChunkAt(cx, cz);
                                        processedChunks++;
                                        org.bukkit.ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                                        for (int x = 0; x < 16; x++) {
                                            for (int z = 0; z < 16; z++) {
                                                for (int y = 0; y < 256; y++) {
                                                    @SuppressWarnings("deprecation")
                                                    int typeId = snapshot.getBlockTypeId(x, y, z);
                                                    if (typeId == 54 || typeId == 146) { // 54=Chest, 146=Trapped_Chest
                                                        chestCount++;
                                                        org.bukkit.block.Block chestBlock = chunk.getBlock(x, y, z);
                                                        org.bukkit.block.Block[] neighbors = {
                                                                chestBlock.getRelative(1, 0, 0),
                                                                chestBlock.getRelative(-1, 0, 0),
                                                                chestBlock.getRelative(0, 0, 1),
                                                                chestBlock.getRelative(0, 0, -1)
                                                        };
                                                        for (org.bukkit.block.Block neighbor : neighbors) {
                                                            if (neighbor.getType().name().contains("SIGN")) {
                                                                neighbor.setType(org.bukkit.Material.AIR);
                                                                signCount++;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        plugin.getLogger().warning(
                                                "Error processing chunk " + cx + "," + cz + ": " + e.getMessage());
                                    }
                                }
                            }
                            plugin.getLogger().info("Region " + mca.getName() + " processed " + processedChunks
                                    + " chunks. Current chests: " + chestCount);

                            // 在卸载前主动调用一次 save
                            backupWorld.save();
                            // 保存完当前 region 后，安全地卸载这些区块释放内存
                            for (int cx = startCx; cx <= endCx; cx++) {
                                for (int cz = startCz; cz <= endCz; cz++) {
                                    backupWorld.unloadChunk(cx, cz, true);
                                }
                            }
                        }
                    }
                }
            }

            backupWorld.save();
            sender.sendMessage(COLOR_SUCCESS + "清理完毕！共在 [" + targetWorldName + "] 扫描到 " + chestCount + " 个箱子，移除了 "
                    + signCount + " 个告示牌！");
        });
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            return java.util.Collections.emptyList();
        }
        if (args.length == 1) {
            java.util.List<String> subs = java.util.Arrays.asList("start", "pause", "skip", "stop", "settime",
                    "resetmap", "reload", "cleansigns");
            java.util.List<String> result = new java.util.ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    result.add(s);
                }
            }
            return result;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("cleansigns")) {
            java.util.List<String> worlds = new java.util.ArrayList<>();
            java.io.File serverFolder = plugin.getServer().getWorldContainer();
            if (serverFolder.exists() && serverFolder.isDirectory()) {
                for (java.io.File f : serverFolder.listFiles()) {
                    if (f.isDirectory() && new java.io.File(f, "level.dat").exists()) {
                        worlds.add(f.getName());
                    }
                }
            }
            java.util.List<String> result = new java.util.ArrayList<>();
            for (String w : worlds) {
                if (w.toLowerCase().startsWith(args[1].toLowerCase())) {
                    result.add(w);
                }
            }
            return result;
        }
        return java.util.Collections.emptyList();
    }
}
