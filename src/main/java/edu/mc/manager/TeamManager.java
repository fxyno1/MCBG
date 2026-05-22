package edu.mc.manager;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;

public class TeamManager {

    public static final int MAX_TEAMS = 30;
    public static final int MAX_PLAYERS_PER_TEAM = 4;

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

    public TeamManager() {
        initTeams();
        for (int i = 1; i <= MAX_TEAMS; i++) {
            teamPlayersMap.put(i, new ArrayList<>());
        }
    }

    private void initTeams() {
        // 初始化 30 个队伍的信息
        addTeam(1, "红色队", "§c", Color.fromRGB(255, 0, 0));
        addTeam(2, "蓝色队", "§9", Color.fromRGB(0, 0, 255));
        addTeam(3, "绿色队", "§a", Color.fromRGB(0, 255, 0));
        addTeam(4, "黄色队", "§e", Color.fromRGB(255, 255, 0));
        addTeam(5, "橙色队", "§6", Color.fromRGB(255, 165, 0));
        addTeam(6, "紫色队", "§5", Color.fromRGB(128, 0, 128));
        addTeam(7, "粉色队", "§d", Color.fromRGB(255, 192, 203));
        addTeam(8, "青色队", "§b", Color.fromRGB(0, 255, 255));
        addTeam(9, "黑色队", "§0", Color.fromRGB(0, 0, 0));
        addTeam(10, "白色队", "§f", Color.fromRGB(255, 255, 255));
        addTeam(11, "灰色队", "§8", Color.fromRGB(128, 128, 128));
        addTeam(12, "浅灰队", "§7", Color.fromRGB(192, 192, 192));
        addTeam(13, "棕色队", "§6", Color.fromRGB(139, 69, 19));
        addTeam(14, "浅蓝队", "§b", Color.fromRGB(173, 216, 230));
        addTeam(15, "浅绿队", "§a", Color.fromRGB(144, 238, 144));
        addTeam(16, "品红队", "§d", Color.fromRGB(255, 0, 255));
        addTeam(17, "深红队", "§4", Color.fromRGB(139, 0, 0));
        addTeam(18, "海军蓝", "§1", Color.fromRGB(0, 0, 128));
        addTeam(19, "橄榄绿", "§2", Color.fromRGB(128, 128, 0));
        addTeam(20, "金色队", "§6", Color.fromRGB(255, 215, 0));
        addTeam(21, "银色队", "§7", Color.fromRGB(192, 192, 192));
        addTeam(22, "栗色队", "§4", Color.fromRGB(128, 0, 0));
        addTeam(23, "水鸭青", "§3", Color.fromRGB(0, 128, 128));
        addTeam(24, "珊瑚色", "§c", Color.fromRGB(255, 127, 80));
        addTeam(25, "鲑鱼粉", "§c", Color.fromRGB(250, 128, 114));
        addTeam(26, "靛蓝色", "§9", Color.fromRGB(75, 0, 130));
        addTeam(27, "紫罗兰", "§5", Color.fromRGB(238, 130, 238));
        addTeam(28, "卡其色", "§e", Color.fromRGB(240, 230, 140));
        addTeam(29, "兰花紫", "§d", Color.fromRGB(218, 112, 214));
        addTeam(30, "番茄红", "§c", Color.fromRGB(255, 99, 71));
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
            player.sendMessage("§c该队伍已满！");
            return false;
        }

        Integer oldTeam = playerTeamMap.get(player.getUniqueId());
        if (oldTeam != null) {
            if (oldTeam == teamId) {
                player.sendMessage("§c你已经在这个队伍中了！");
                return false;
            }
            teamPlayersMap.get(oldTeam).remove(player.getUniqueId());
        }

        playerTeamMap.put(player.getUniqueId(), teamId);
        teamPlayersMap.get(teamId).add(player.getUniqueId());

        TeamInfo info = getTeamInfo(teamId);
        player.sendMessage("§a你已成功加入 " + info.chatColor + info.name + "§a！");

        updateTabName(player, info);
        equipTeamArmor(player, info);

        return true;
    }

    public void leaveTeam(Player player) {
        Integer teamId = playerTeamMap.remove(player.getUniqueId());
        if (teamId != null) {
            teamPlayersMap.get(teamId).remove(player.getUniqueId());
            player.setPlayerListName(player.getName()); // 恢复默认
            player.getInventory().setArmorContents(null);
        }
    }

    public void updateTabName(Player player, TeamInfo info) {
        String format = info.chatColor + info.name + " §f✈ §r" + player.getName();
        if (format.length() > 16 && Bukkit.getVersion().contains("1.8")) {
            // 1.8 可能会有长度限制，但 setPlayerListName 限制较宽，通常是记分板有 16 字符限制
        }
        player.setPlayerListName(format);
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
                p.sendMessage("§a[系统] 游戏即将开始，已为您自动分配到 " + info.chatColor + info.name);
                updateTabName(p, info);
                equipTeamArmor(p, info);
            } else {
                p.sendMessage("§c队伍已满，无法分配队伍！");
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
        }
    }
}
