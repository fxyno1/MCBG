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
    private edu.mc.manager.HealingManager healingManager;
    private edu.mc.manager.AirdropManager airdropManager;
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
    public void onLoad() {
        java.io.File backupDir = new java.io.File("world_backup");
        java.io.File worldDir = new java.io.File("world");
        if (backupDir.exists() && backupDir.isDirectory()) {
            getLogger().info("发现 world_backup，正在重置世界地图...");
            deleteDirectory(worldDir);
            try {
                copyDirectory(backupDir, worldDir);
                getLogger().info("地图重置成功！");
            } catch (Exception e) {
                getLogger().severe("地图重置失败：" + e.getMessage());
                e.printStackTrace();
            }
        }

        // 清理地图缓存文件和重置地图ID计数器
        try {
            cleanMapFiles();
            getLogger().info("地图缓存文件和地图ID计数器清理成功！");
        } catch (Exception e) {
            getLogger().warning("清理地图文件失败: " + e.getMessage());
        }
    }

    private void cleanMapFiles() {
        java.io.File serverDir = new java.io.File(".");
        java.io.File[] dirs = serverDir.listFiles();
        if (dirs != null) {
            for (java.io.File dir : dirs) {
                if (dir.isDirectory()) {
                    java.io.File dataDir = new java.io.File(dir, "data");
                    if (dataDir.exists() && dataDir.isDirectory()) {
                        java.io.File[] files = dataDir.listFiles();
                        if (files != null) {
                            for (java.io.File f : files) {
                                String name = f.getName();
                                if ((name.startsWith("map_") && name.endsWith(".dat")) || name.equals("idcounts.dat")) {
                                    f.delete();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void deleteDirectory(java.io.File path) {
        if (path.exists()) {
            java.io.File[] files = path.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.isDirectory()) {
                        deleteDirectory(f);
                    }
                    f.delete();
                }
            }
        }
    }

    private void copyDirectory(java.io.File source, java.io.File destination) throws java.io.IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdirs();
            }
            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    java.io.File srcFile = new java.io.File(source, file);
                    java.io.File destFile = new java.io.File(destination, file);
                    copyDirectory(srcFile, destFile);
                }
            }
        } else {
            java.nio.file.Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public void onEnable() {
        org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);

        mainWorld.setSpawnLocation((int) GameConfig.LOBBY_X, (int) GameConfig.LOBBY_Y, (int) GameConfig.LOBBY_Z);
        mainWorld.getWorldBorder().reset();

        mainWorld.setGameRuleValue("doMobSpawning", "false");
        mainWorld.setGameRuleValue("mobGriefing", "false");
        mainWorld.setGameRuleValue("doDaylightCycle", "false");
        mainWorld.setGameRuleValue("doFireTick", "false");
        mainWorld.setTime(6000);
        mainWorld.setStorm(false);

        mainWorld.setDifficulty(org.bukkit.Difficulty.NORMAL);

        this.playerManager = new edu.mc.manager.PlayerManager();
        this.lootManager = new edu.mc.manager.LootManager(this);
        this.healingManager = new edu.mc.manager.HealingManager(this);
        this.airdropManager = new edu.mc.manager.AirdropManager(this);
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

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopTimer();
        }
        if (airdropManager != null) {
            airdropManager.reset();
        }

        // 【新增修复】服务器关闭/重载时，彻底释放原版地图上的自定义渲染器，防止留给下一把或者造成内存泄漏
        for (org.bukkit.map.MapView mapView : playerRadarMaps.values()) {
            if (mapView != null) {
                for (org.bukkit.map.MapRenderer renderer : mapView.getRenderers()) {
                    if (renderer instanceof edu.mc.map.RadarMapRenderer) {
                        mapView.removeRenderer(renderer);
                    }
                }
            }
        }
        playerRadarMaps.clear();

        getLogger().info("MCBG 核心已安全卸载。");
    }

    public edu.mc.manager.PlayerManager getPlayerManager() {
        return playerManager;
    }

    public edu.mc.manager.LootManager getLootManager() {
        return lootManager;
    }

    public edu.mc.manager.HealingManager getHealingManager() {
        return healingManager;
    }

    public edu.mc.manager.AirdropManager getAirdropManager() {
        return airdropManager;
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

    private final java.util.Map<java.util.UUID, org.bukkit.map.MapView> playerRadarMaps = new java.util.concurrent.ConcurrentHashMap<>();
    private byte[] terrainCache = null;

    public byte[] getTerrainCache() {
        return this.terrainCache;
    }

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
                            int rgb = resized.getRGB(x, y);
                            int r = (rgb >> 16) & 0xFF;
                            int gCol = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;
                            int alpha = (rgb >> 24) & 0xFF;
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

    public org.bukkit.inventory.ItemStack createRadarMap(org.bukkit.entity.Player player) {
        org.bukkit.World world = player.getWorld();
        java.util.UUID pid = player.getUniqueId();
        org.bukkit.map.MapView mapView = playerRadarMaps.get(pid);
        if (mapView == null) {
            mapView = Bukkit.createMap(world);

            // 设置地图正中心为 (0, 16)
            mapView.setCenterX(0);
            mapView.setCenterZ(16);
            // 设置缩放等级为 NORMAL (1 像素 = 4 格)，让整张岛屿的有效内容几乎放满 128x128 屏幕，大幅提升清晰度！
            mapView.setScale(org.bukkit.map.MapView.Scale.NORMAL);

            // 清除所有默认渲染器，防止原版生成杂乱的玩家小箭头或渲染不完全的地形
            for (org.bukkit.map.MapRenderer r : mapView.getRenderers()) {
                mapView.removeRenderer(r);
            }

            // 主线程上安全预加载全彩岛屿地形缓存，防止异步渲染线程（Netty 线程）访问世界 Block 导致崩溃！
            if (this.terrainCache == null) {
                this.terrainCache = generateTerrainCache(world);
            }

            // 挂载我们高度定制的高科技战地雷达渲染器
            mapView.addRenderer(new edu.mc.map.RadarMapRenderer(this, this.terrainCache));
            playerRadarMaps.put(pid, mapView);
        }

        org.bukkit.inventory.ItemStack mapItem = new org.bukkit.inventory.ItemStack(org.bukkit.Material.MAP, 1,
                mapView.getId());
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

    /**
     * 清除指定玩家的雷达地图渲染缓存，确保其下次拿到地图时立刻全量刷新。
     * 在玩家死亡/成为旁观者时调用，防止旧帧死亡位置残留到下一局。
     */
    public void resetPlayerMap(java.util.UUID pid) {
        // 不要在这里 remove 掉 mapView，保留映射，但要驱使它内部清空该玩家的帧缓存
        org.bukkit.map.MapView mapView = playerRadarMaps.get(pid);
        if (mapView == null) return;

        for (org.bukkit.map.MapRenderer renderer : mapView.getRenderers()) {
            if (renderer instanceof edu.mc.map.RadarMapRenderer) {
                ((edu.mc.map.RadarMapRenderer) renderer).resetPlayerState(pid);
            }
        }
    }

    /**
     * 强制重置全局的地图缓存，将航线等静态绘制痕迹彻底抹除。
     * 在每一局游戏结束回归大厅时调用。
     */
    public void resetGlobalMapCache() {
        // 先清除全局底图缓存的航线残留
        this.terrainCache = null;

        for (org.bukkit.map.MapView mapView : playerRadarMaps.values()) {
            if (mapView == null) continue;
            for (org.bukkit.map.MapRenderer renderer : mapView.getRenderers()) {
                if (renderer instanceof edu.mc.map.RadarMapRenderer) {
                    ((edu.mc.map.RadarMapRenderer) renderer).resetGlobalCache();
                }
            }
        }
    }

    private Class<?> packetTitleCls;
    private Class<?> chatSerializerCls;
    private Class<?> iChatBaseComponentCls;
    private Class<?> enumTitleActionCls;
    private Class<?> packetCls;
    private Constructor<?> timeConstructor;
    private Constructor<?> titleConstructor;
    private java.lang.reflect.Method chatSerializerMethod;
    private java.lang.reflect.Method getHandleMethod;
    private java.lang.reflect.Field playerConnectionField;
    private java.lang.reflect.Method sendPacketMethod;
    private Object titleActionEnum;
    private Object subtitleActionEnum;
    private boolean reflectionInitialized = false;

    private void initReflection() {
        if (reflectionInitialized)
            return;
        try {
            String nmsPackage = getNmsPackage();
            packetTitleCls = Class.forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutTitle");
            chatSerializerCls = Class
                    .forName("net.minecraft.server." + nmsPackage + ".IChatBaseComponent$ChatSerializer");
            iChatBaseComponentCls = Class.forName("net.minecraft.server." + nmsPackage + ".IChatBaseComponent");
            enumTitleActionCls = Class
                    .forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutTitle$EnumTitleAction");
            packetCls = Class.forName("net.minecraft.server." + nmsPackage + ".Packet");

            timeConstructor = packetTitleCls.getConstructor(int.class, int.class, int.class);
            chatSerializerMethod = chatSerializerCls.getMethod("a", String.class);
            titleActionEnum = enumTitleActionCls.getField("TITLE").get(null);
            subtitleActionEnum = enumTitleActionCls.getField("SUBTITLE").get(null);
            titleConstructor = packetTitleCls.getConstructor(enumTitleActionCls, iChatBaseComponentCls);

            // 缓存 CraftBukkit 和 NMS 发送 Packet 的反射方法与字段，彻底消除每次调用的巨大耗时
            Class<?> craftPlayerCls = Class.forName("org.bukkit.craftbukkit." + nmsPackage + ".entity.CraftPlayer");
            getHandleMethod = craftPlayerCls.getMethod("getHandle");
            Class<?> entityPlayerCls = Class.forName("net.minecraft.server." + nmsPackage + ".EntityPlayer");
            playerConnectionField = entityPlayerCls.getField("playerConnection");
            Class<?> playerConnectionCls = Class.forName("net.minecraft.server." + nmsPackage + ".PlayerConnection");
            sendPacketMethod = playerConnectionCls.getMethod("sendPacket", packetCls);

            reflectionInitialized = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            initReflection();
            if (!reflectionInitialized)
                return;

            Object timePacket = timeConstructor.newInstance(fadeIn, stay, fadeOut);
            sendPacket(player, timePacket);

            if (title != null) {
                Object titleComponent = chatSerializerMethod.invoke(null, "{\"text\":\"" + title + "\"}");
                Object titlePacket = titleConstructor.newInstance(titleActionEnum, titleComponent);
                sendPacket(player, titlePacket);
            }

            if (subtitle != null) {
                Object subtitleComponent = chatSerializerMethod.invoke(null, "{\"text\":\"" + subtitle + "\"}");
                Object subtitlePacket = titleConstructor.newInstance(subtitleActionEnum, subtitleComponent);
                sendPacket(player, subtitlePacket);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendActionBar(Player player, String message) {
        initReflection();
        if (!reflectionInitialized)
            return;
        try {
            Object handle = getHandleMethod.invoke(player);
            Object connection = playerConnectionField.get(handle);
            Object chatComponent = chatSerializerMethod.invoke(null, "{\"text\": \"" + message + "\"}");
            Class<?> packetChatCls = Class.forName("net.minecraft.server." + getNmsPackage() + ".PacketPlayOutChat");
            Object packet = packetChatCls.getConstructor(iChatBaseComponentCls, byte.class).newInstance(chatComponent,
                    (byte) 2);
            sendPacketMethod.invoke(connection, packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void sendPacket(Player player, Object packet) throws Exception {
        initReflection();
        if (!reflectionInitialized)
            return;
        Object handle = getHandleMethod.invoke(player);
        Object playerConnection = playerConnectionField.get(handle);
        sendPacketMethod.invoke(playerConnection, packet);
    }

}
