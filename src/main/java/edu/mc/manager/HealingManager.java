package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HealingManager {
    private final ChickenDinnerPlugin plugin;
    private final Map<UUID, BukkitRunnable> healingTasks = new HashMap<>();
    private final Map<UUID, org.bukkit.Location> healingLocations = new HashMap<>();

    private final Map<UUID, ItemStack> originalItems = new HashMap<>();
    private final Map<UUID, Integer> initialSlots = new HashMap<>();
    private final Map<UUID, Long> startTimes = new HashMap<>();
    private final Map<UUID, Long> lastInteractTimes = new HashMap<>();

    public HealingManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    public long getLastInteractTime(Player player) {
        return lastInteractTimes.getOrDefault(player.getUniqueId(), 0L);
    }

    public void updateLastInteractTime(Player player, long time) {
        lastInteractTimes.put(player.getUniqueId(), time);
    }

    public ItemStack createBandage() {
        ItemStack item = new ItemStack(plugin.getDataManager().bandageMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getDataManager().bandageName);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMedkit() {
        ItemStack item = new ItemStack(plugin.getDataManager().medkitMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getDataManager().medkitName);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMedicalBox() {
        ItemStack item = new ItemStack(plugin.getDataManager().medicalBoxMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(plugin.getDataManager().medicalBoxName);
        meta.setLore(plugin.getDataManager().medicalBoxLore);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isHealing(Player player) {
        return healingTasks.containsKey(player.getUniqueId());
    }

    public void cancelHealing(Player player) {
        UUID uuid = player.getUniqueId();
        if (healingTasks.containsKey(uuid)) {
            plugin.sendActionBar(player, "§c打药已取消！");
            restoreOriginalItem(player);
            cancelTask(uuid);
        }
    }

    public long getStartTime(Player player) {
        return startTimes.getOrDefault(player.getUniqueId(), 0L);
    }

    public void restoreOriginalItem(Player player) {
        UUID uuid = player.getUniqueId();
        if (originalItems.containsKey(uuid) && initialSlots.containsKey(uuid)) {
            int slot = initialSlots.get(uuid);
            ItemStack orig = originalItems.remove(uuid);
            initialSlots.remove(uuid);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.getInventory().setItem(slot, orig);
                    player.updateInventory();
                }
            }, 1L);
        }
    }

    public void startHealing(Player player, boolean isMedkit) {
        UUID uuid = player.getUniqueId();
        if (healingTasks.containsKey(uuid)) {
            return; // 已经在打药中
        }

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack hand = player.getInventory().getItem(slot);
        if (hand == null || hand.getType() == Material.AIR) return;

        // 保存原物品和槽位
        originalItems.put(uuid, hand.clone());
        initialSlots.put(uuid, slot);
        startTimes.put(uuid, System.currentTimeMillis());

        // 延时 1 tick 执行改名和附魔，避免 Bukkit cancelEvent 导致的自动回滚
        final boolean isMed = isMedkit;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && healingTasks.containsKey(uuid)) {
                ItemStack currentHand = player.getInventory().getItem(slot);
                if (currentHand != null && currentHand.getType() != Material.AIR) {
                    ItemStack activeItem = currentHand.clone();
                    ItemMeta meta = activeItem.getItemMeta();
                    String name = ChatColor.stripColor(isMed ? plugin.getDataManager().medkitName : plugin.getDataManager().bandageName);
                    meta.setDisplayName("§6[正在使用中...] " + name);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    activeItem.setItemMeta(meta);
                    player.getInventory().setItem(slot, activeItem);
                    player.updateInventory();
                }
            }
        }, 1L);

        // 绷带3秒(60 ticks)，回复2/9血量
        // 急救包5秒(100 ticks)，回复3/4血量
        int totalTicks = isMedkit ? plugin.getDataManager().medkitUseTicks : plugin.getDataManager().bandageUseTicks;
        double healAmount = player.getMaxHealth() * (isMedkit ? plugin.getDataManager().medkitHealRatio : plugin.getDataManager().bandageHealRatio);

        healingLocations.put(uuid, player.getLocation().clone());

        // 赋予缓慢 III 效果
        player.addPotionEffect(
                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, totalTicks + 20, 2));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancelTask(uuid);
                    return;
                }

                // 如果切出了当前物品栏，或者格子里的药不见了，自动取消
                if (player.getInventory().getHeldItemSlot() != slot) {
                    plugin.sendActionBar(player, "§c打药已取消！");
                    restoreOriginalItem(player);
                    cancelTask(uuid);
                    return;
                }

                ItemStack current = player.getInventory().getItem(slot);
                if (current == null || current.getType() == Material.AIR) {
                    plugin.sendActionBar(player, "§c打药已取消！");
                    cancelTask(uuid);
                    return;
                }

                ticks += 2;

                if (ticks >= totalTicks) {
                    // 打药完成，扣除1个数量
                    ItemStack orig = originalItems.get(uuid);
                    if (orig != null) {
                        if (orig.getAmount() > 1) {
                            orig.setAmount(orig.getAmount() - 1);
                            player.getInventory().setItem(slot, orig);
                        } else {
                            player.getInventory().setItem(slot, null);
                        }
                        player.updateInventory();
                    }

                    double newHealth = Math.min(player.getMaxHealth(), player.getHealth() + healAmount);
                    player.setHealth(newHealth);
                    plugin.sendActionBar(player, "§a打药完成！");

                    cleanupTaskOnly(uuid);
                    return;
                }

                // 渲染 Action Bar 进度条
                int progressBars = (int) ((double) ticks / totalTicks * 10);
                StringBuilder bar = new StringBuilder("§a");
                for (int i = 0; i < progressBars; i++)
                    bar.append("▍");
                bar.append("§7");
                for (int i = progressBars; i < 10; i++)
                    bar.append("▍");

                double secondsLeft = (totalTicks - ticks) / 20.0;
                plugin.sendActionBar(player,
                        bar.toString() + " §e" + String.format("%.1f", Math.max(0, secondsLeft)) + "秒 (缓慢移动中) §c[右键再次取消]");
            }
        };

        task.runTaskTimer(plugin, 0L, 2L);
        healingTasks.put(uuid, task);
    }

    public void startHealingMedicalBox(Player player) {
        UUID uuid = player.getUniqueId();
        if (healingTasks.containsKey(uuid)) {
            return; // 已经在打药中
        }

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack hand = player.getInventory().getItem(slot);
        if (hand == null || hand.getType() == Material.AIR) return;

        // 保存原物品和槽位
        originalItems.put(uuid, hand.clone());
        initialSlots.put(uuid, slot);
        startTimes.put(uuid, System.currentTimeMillis());

        // 延时 1 tick 执行改名和附魔，避免 Bukkit cancelEvent 导致的自动回滚
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && healingTasks.containsKey(uuid)) {
                ItemStack currentHand = player.getInventory().getItem(slot);
                if (currentHand != null && currentHand.getType() != Material.AIR) {
                    ItemStack activeItem = currentHand.clone();
                    ItemMeta meta = activeItem.getItemMeta();
                    String name = ChatColor.stripColor(plugin.getDataManager().medicalBoxName);
                    meta.setDisplayName("§6[正在使用中...] " + name);
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    activeItem.setItemMeta(meta);
                    player.getInventory().setItem(slot, activeItem);
                    player.updateInventory();
                }
            }
        }, 1L);

        int totalTicks = plugin.getDataManager().medicalBoxUseTicks;

        healingLocations.put(uuid, player.getLocation().clone());

        // 赋予缓慢 III 效果
        player.addPotionEffect(
                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, totalTicks + 20, 2));

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cancelTask(uuid);
                    return;
                }

                // 如果切出了当前物品栏，或者格子里的药不见了，自动取消
                if (player.getInventory().getHeldItemSlot() != slot) {
                    plugin.sendActionBar(player, "§c打药已取消！");
                    restoreOriginalItem(player);
                    cancelTask(uuid);
                    return;
                }

                ItemStack current = player.getInventory().getItem(slot);
                if (current == null || current.getType() == Material.AIR) {
                    plugin.sendActionBar(player, "§c打药已取消！");
                    cancelTask(uuid);
                    return;
                }

                ticks += 2;

                if (ticks >= totalTicks) {
                    // 打药完成，扣除1个数量
                    ItemStack orig = originalItems.get(uuid);
                    if (orig != null) {
                        if (orig.getAmount() > 1) {
                            orig.setAmount(orig.getAmount() - 1);
                            player.getInventory().setItem(slot, orig);
                        } else {
                            player.getInventory().setItem(slot, null);
                        }
                        player.updateInventory();
                    }

                    // 医疗箱效果：回满血量，饱食度设为19
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(19);
                    player.setSaturation(20F);
                    plugin.sendActionBar(player, "§a医疗箱使用完成，状态已全部拉满！");

                    cleanupTaskOnly(uuid);
                    return;
                }

                // 进度条渲染
                int progressBars = (int) ((double) ticks / totalTicks * 10);
                StringBuilder bar = new StringBuilder("§a");
                for (int i = 0; i < progressBars; i++)
                    bar.append("▍");
                bar.append("§7");
                for (int i = progressBars; i < 10; i++)
                    bar.append("▍");

                double secondsLeft = (totalTicks - ticks) / 20.0;
                plugin.sendActionBar(player,
                        bar.toString() + " §e" + String.format("%.1f", Math.max(0, secondsLeft)) + "秒 (缓慢移动中) §c[右键再次取消]");
            }
        };

        task.runTaskTimer(plugin, 0L, 2L);
        healingTasks.put(uuid, task);
    }

    public void cancelTask(UUID pid) {
        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            restoreOriginalItem(p);
        }
        cleanupTaskOnly(pid);
    }

    private void cleanupTaskOnly(UUID pid) {
        if (healingTasks.containsKey(pid)) {
            healingTasks.get(pid).cancel();
            healingTasks.remove(pid);
        }
        originalItems.remove(pid);
        initialSlots.remove(pid);
        healingLocations.remove(pid);
        startTimes.remove(pid);
        lastInteractTimes.remove(pid);

        // 移除缓慢效果
        Player p = Bukkit.getPlayer(pid);
        if (p != null && p.isOnline()) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOW);
        }
    }

    public void handleDeathDrops(Player player, java.util.List<ItemStack> drops) {
        UUID uuid = player.getUniqueId();
        if (originalItems.containsKey(uuid)) {
            ItemStack orig = originalItems.get(uuid);
            // 将掉落物中正在使用的药还原回原本的状态
            for (int i = 0; i < drops.size(); i++) {
                ItemStack drop = drops.get(i);
                if (drop != null && drop.hasItemMeta() && drop.getItemMeta().hasDisplayName() &&
                    drop.getItemMeta().getDisplayName().startsWith("§6[正在使用中...]")) {
                    drops.set(i, orig.clone());
                    break;
                }
            }
            // 从 originalItems 中移除，防止后续 cancelTask 运行时又凭空发一次药
            originalItems.remove(uuid);
            initialSlots.remove(uuid);
        }
    }
}

