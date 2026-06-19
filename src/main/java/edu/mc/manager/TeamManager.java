package edu.mc.manager;

import edu.mc.ChickenDinnerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

public class TeamManager {

    public static final int MAX_TEAMS = 30;
    public static final int MAX_PLAYERS_PER_TEAM = 4;

    private final ChickenDinnerPlugin plugin;
    private final Map<UUID, Integer> playerTeamMap = new HashMap<>();
    private final Map<Integer, List<UUID>> teamPlayersMap = new HashMap<>();
    private final Map<Integer, TeamInfo> teamInfoMap = new HashMap<>();

    public static class TeamInfo {
        public int id;
        public String name;
        public String chatColor; // 用于 Tab 列表的颜色代码
        public Color armorColor; // 用于皮革衣服的颜色

        public TeamInfo(int id, String name, String chatColor, Color armorColor) {
            this.id = id;
            this.name = name;
            this.chatColor = chatColor;
            this.armorColor = armorColor;
        }
    }

    public TeamManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        initTeams();
        for (int i = 1; i <= MAX_TEAMS; i++) {
            teamPlayersMap.put(i, new ArrayList<>());
        }
    }

    private void initTeams() {
        File file = new File(plugin.getDataFolder(), "teams.yml");
        if (!file.exists()) {
            plugin.saveResource("teams.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        if (config.contains("teams")) {
            for (String key : config.getConfigurationSection("teams").getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    String name = config.getString("teams." + key + ".name");
                    String chatColor = org.bukkit.ChatColor.translateAlternateColorCodes('&', config.getString("teams." + key + ".chat-color"));
                    String rgbStr = config.getString("teams." + key + ".color-rgb");
                    String[] rgb = rgbStr.split(",");
                    Color color = Color.fromRGB(Integer.parseInt(rgb[0].trim()), Integer.parseInt(rgb[1].trim()), Integer.parseInt(rgb[2].trim()));
                    addTeam(id, name, chatColor, color);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error loading team: " + key);
                }
            }
        }
    }

    private void addTeam(int id, String name, String chatColor, Color color) {
        teamInfoMap.put(id, new TeamInfo(id, name, chatColor, color));
    }

    public TeamInfo getTeamInfo(int teamId) {
        return teamInfoMap.get(teamId);
    }

    public Map<Integer, TeamInfo> getAllTeamInfos() {
        return teamInfoMap;
    }

    public Integer getTeam(UUID playerId) {
        return playerTeamMap.get(playerId);
    }

    public List<UUID> getPlayersInTeam(int teamId) {
        return teamPlayersMap.getOrDefault(teamId, new ArrayList<>());
    }

    public boolean joinTeam(Player player, int teamId) {
        List<UUID> currentPlayers = getPlayersInTeam(teamId);
        if (currentPlayers.size() >= MAX_PLAYERS_PER_TEAM) {
            player.sendMessage(plugin.getMessageManager().getMessage("team.full"));
            return false;
        }

        Integer oldTeam = playerTeamMap.get(player.getUniqueId());
        if (oldTeam != null) {
            if (oldTeam == teamId) {
                player.sendMessage(plugin.getMessageManager().getMessage("team.already_in"));
                return false;
            }
            teamPlayersMap.get(oldTeam).remove(player.getUniqueId());
        }

        playerTeamMap.put(player.getUniqueId(), teamId);
        teamPlayersMap.get(teamId).add(player.getUniqueId());

        TeamInfo info = getTeamInfo(teamId);
        
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("team", info.chatColor + info.name);
        player.sendMessage(plugin.getMessageManager().getMessage("team.join_success", placeholders));

        updateTabName(player, info);
        equipTeamArmor(player, info);

        return true;
    }

    public void leaveTeam(Player player) {
        Integer teamId = playerTeamMap.remove(player.getUniqueId());
        if (teamId != null) {
            teamPlayersMap.get(teamId).remove(player.getUniqueId());
            player.setPlayerListName(player.getName()); // 恢复默认
            player.setDisplayName(player.getName());
            player.getInventory().setArmorContents(null);
        }
    }

    public void updateTabName(Player player, TeamInfo info) {
        String format = info.chatColor + info.name + " §f✈ §r" + info.chatColor + player.getName();
        if (format.length() > 16 && Bukkit.getVersion().contains("1.8")) {
            // 1.8 可能会有长度限制，但 setPlayerListName 限制较宽，通常是记分板有 16 字符限制
        }
        player.setPlayerListName(format);
        player.setDisplayName(info.chatColor + player.getName() + "§r");
    }

    public void equipTeamArmor(Player player, TeamInfo info) {
        ItemStack helmet = createColoredLeatherArmor(Material.LEATHER_HELMET, info.armorColor, info.chatColor + info.name);
        ItemStack chestplate = createColoredLeatherArmor(Material.LEATHER_CHESTPLATE, info.armorColor, info.chatColor + info.name);
        ItemStack leggings = createColoredLeatherArmor(Material.LEATHER_LEGGINGS, info.armorColor, info.chatColor + info.name);
        ItemStack boots = createColoredLeatherArmor(Material.LEATHER_BOOTS, info.armorColor, info.chatColor + info.name);

        player.getInventory().setArmorContents(new ItemStack[]{boots, leggings, chestplate, helmet});
        
        // 倒数第二格放置帽子
        player.getInventory().setItem(7, helmet.clone());
        player.updateInventory();
    }

    public static ItemStack createColoredLeatherArmor(Material material, Color color, String name) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            if (name != null) {
                meta.setDisplayName(name);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void autoAssignUnassignedPlayers(java.util.Collection<UUID> alivePlayers) {
        List<UUID> unassigned = new ArrayList<>();
        for (UUID uuid : alivePlayers) {
            if (!playerTeamMap.containsKey(uuid)) {
                unassigned.add(uuid);
            }
        }

        if (unassigned.isEmpty()) return;

        // 打乱未分配玩家
        Collections.shuffle(unassigned);

        for (UUID uuid : unassigned) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            // 优先找完全没人的空队伍
            int assignedTeam = -1;
            for (int i = 1; i <= MAX_TEAMS; i++) {
                if (getPlayersInTeam(i).isEmpty()) {
                    assignedTeam = i;
                    break;
                }
            }

            // 如果没有空队伍，找没满人的队伍
            if (assignedTeam == -1) {
                for (int i = 1; i <= MAX_TEAMS; i++) {
                    if (getPlayersInTeam(i).size() < MAX_PLAYERS_PER_TEAM) {
                        assignedTeam = i;
                        break;
                    }
                }
            }

            if (assignedTeam != -1) {
                playerTeamMap.put(uuid, assignedTeam);
                teamPlayersMap.get(assignedTeam).add(uuid);
                TeamInfo info = getTeamInfo(assignedTeam);
                
                java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                placeholders.put("team", info.chatColor + info.name);
                p.sendMessage(plugin.getMessageManager().getMessage("team.auto_assign", placeholders));
                
                updateTabName(p, info);
                equipTeamArmor(p, info);
            } else {
                p.sendMessage(plugin.getMessageManager().getMessage("team.auto_assign_fail"));
            }
        }
    }

    public boolean isSameTeam(UUID p1, UUID p2) {
        Integer t1 = playerTeamMap.get(p1);
        Integer t2 = playerTeamMap.get(p2);
        return t1 != null && t2 != null && t1.equals(t2);
    }

    public boolean isOnlyOneTeamLeft(java.util.Set<UUID> alivePlayers) {
        Integer survivingTeam = null;
        for (UUID uuid : alivePlayers) {
            Integer teamId = playerTeamMap.get(uuid);
            if (teamId == null) {
                return false; // 有未分配队伍的玩家存活，不能算一队吃鸡
            }
            if (survivingTeam == null) {
                survivingTeam = teamId;
            } else if (!survivingTeam.equals(teamId)) {
                return false; // 存活玩家不全在一个队伍
            }
        }
        return true; // 所有存活玩家都在同一个队伍
    }

    public TeamInfo getWinningTeam(java.util.Set<UUID> alivePlayers) {
        if (alivePlayers.isEmpty()) return null;
        for (UUID uuid : alivePlayers) {
            Integer teamId = playerTeamMap.get(uuid);
            if (teamId != null) {
                return getTeamInfo(teamId);
            }
        }
        return null;
    }

    public void reset() {
        playerTeamMap.clear();
        for (List<UUID> list : teamPlayersMap.values()) {
            list.clear();
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setPlayerListName(p.getName()); // 恢复Tab名称
            p.setDisplayName(p.getName()); // 恢复聊天名称
        }
    }
}
