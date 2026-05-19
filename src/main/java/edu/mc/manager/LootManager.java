package edu.mc.manager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class LootManager {

    private final Set<Location> openedChests = new HashSet<>();
    private final Random random = new Random();

    private final Material[] COMMON_LOOT = {
            Material.WOOD_SWORD, Material.STONE_SWORD, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.APPLE, Material.BREAD,
            Material.BOW, Material.ARROW, Material.SNOW_BALL
    };

    private final Material[] RARE_LOOT = {
            Material.IRON_SWORD, Material.IRON_CHESTPLATE, Material.GOLDEN_APPLE,
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
        
        int itemCount = 3 + random.nextInt(5);
        for (int i = 0; i < itemCount; i++) {
            Material type;
            int amount = 1;
            
            if (random.nextInt(100) < 20) {
                type = RARE_LOOT[random.nextInt(RARE_LOOT.length)];
            } else {
                type = COMMON_LOOT[random.nextInt(COMMON_LOOT.length)];
            }
            
            if (type == Material.ARROW || type == Material.SNOW_BALL) {
                amount = 5 + random.nextInt(11);
            } else if (type == Material.APPLE || type == Material.BREAD) {
                amount = 2 + random.nextInt(4);
            }

            int slot = random.nextInt(inventory.getSize());
            inventory.setItem(slot, new ItemStack(type, amount));
        }
    }
    
    public void reset() {
        openedChests.clear();
    }
}
