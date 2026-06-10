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



    public boolean isChestOpened(Location location) {
        return openedChests.contains(location);
    }

    public void markChestOpened(Location location) {
        openedChests.add(location);
    }

    public void populateChest(Inventory inventory) {
        inventory.clear();

        int size = inventory.getSize();
        int min = plugin.getDataManager().lootChestMinItems;
        int max = plugin.getDataManager().lootChestMaxItems;
        int diff = max - min + 1;
        int itemCount = min + (diff > 0 ? random.nextInt(diff) : 0);
        itemCount = Math.min(itemCount, size);
        
        plugin.getLogger().info("[LootManager] Populating chest. Size: " + size + ", itemCount: " + itemCount + ", totalWeight: " + plugin.getDataManager().totalLootWeight);

        // 创建一个打乱顺序的槽位索引列表，实现无重复位置分配
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        java.util.Collections.shuffle(slots);

        for (int i = 0; i < itemCount; i++) {
            int slot = slots.get(i);
            
            if (plugin.getDataManager().totalLootWeight <= 0) {
                inventory.setItem(slot, new ItemStack(Material.APPLE, 1));
                plugin.getLogger().warning("[LootManager] totalLootWeight is 0! Configuration might be broken. Spawning default APPLE.");
                continue;
            }
            int r = random.nextInt(plugin.getDataManager().totalLootWeight);
            DataManager.LootEntry chosenEntry = null;
            int acc = 0;
            for (DataManager.LootEntry entry : plugin.getDataManager().lootEntries) {
                acc += entry.weight;
                if (r < acc) {
                    chosenEntry = entry;
                    break;
                }
            }
            if (chosenEntry != null) {
                int amount = chosenEntry.minAmount;
                if (chosenEntry.maxAmount > chosenEntry.minAmount) {
                    amount += random.nextInt(chosenEntry.maxAmount - chosenEntry.minAmount + 1);
                }
                
                ItemStack item = null;
                // 判断是否为医疗物品
                if (chosenEntry.material == plugin.getDataManager().bandageMaterial) {
                    item = plugin.getHealingManager().createBandage();
                    item.setAmount(amount);
                } else if (chosenEntry.material == plugin.getDataManager().medkitMaterial) {
                    item = plugin.getHealingManager().createMedkit();
                    item.setAmount(amount);
                } else if (chosenEntry.material == plugin.getDataManager().medicalBoxMaterial) {
                    item = plugin.getHealingManager().createMedicalBox();
                    item.setAmount(amount);
                } else {
                    item = new ItemStack(chosenEntry.material, amount);
                }
                
                // random protection logic
                if (chosenEntry.randomProtection && !chosenEntry.protectionLevels.isEmpty()) {
                    org.bukkit.enchantments.Enchantment[] prots = {
                        org.bukkit.enchantments.Enchantment.PROTECTION_ENVIRONMENTAL,
                        org.bukkit.enchantments.Enchantment.PROTECTION_FIRE,
                        org.bukkit.enchantments.Enchantment.PROTECTION_EXPLOSIONS,
                        org.bukkit.enchantments.Enchantment.PROTECTION_PROJECTILE
                    };
                    org.bukkit.enchantments.Enchantment chosenProt = prots[random.nextInt(prots.length)];
                    
                    int protTotalWeight = 0;
                    for (int w : chosenEntry.protectionLevels.values()) {
                        protTotalWeight += w;
                    }
                    if (protTotalWeight > 0) {
                        int pr = random.nextInt(protTotalWeight);
                        int pAcc = 0;
                        int chosenLevel = 0;
                        for (java.util.Map.Entry<Integer, Integer> ple : chosenEntry.protectionLevels.entrySet()) {
                            pAcc += ple.getValue();
                            if (pr < pAcc) {
                                chosenLevel = ple.getKey();
                                break;
                            }
                        }
                        if (chosenLevel > 0) {
                            item.addUnsafeEnchantment(chosenProt, chosenLevel);
                        }
                    }
                }
                
                // standard enchants logic
                if (chosenEntry.enchants != null && !chosenEntry.enchants.isEmpty()) {
                    for (java.util.Map.Entry<org.bukkit.enchantments.Enchantment, java.util.Map<Integer, Integer>> enchEntry : chosenEntry.enchants.entrySet()) {
                        int enchTotalWeight = 0;
                        for (int w : enchEntry.getValue().values()) {
                            enchTotalWeight += w;
                        }
                        if (enchTotalWeight > 0) {
                            int er = random.nextInt(enchTotalWeight);
                            int eAcc = 0;
                            int chosenLevel = 0;
                            for (java.util.Map.Entry<Integer, Integer> levelEntry : enchEntry.getValue().entrySet()) {
                                eAcc += levelEntry.getValue();
                                if (er < eAcc) {
                                    chosenLevel = levelEntry.getKey();
                                    break;
                                }
                            }
                            if (chosenLevel > 0) {
                                item.addUnsafeEnchantment(enchEntry.getKey(), chosenLevel);
                            }
                        }
                    }
                }
                if (item.getType() == Material.TNT) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§c手雷");
                        item.setItemMeta(meta);
                    }
                } else if (item.getType() == Material.FIREBALL) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName("§6燃烧弹");
                        item.setItemMeta(meta);
                    }
                }

                inventory.setItem(slot, item);
                plugin.getLogger().info("[LootManager] Added item: " + item.getType() + " at slot: " + slot);
            } else {
                plugin.getLogger().info("[LootManager] chosenEntry is null for some reason! r=" + r);
            }
        }
        plugin.getLogger().info("[LootManager] Chest population complete.");
    }

    public void reset() {
        openedChests.clear();
    }
}
