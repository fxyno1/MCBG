package edu.mc.manager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import edu.mc.ChickenDinnerPlugin;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LootManager {

    private final ChickenDinnerPlugin plugin;
    private final Set<Location> openedChests = new HashSet<>();
    private final Random random = new Random();

    public LootManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    private final Material[] COMMON_LOOT = {
            Material.WOOD_SWORD, Material.STONE_SWORD, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.BOW, Material.ARROW, Material.SNOW_BALL
    };

    private final Material[] RARE_LOOT = {
            Material.IRON_SWORD, Material.IRON_CHESTPLATE,
            Material.DIAMOND_SWORD, Material.ENDER_PEARL
    };

    public boolean isChestOpened(Location location) {
        return openedChests.contains(location);
    }

    public void markChestOpened(Location location) {
        openedChests.add(location);
    }

    public void populateChest(Inventory inventory) {
        inventory.clear();

        int size = inventory.getSize();
        int itemCount = 3 + random.nextInt(5); // 随机 3 到 7 个物品
        itemCount = Math.min(itemCount, size);

        // 创建一个打乱顺序的槽位索引列表，实现无重复位置分配
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots);

        for (int i = 0; i < itemCount; i++) {
            int slot = slots.get(i);
            Material type;
            int amount = 1;

            if (random.nextInt(100) < 20) {
                type = RARE_LOOT[random.nextInt(RARE_LOOT.length)];
                inventory.setItem(slot, new ItemStack(type, amount));
            } else {
                // 有 30% 几率刷出药品（急救包或绷带）
                if (random.nextInt(100) < 30) {
                    if (random.nextInt(100) < 20) {
                        inventory.setItem(slot, plugin.getHealingManager().createMedkit());
                    } else {
                        inventory.setItem(slot, plugin.getHealingManager().createBandage());
                    }
                } else {
                    type = COMMON_LOOT[random.nextInt(COMMON_LOOT.length)];
                    if (type == Material.ARROW || type == Material.SNOW_BALL) {
                        amount = 5 + random.nextInt(11);
                    }
                    inventory.setItem(slot, new ItemStack(type, amount));
                }
            }
        }
    }

    public void reset() {
        openedChests.clear();
    }
}
