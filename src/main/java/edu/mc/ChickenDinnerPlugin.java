package edu.mc;

import edu.mc.manager.GameManager;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Constructor;

public final class ChickenDinnerPlugin extends JavaPlugin {

    private GameState currentState = GameState.LOBBY;
    private GameManager gameManager;
    private edu.mc.manager.PlayerManager playerManager;
    private edu.mc.manager.LootManager lootManager;
    private edu.mc.manager.ZoneManager zoneManager;
    private edu.mc.manager.ScatterManager scatterManager;
    private edu.mc.manager.FlightManager flightManager;

    private static String NMS_PACKAGE = null;
    private static String getNmsPackage() {
        if (NMS_PACKAGE == null) {
            NMS_PACKAGE = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        }
        return NMS_PACKAGE;
    }

    @Override
    public void onEnable() {
        org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);

        mainWorld.setSpawnLocation(1387, 226, 21);
        mainWorld.getWorldBorder().reset();

        mainWorld.setGameRuleValue("doMobSpawning", "false");
        mainWorld.setGameRuleValue("mobGriefing", "false");
        mainWorld.setGameRuleValue("doDaylightCycle", "false");
        mainWorld.setGameRuleValue("doFireTick", "false");
        mainWorld.setTime(6000);
        mainWorld.setStorm(false);

        mainWorld.setDifficulty(org.bukkit.Difficulty.NORMAL);

        this.playerManager = new edu.mc.manager.PlayerManager();
        this.lootManager = new edu.mc.manager.LootManager();
        this.scatterManager = new edu.mc.manager.ScatterManager();
        this.flightManager = new edu.mc.manager.FlightManager(this);

        this.gameManager = new GameManager(this);

        Bukkit.getPluginManager().registerEvents(new edu.mc.listener.GameListener(this), this);

        getCommand("chickendinner").setExecutor(new edu.mc.command.GameCommand(this));
        getCommand("hub").setExecutor(new edu.mc.command.HubCommand(this));

        // 注册 BungeeCord 跨服通讯通道
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getLogger().info("MCBG (代号：吃鸡) 1.8.9 核心已启动！");
    }

    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopTimer();
        }
        getLogger().info("MCBG 核心已安全卸载。");
    }

    public edu.mc.manager.PlayerManager getPlayerManager() {
        return playerManager;
    }

    public edu.mc.manager.LootManager getLootManager() {
        return lootManager;
    }

    public edu.mc.manager.ZoneManager getZoneManager() {
        return zoneManager;
    }

    public edu.mc.manager.ScatterManager getScatterManager() {
        return scatterManager;
    }

    public edu.mc.manager.FlightManager getFlightManager() {
        return flightManager;
    }

    public void initZoneManager() {
        if (this.zoneManager == null) {
            this.zoneManager = new edu.mc.manager.ZoneManager(this, Bukkit.getWorlds().get(0));
        }
        this.zoneManager.initBorder();
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public GameState getCurrentState() {
        return currentState;
    }

    private org.bukkit.map.MapView radarMapView = null;
    private byte[] terrainCache = null;

    private byte[] generateTerrainCache(org.bukkit.World world) {
        byte[] cache = new byte[128 * 128];

        // 从编译生成的 JAR 内部直接读取 "吃鸡荒野.png"（零外部依赖）
        try (java.io.InputStream is = this.getClass().getResourceAsStream("/吃鸡荒野.png")) {
            if (is != null) {
                java.awt.image.BufferedImage originalImage = javax.imageio.ImageIO.read(is);
                if (originalImage != null) {
                    java.awt.image.BufferedImage resized = new java.awt.image.BufferedImage(
                            128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g = resized.createGraphics();
                    g.drawImage(originalImage, 0, 0, 128, 128, null);
                    g.dispose();

                    for (int x = 0; x < 128; x++) {
                        for (int y = 0; y < 128; y++) {
                            int rgb   = resized.getRGB(x, y);
                            int r     = (rgb >> 16) & 0xFF;
                            int gCol  = (rgb >> 8)  & 0xFF;
                            int b     = rgb         & 0xFF;
                            int alpha = (rgb >> 24)  & 0xFF;
                            cache[x + y * 128] = (alpha < 50)
                                    ? org.bukkit.map.MapPalette.matchColor(100, 160, 90)
                                    : org.bukkit.map.MapPalette.matchColor(r, gCol, b);
                        }
                    }
                    Bukkit.getLogger().info("[MCBG] 战术雷达：成功从 JAR 内载入高清战术地图！");
                    return cache;
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[MCBG] JAR 内地图图片载入失败：" + e.getMessage());
        }

        // 降级：填充纯草地绿，保证地图可用
        java.util.Arrays.fill(cache, org.bukkit.map.MapPalette.matchColor(100, 160, 90));
        Bukkit.getLogger().warning("[MCBG] 未找到 /吃鸡荒野.png，使用纯色底图。请确认图片已打包进 JAR！");
        return cache;
    }


    public org.bukkit.inventory.ItemStack createRadarMap(org.bukkit.World world) {
        if (radarMapView == null) {
            radarMapView = Bukkit.createMap(world);
            
            // 设置地图正中心为 (0, 16)
            radarMapView.setCenterX(0);
            radarMapView.setCenterZ(16);
            // 设置缩放等级为 NORMAL (1 像素 = 4 格)，让整张岛屿的有效内容几乎放满 128x128 屏幕，大幅提升清晰度！
            radarMapView.setScale(org.bukkit.map.MapView.Scale.NORMAL);
            
            // 清除所有默认渲染器，防止原版生成杂乱的玩家小箭头或渲染不完全的地形
            for (org.bukkit.map.MapRenderer r : radarMapView.getRenderers()) {
                radarMapView.removeRenderer(r);
            }
            
            // 主线程上安全预加载全彩岛屿地形缓存，防止异步渲染线程（Netty 线程）访问世界 Block 导致崩溃！
            this.terrainCache = generateTerrainCache(world);
            
            // 挂载我们高度定制的高科技战地雷达渲染器
            radarMapView.addRenderer(new edu.mc.map.RadarMapRenderer(this, this.terrainCache));
        }

        org.bukkit.inventory.ItemStack mapItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.MAP, 1, radarMapView.getId());
        org.bukkit.inventory.meta.ItemMeta meta = mapItem.getItemMeta();
        meta.setDisplayName("§a§l[战术 GPS 雷达]");
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7放置在手上第一格生效。实时显示：");
        lore.add("§7- ✈ §c随机飞行航线（红色虚线）");
        lore.add("§7- ⚪ §f下级安全圈范围（白色虚线）");
        lore.add("§7- 🔵 §9实时毒圈边缘（蓝色实线）");
        lore.add("§7- 🟢 §a你的实时坐标与鼠标朝向");
        meta.setLore(lore);
        mapItem.setItemMeta(meta);
        return mapItem;
    }

    public void setCurrentState(GameState newState) {
        this.currentState = newState;
        getLogger().info("[状态机] 游戏状态变更至: " + newState.name());
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            String nmsPackage = getNmsPackage();
            Class<?> packetTitleCls = Class.forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutTitle");
            Class<?> chatSerializerCls = Class.forName("net.minecraft.server." + nmsPackage + ".IChatBaseComponent$ChatSerializer");
            Class<?> iChatBaseComponentCls = Class.forName("net.minecraft.server." + nmsPackage + ".IChatBaseComponent");
            Class<?> enumTitleActionCls = Class.forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutTitle$EnumTitleAction");

            Constructor<?> timeConstructor = packetTitleCls.getConstructor(int.class, int.class, int.class);
            Object timePacket = timeConstructor.newInstance(fadeIn, stay, fadeOut);
            sendPacket(player, timePacket);

            if (title != null) {
                Object titleComponent = chatSerializerCls.getMethod("a", String.class).invoke(null, "{\"text\":\"" + title + "\"}");
                Object titleAction = enumTitleActionCls.getField("TITLE").get(null);
                Constructor<?> titleConstructor = packetTitleCls.getConstructor(enumTitleActionCls, iChatBaseComponentCls);
                Object titlePacket = titleConstructor.newInstance(titleAction, titleComponent);
                sendPacket(player, titlePacket);
            }

            if (subtitle != null) {
                Object subtitleComponent = chatSerializerCls.getMethod("a", String.class).invoke(null, "{\"text\":\"" + subtitle + "\"}");
                Object subtitleAction = enumTitleActionCls.getField("SUBTITLE").get(null);
                Constructor<?> subtitleConstructor = packetTitleCls.getConstructor(enumTitleActionCls, iChatBaseComponentCls);
                Object subtitlePacket = subtitleConstructor.newInstance(subtitleAction, subtitleComponent);
                sendPacket(player, subtitlePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendPacket(Player player, Object packet) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
        String nmsPackage = getNmsPackage();
        playerConnection.getClass().getMethod("sendPacket", Class.forName("net.minecraft.server." + nmsPackage + ".Packet")).invoke(playerConnection, packet);
    }
}
