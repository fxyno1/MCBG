package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

public class AirdropManager {
    private final ChickenDinnerPlugin plugin;
    private final Random random = new Random();
    private final java.util.List<Location> activeAirdrops = new java.util.concurrent.CopyOnWriteArrayList<>();

    public AirdropManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        loadAndClearAirdrops();
        clearAllTrappedChests();
    }

    public java.util.List<Location> getActiveAirdrops() {
        return activeAirdrops;
    }

    public void reset() {
        clearAllTrappedChests();
        activeAirdrops.clear();

        // 删除持久化记录文件
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "airdrops.txt");
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearAllTrappedChests() {
        World world = Bukkit.getWorlds().get(0);
        int minChunkX = edu.mc.GameConfig.ISLAND_X1 >> 4;
        int maxChunkX = edu.mc.GameConfig.ISLAND_X2 >> 4;
        int minChunkZ = edu.mc.GameConfig.ISLAND_Z1 >> 4;
        int maxChunkZ = edu.mc.GameConfig.ISLAND_Z2 >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                boolean wasLoaded = world.isChunkLoaded(cx, cz);
                if (!wasLoaded) {
                    world.loadChunk(cx, cz, false);
                }
                org.bukkit.Chunk chunk = world.getChunkAt(cx, cz);
                for (org.bukkit.block.BlockState state : chunk.getTileEntities()) {
                    if (state.getType() == Material.TRAPPED_CHEST) {
                        state.getBlock().setType(Material.AIR);
                    }
                }
                if (!wasLoaded) {
                    world.unloadChunk(cx, cz, true);
                }
            }
        }
    }

    private void saveAirdrops() {
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "airdrops.txt");
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            java.io.FileWriter writer = new java.io.FileWriter(file);
            for (Location loc : activeAirdrops) {
                writer.write(loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + ","
                        + loc.getBlockZ() + "\n");
            }
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadAndClearAirdrops() {
        try {
            java.io.File file = new java.io.File(plugin.getDataFolder(), "airdrops.txt");
            if (file.exists()) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",");
                    if (parts.length == 4) {
                        World w = Bukkit.getWorld(parts[0]);
                        if (w != null) {
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            int z = Integer.parseInt(parts[3]);
                            Block b = w.getBlockAt(x, y, z);
                            if (b.getType() == Material.TRAPPED_CHEST || b.getType() == Material.CHEST) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                }
                reader.close();
                file.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void spawnAirdrop(double cx, double cz, double size) {
        World world = Bukkit.getWorlds().get(0);

        // 在目标安全区内随机生成空投坐标 (限制在半径的 80% 以内防止压边)
        double offset = (size / 2) * 0.8;
        double dropX = cx + (random.nextDouble() * 2 - 1) * offset;
        double dropZ = cz + (random.nextDouble() * 2 - 1) * offset;

        int dropY = world.getHighestBlockYAt((int) dropX, (int) dropZ);

        Location dropLoc = new Location(world, dropX, dropY + 1, dropZ);
        Block block = dropLoc.getBlock();
        block.setType(Material.TRAPPED_CHEST);

        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            // 必刷：钻石胸甲 + 钻石靴
            chest.getInventory().addItem(new ItemStack(Material.DIAMOND_CHESTPLATE));
            chest.getInventory().addItem(new ItemStack(Material.DIAMOND_BOOTS));

            // 选择性必刷（50%概率 满力量弓，50%概率 满锋利钻剑，保证必刷其一）
            if (random.nextBoolean()) {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.ARROW_DAMAGE, 5);
                bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.ARROW_INFINITE, 1);
                chest.getInventory().addItem(bow);
                chest.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            } else {
                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DAMAGE_ALL, 5);
                chest.getInventory().addItem(sword);
            }

            // 选择性刷：医疗箱（50%概率）
            if (random.nextBoolean()) {
                chest.getInventory().addItem(plugin.getHealingManager().createMedicalBox());
            }

            // 附带刷一些常规补给
            chest.getInventory().addItem(plugin.getHealingManager().createMedkit());
            chest.getInventory().addItem(plugin.getHealingManager().createBandage());
            chest.update(true);
        }

        // 视觉效果与记录
        activeAirdrops.add(dropLoc);
        saveAirdrops();

        // 广播空投消息
        Bukkit.broadcastMessage("§e[空投] §a一架飞机已投下空投补给箱！坐标: X:" + (int) dropX + " Z:" + (int) dropZ);

        // 给全部在线玩家的屏幕上直接投射空投坐标大标题
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            plugin.sendTitle(p, "§e★ 空投补给降临 ★", "§a坐标: X:" + (int) dropX + " Z:" + (int) dropZ, 10, 80, 10);
        }

        // 连续10秒发射烟花（每秒发射一颗，爆炸后再发一颗）
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 10) {
                    this.cancel();
                    return;
                }
                // 烟花从箱子上空一点发出，获得更好观赏效果
                Location fireworkLoc = dropLoc.clone().add(0.5, 1.0, 0.5);
                org.bukkit.entity.Firework fw = (org.bukkit.entity.Firework) world.spawnEntity(fireworkLoc,
                        org.bukkit.entity.EntityType.FIREWORK);
                org.bukkit.inventory.meta.FireworkMeta fwm = fw.getFireworkMeta();
                org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                        .flicker(true)
                        .withColor(org.bukkit.Color.RED)
                        .withFade(org.bukkit.Color.YELLOW)
                        .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                        .trail(true)
                        .build();
                fwm.addEffect(effect);
                fwm.setPower(2);
                fw.setFireworkMeta(fwm);
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

}
