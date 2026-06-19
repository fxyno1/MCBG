package edu.mc.listener;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.manager.TeamManager;
import edu.mc.state.GameState;
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

public class TeamListener implements Listener {

    private final ChickenDinnerPlugin plugin;

    public TeamListener(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
    }

    public void openTeamGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, plugin.getMessageManager().getMessage("gui.team_select"));
        TeamManager tm = plugin.getTeamManager();
        
        for (int i = 1; i <= TeamManager.MAX_TEAMS; i++) {
            TeamManager.TeamInfo info = tm.getTeamInfo(i);
            if (info != null) {
                ItemStack item = TeamManager.createColoredLeatherArmor(Material.LEATHER_HELMET, info.armorColor, info.chatColor + info.name);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    int size = tm.getPlayersInTeam(i).size();
                    java.util.Map<String, String> countMap = new java.util.HashMap<>();
                    countMap.put("count", String.valueOf(size));
                    countMap.put("max", String.valueOf(TeamManager.MAX_PLAYERS_PER_TEAM));
                    lore.add(plugin.getMessageManager().getMessage("gui.team_count", countMap));
                    if (size >= TeamManager.MAX_PLAYERS_PER_TEAM) {
                        lore.add(plugin.getMessageManager().getMessage("gui.team_full"));
                    } else {
                        lore.add(plugin.getMessageManager().getMessage("gui.team_join"));
                    }
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                // Slots: 0-29. Maybe distribute nicely, but 0-29 is fine.
                // 30 teams fit in 54 slots
                inv.setItem(i - 1, item);
            }
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        GameState state = plugin.getCurrentState();
        
        if (state == GameState.LOBBY || state == GameState.STARTING) {
            ItemStack item = event.getItem();
            Action action = event.getAction();
            
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (item != null && item.getType() == Material.PAPER) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("选队")) {
                        openTeamGUI(player);
                        event.setCancelled(true);
                    }
                } else if (item != null && item.getType() == Material.FEATHER) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("退出大厅")) {
                        player.performCommand("hub");
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        GameState state = plugin.getCurrentState();
        if ((state == GameState.LOBBY || state == GameState.STARTING) && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // 防止玩家在大厅乱动背包里的选队物品/衣服
        GameState state = plugin.getCurrentState();
        if (state == GameState.LOBBY || state == GameState.STARTING) {
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }

        if (event.getView().getTitle().equals(plugin.getMessageManager().getMessage("gui.team_select"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.LEATHER_HELMET) {
                int slot = event.getRawSlot();
                if (slot >= 0 && slot < TeamManager.MAX_TEAMS) {
                    int teamId = slot + 1;
                    plugin.getTeamManager().joinTeam(player, teamId);
                    player.closeInventory();
                }
            }
        }
    }
}
