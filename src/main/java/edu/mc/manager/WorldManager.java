package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class WorldManager {

    private final ChickenDinnerPlugin plugin;
    private final String TEMPLATE_WORLD = "world_backup";
    private final String GAME_WORLD = "game_1";

    public WorldManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void createGameWorld() {
        File serverFolder = plugin.getServer().getWorldContainer();
        File templateFolder = new File(serverFolder, TEMPLATE_WORLD);
        File gameFolder = new File(serverFolder, GAME_WORLD);

        if (!templateFolder.exists()) {
            plugin.getLogger().severe("Template world '" + TEMPLATE_WORLD + "' not found! Cannot create game world.");
            return;
        }

        // 确保上一个 game_1 已被完全删除
        if (gameFolder.exists()) {
            deleteDirectory(gameFolder);
        }

        plugin.getLogger().info("Copying world from " + TEMPLATE_WORLD + " to " + GAME_WORLD + "...");
        try {
            copyDirectory(templateFolder, gameFolder);
            // 确保不复制 uid.dat 和 session.lock 避免Bukkit报错
            new File(gameFolder, "uid.dat").delete();
            new File(gameFolder, "session.lock").delete();
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to copy world folder!");
            e.printStackTrace();
            return;
        }

        plugin.getLogger().info("Loading game world: " + GAME_WORLD);
        WorldCreator creator = new WorldCreator(GAME_WORLD);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            world.setAutoSave(false); // 禁止自动保存，提高性能，反正是用完即删
            world.setGameRuleValue("mobGriefing", "true"); // 必须开启，否则火焰弹无法破坏地形！
            plugin.getLogger().info("Game world loaded successfully.");
        } else {
            plugin.getLogger().severe("Failed to load game world!");
        }
    }

    public void deleteGameWorld() {
        World gameWorld = Bukkit.getWorld(GAME_WORLD);
        if (gameWorld != null) {
            // 强制踢出还留在这个世界里的玩家（理论上不应该有，但作为防脱底措施）
            for (Player p : gameWorld.getPlayers()) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }

        // 延迟 3 秒执行卸载和删除。
        // 因为在 Windows 系统上，如果在玩家刚被传送走的同一 Tick 强行卸载世界，
        // Bukkit 会卸载失败，且底层文件依然被 Java 进程占用，导致后续的 deleteDirectory 删除失败并产生严重的区块损坏。
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                World w = Bukkit.getWorld(GAME_WORLD);
                if (w != null) {
                    boolean success = Bukkit.unloadWorld(w, false);
                    plugin.getLogger().info("Game world unloaded: " + success);
                }

                File serverFolder = plugin.getServer().getWorldContainer();
                File gameFolder = new File(serverFolder, GAME_WORLD);

                if (gameFolder.exists()) {
                    plugin.getLogger().info("Deleting game world folder...");
                    deleteDirectory(gameFolder);
                    plugin.getLogger().info("Game world folder deleted.");
                }
            }
        }.runTaskLater(plugin, 60L);
    }

    private void copyDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            if (!target.exists()) {
                target.mkdir();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    copyDirectory(new File(source, file), new File(target, file));
                }
            }
        } else {
            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }
        }
    }

    private void deleteDirectory(File path) {
        if (path.exists()) {
            File[] files = path.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            path.delete();
        }
    }
}
