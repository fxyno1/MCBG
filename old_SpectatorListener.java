package edu.mc.listener;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import edu.mc.manager.TeamManager;

import java.util.Arrays;
import java.util.UUID;

public class SpectatorListener implements Listener {

    private final ChickenDinnerPlugin plugin;

    public SpectatorListener(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    public static void openDeathMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§c你已被淘汰");

        ItemStack eye = new ItemStack(Material.EYE_OF_ENDER);
        ItemMeta eyeMeta = eye.getItemMeta();
        eyeMeta.setDisplayName("§a继续观战");
        eye.setItemMeta(eyeMeta);
        inv.setItem(10, eye); // 2行2列

        ItemStack cart = new ItemStack(Material.STORAGE_MINECART);
        ItemMeta cartMeta = cart.getItemMeta();
        cartMeta.setDisplayName("§e再来一局");
        cartMeta.setLore(Arrays.asList("§7(暂时无法使用，等待后续更新)"));
        cart.setItemMeta(cartMeta);
        inv.setItem(13, cart); // 2行5列

        ItemStack bed = new ItemStack(Material.BED);
        ItemMeta bedMeta = bed.getItemMeta();
        bedMeta.setDisplayName("§c退出到大厅");
        bed.setItemMeta(bedMeta);
        inv.setItem(16, bed); // 2行8列

        player.openInventory(inv);
    }

    public void openSpectateList(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§a观战列表");

        int index = 0;
        for (UUID aliveId : plugin.getPlayerManager().getAlivePlayers()) {
            Player alive = Bukkit.getPlayer(aliveId);
            if (alive != null && alive.isOnline()) {
                ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
                SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
                skullMeta.setOwner(alive.getName());

                String teamColor = "§f";
                Integer teamId = plugin.getTeamManager().getTeam(aliveId);
                if (teamId != null) {
                    TeamManager.TeamInfo teamInfo = plugin.getTeamManager().getTeamInfo(teamId);
                    if (teamInfo != null) {
                        teamColor = teamInfo.chatColor;
                    }
                }

                skullMeta.setDisplayName(teamColor + alive.getName());
                skull.setItemMeta(skullMeta);

                inv.setItem(index++, skull);
                if (index >= 53)
                    break; // 防止越界
            }
        }

        ItemStack close = new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 14);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c关闭");
        close.setItemMeta(closeMeta);
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        Player player = (Player) event.getWhoClicked();

        if (event.getView().getTitle().equals("§c你已被淘汰")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                if (event.getCurrentItem().getType() == Material.EYE_OF_ENDER) {
                    player.closeInventory();
                } else if (event.getCurrentItem().getType() == Material.BED) {
                    player.closeInventory();
                    player.performCommand("hub");
                }
            }
        } else if (event.getView().getTitle().equals("§a观战列表")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null) {
                if (event.getCurrentItem().getType() == Material.STAINED_GLASS_PANE) {
                    player.closeInventory();
                } else if (event.getCurrentItem().getType() == Material.SKULL_ITEM) {
                    SkullMeta meta = (SkullMeta) event.getCurrentItem().getItemMeta();
                    if (meta.hasOwner()) {
                        Player target = Bukkit.getPlayerExact(meta.getOwner());
                        if (target != null && target.isOnline()) {
                            player.teleport(target.getLocation().clone().add(0, 3.5, 0));
                            player.sendMessage("§a已传送至 " + target.getName() + " 身边观战！");
                            player.closeInventory();
                        } else {
                            player.sendMessage("§c该玩家不在线或已淘汰！");
                        }
                    }
                }
            }
        } else {
            // 禁止旁观者移动快捷栏里的特殊物品
            if (plugin.getPlayerManager().isSpectator(player)) {
                if (event.getCurrentItem() != null) {
                    if (event.getCurrentItem().getType() == Material.COMPASS
                            || event.getCurrentItem().getType() == Material.BED) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (plugin.getPlayerManager().isSpectator(player)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK ||
                    event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                ItemStack item = player.getItemInHand();
                if (item != null) {
                    if (item.getType() == Material.COMPASS) {
                        openSpectateList(player);
                        event.setCancelled(true);
                    } else if (item.getType() == Material.BED) {
                        player.performCommand("hub");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}
