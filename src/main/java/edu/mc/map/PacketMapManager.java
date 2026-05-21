package edu.mc.map;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.GameConfig;
import edu.mc.manager.ZoneManager;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketMapManager {

    private final ChickenDinnerPlugin plugin;
    private static final int MAP_SIZE = 128;
    
    private final Map<UUID, Integer> playerMapIds = new ConcurrentHashMap<>();
    private final Map<Integer, MapRenderData> activeMaps = new ConcurrentHashMap<>();
    private BufferedImage terrainImage;
    private BukkitRunnable renderTask;
    private int nextMapId = 5000;
    
    // NMS 反射相关
    private static Constructor<?> mapPacketConstructor;
    private static boolean reflectionInitialized = false;

    public PacketMapManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        loadTerrain();
        initReflection();
        startRenderTask();
    }

    private void loadTerrain() {
        terrainImage = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = terrainImage.createGraphics();
        
        // 绘制绿色草地背景
        g.setColor(new Color(100, 160, 90));
        g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
        
        // 绘制网格线
        drawGridLines(g);
        
        // 绘制岛屿区域
        drawIslandArea(g);
        
        g.dispose();
        Bukkit.getLogger().info("[PacketMap] 成功载入高清战术地图底图！");
    }

    private void drawGridLines(Graphics2D g) {
        g.setColor(new Color(50, 50, 50));
        for (int wx = -GameConfig.MAP_GRID_RANGE; wx <= GameConfig.MAP_GRID_RANGE; wx += GameConfig.MAP_GRID_INTERVAL) {
            int cx = worldToPixelX(wx);
            if (cx >= 0 && cx < MAP_SIZE) {
                for (int cy = 0; cy < MAP_SIZE; cy++) {
                    if (cy % 2 == 0) {
                        terrainImage.setRGB(cx, cy, g.getColor().getRGB());
                    }
                }
            }
        }
        for (int wz = -GameConfig.MAP_GRID_RANGE; wz <= GameConfig.MAP_GRID_RANGE; wz += GameConfig.MAP_GRID_INTERVAL) {
            int cy = worldToPixelZ(wz + GameConfig.MAP_CENTER_Z);
            if (cy >= 0 && cy < MAP_SIZE) {
                for (int cx = 0; cx < MAP_SIZE; cx++) {
                    if (cx % 2 == 0) {
                        terrainImage.setRGB(cx, cy, g.getColor().getRGB());
                    }
                }
            }
        }
    }

    private void drawIslandArea(Graphics2D g) {
        g.setColor(new Color(0, 100, 0));
        int ix1 = worldToPixelX(GameConfig.ISLAND_X1);
        int ix2 = worldToPixelX(GameConfig.ISLAND_X2);
        int iz1 = worldToPixelZ(GameConfig.ISLAND_Z1);
        int iz2 = worldToPixelZ(GameConfig.ISLAND_Z2);
        g.drawRect(Math.min(ix1, ix2), Math.min(iz1, iz2), 
                   Math.abs(ix2 - ix1), Math.abs(iz2 - iz1));
    }

    public void giveMap(Player player) {
        UUID pid = player.getUniqueId();
        
        // 如果玩家已有地图，先清理
        if (playerMapIds.containsKey(pid)) {
            removeMap(player);
        }

        int mapId = nextMapId++;
        playerMapIds.put(pid, mapId);
        
        // 创建地图物品
        org.bukkit.inventory.ItemStack mapItem = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.MAP, 1, (short) mapId);
        org.bukkit.inventory.meta.ItemMeta meta = mapItem.getItemMeta();
        meta.setDisplayName("§a§l[战术 GPS 雷达]");
        meta.setLore(Arrays.asList(
            "§7放在手上第一格生效",
            "§7- §c飞行航线（红色虚线）",
            "§7- §f下级安全圈范围（白色虚线）",
            "§7- §9实时毒圈边缘（蓝色实线）",
            "§7- §a你的实时坐标与鼠标朝向"
        ));
        mapItem.setItemMeta(meta);
        
        player.getInventory().setItem(0, mapItem);
        
        // 立即发送初始地图数据
        sendInitialMapData(player, mapId);
    }

    private void sendInitialMapData(Player player, int mapId) {
        byte[] pixels = renderFrame(player);
        sendMapPacket(player, mapId, pixels);
    }

    public void removeMap(Player player) {
        UUID pid = player.getUniqueId();
        Integer mapId = playerMapIds.remove(pid);
        if (mapId != null) {
            activeMaps.remove(mapId);
        }
    }

    public void clearAll() {
        // 彻底清空所有玩家的地图缓存
        for (UUID pid : new ArrayList<>(playerMapIds.keySet())) {
            Player player = Bukkit.getPlayer(pid);
            if (player != null && player.isOnline()) {
                // 发送空白地图数据包
                Integer mapId = playerMapIds.get(pid);
                if (mapId != null) {
                    byte[] blankPixels = new byte[MAP_SIZE * MAP_SIZE];
                    Arrays.fill(blankPixels, (byte) 0);
                    sendMapPacket(player, mapId, blankPixels);
                }
            }
        }
        
        playerMapIds.clear();
        activeMaps.clear();
        Bukkit.getLogger().info("[PacketMap] 已清空所有地图缓存");
    }

    private void startRenderTask() {
        renderTask = new BukkitRunnable() {
            @Override
            public void run() {
                GameState state = plugin.getCurrentState();
                
                for (Map.Entry<UUID, Integer> entry : playerMapIds.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) continue;
                    
                    // 检查玩家是否手持地图
                    org.bukkit.inventory.ItemStack hand = player.getInventory().getItem(0);
                    if (hand == null || hand.getType() != org.bukkit.Material.MAP) continue;
                    
                    // 渲染并发送地图数据
                    int mapId = entry.getValue();
                    byte[] pixels = renderFrame(player);
                    sendMapPacket(player, mapId, pixels);
                }
            }
        };
        renderTask.runTaskTimer(plugin, 20L, 5L); // 每5tick更新一次（0.25秒）
    }

    public void stop() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
        clearAll();
    }

    private byte[] renderFrame(Player player) {
        // 复制底图
        byte[] frame = new byte[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < terrainImage.getWidth() * terrainImage.getHeight(); i++) {
            int rgb = terrainImage.getRGB(i % MAP_SIZE, i / MAP_SIZE);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            frame[i] = org.bukkit.map.MapPalette.matchColor(r, g, b);
        }
        
        GameState state = plugin.getCurrentState();
        World world = player.getWorld();

        // 绘制飞行航线
        if (state == GameState.FLIGHT || state == GameState.STARTING || state == GameState.INGAME) {
            Location start = plugin.getFlightManager().getStartPoint();
            Location end = plugin.getFlightManager().getEndPoint();
            if (start != null && end != null) {
                int sx = worldToPixelX(start.getX());
                int sz = worldToPixelZ(start.getZ());
                int ex = worldToPixelX(end.getX());
                int ez = worldToPixelZ(end.getZ());
                drawLineOnBytes(frame, sx, sz, ex, ez, colorFlight(), true);
            }
        }

        // 绘制毒圈和安全区
        ZoneManager zm = plugin.getZoneManager();
        if (zm != null && state != GameState.LOBBY) {
            org.bukkit.WorldBorder border = world.getWorldBorder();
            double bx = border.getCenter().getX();
            double bz = border.getCenter().getZ();
            double bs = border.getSize();
            
            // 绘制当前毒圈（蓝色）
            drawRectOnBytes(frame, 
                worldToPixelX(bx - bs/2), 
                worldToPixelZ(bz - bs/2),
                worldToPixelX(bx + bs/2), 
                worldToPixelZ(bz + bs/2), 
                colorBorder());
            
            // 绘制下一个安全区（白色虚线框）
            if (zm.getCurrentPhase() < 5) {
                drawRectOnBytes(frame,
                    worldToPixelX(zm.getTargetX() - zm.getTargetSize()/2),
                    worldToPixelZ(zm.getTargetZ() - zm.getTargetSize()/2),
                    worldToPixelX(zm.getTargetX() + zm.getTargetSize()/2),
                    worldToPixelZ(zm.getTargetZ() + zm.getTargetSize()/2), 
                    colorTarget());
            }
        }

        // 绘制空投位置
        if (state == GameState.INGAME || state == GameState.FLIGHT) {
            for (Location drop : plugin.getAirdropManager().getActiveAirdrops()) {
                int dx = worldToPixelX(drop.getX());
                int dz = worldToPixelZ(drop.getZ());
                for (int r = -1; r <= 1; r++) {
                    setPixel(frame, dx + r, dz, colorAirdrop());
                    setPixel(frame, dx, dz + r, colorAirdrop());
                }
            }
        }

        // 绘制玩家位置（黄色十字）
        int px = worldToPixelX(player.getLocation().getX());
        int pz = worldToPixelZ(player.getLocation().getZ());
        for (int r = -1; r <= 1; r++) {
            for (int c = -1; c <= 1; c++) {
                if (Math.abs(r) + Math.abs(c) <= 1) {
                    setPixel(frame, px + r, pz + c, colorPlayer());
                }
            }
        }
        
        return frame;
    }

    private void sendMapPacket(Player player, int mapId, byte[] pixels) {
        if (!reflectionInitialized || mapPacketConstructor == null) return;
        
        try {
            Object packet = mapPacketConstructor.newInstance(
                mapId,
                (byte) 3,
                new java.util.ArrayList<>(),
                pixels,
                0,
                0,
                128,
                128
            );
            plugin.sendPacketToPlayer(player, packet);
        } catch (Exception e) {
            // 静默处理异常，避免刷屏
        }
    }

    private int worldToPixelX(double wx) {
        return 64 + (int)Math.round((wx - GameConfig.MAP_CENTER_X) / GameConfig.MAP_SCALE);
    }

    private int worldToPixelZ(double wz) {
        return 64 + (int)Math.round((wz - GameConfig.MAP_CENTER_Z) / GameConfig.MAP_SCALE);
    }

    private void setPixel(byte[] f, int x, int y, byte c) {
        if (x >= 0 && x < MAP_SIZE && y >= 0 && y < MAP_SIZE) {
            f[x + y * MAP_SIZE] = c;
        }
    }

    private void drawRectOnBytes(byte[] f, int x1, int y1, int x2, int y2, byte c) {
        drawLineOnBytes(f, x1, y1, x2, y1, c, false);
        drawLineOnBytes(f, x1, y2, x2, y2, c, false);
        drawLineOnBytes(f, x1, y1, x1, y2, c, false);
        drawLineOnBytes(f, x2, y1, x2, y2, c, false);
    }

    private void drawLineOnBytes(byte[] f, int x1, int y1, int x2, int y2, byte c, boolean dashed) {
        x1 = clamp(x1);
        y1 = clamp(y1);
        x2 = clamp(x2);
        y2 = clamp(y2);
        
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int cnt = 0;
        
        while (true) {
            if (!dashed || cnt % 2 == 0) {
                setPixel(f, x1, y1, c);
            }
            cnt++;
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(MAP_SIZE - 1, v));
    }

    // 颜色定义
    private byte colorGround() { return org.bukkit.map.MapPalette.matchColor(100, 160, 90); }
    private byte colorGrid() { return org.bukkit.map.MapPalette.matchColor(50, 50, 50); }
    private byte colorIsland() { return org.bukkit.map.MapPalette.matchColor(0, 100, 0); }
    private byte colorBorder() { return org.bukkit.map.MapPalette.matchColor(0, 80, 220); }
    private byte colorTarget() { return org.bukkit.map.MapPalette.matchColor(240, 240, 240); }
    private byte colorFlight() { return org.bukkit.map.MapPalette.matchColor(255, 0, 0); }
    private byte colorAirdrop() { return org.bukkit.map.MapPalette.matchColor(0, 255, 255); }
    private byte colorPlayer() { return org.bukkit.map.MapPalette.matchColor(255, 255, 0); }

    private void initReflection() {
        if (reflectionInitialized) return;
        try {
            String nmsPackage = ChickenDinnerPlugin.getNmsPackage();
            Class<?> packetClass = Class.forName("net.minecraft.server." + nmsPackage + ".PacketPlayOutMap");
            mapPacketConstructor = packetClass.getConstructor(
                int.class,
                byte.class,
                java.util.Collection.class,
                byte[].class,
                int.class,
                int.class,
                int.class,
                int.class
            );
            reflectionInitialized = true;
            Bukkit.getLogger().info("[PacketMap] NMS反射初始化成功！");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[PacketMap] NMS反射初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static class MapRenderData {
        private final int mapId;
        private final UUID playerId;
        private long lastUpdate;

        public MapRenderData(int mapId, UUID playerId) {
            this.mapId = mapId;
            this.playerId = playerId;
            this.lastUpdate = System.currentTimeMillis();
        }

        public void update() {
            this.lastUpdate = System.currentTimeMillis();
        }
    }
}
