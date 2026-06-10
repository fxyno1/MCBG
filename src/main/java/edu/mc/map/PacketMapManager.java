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

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PacketMapManager {

    private final ChickenDinnerPlugin plugin;
    private static final int MAP_SIZE = 128;

    private final Map<UUID, Integer> playerMapIds = new ConcurrentHashMap<>();
    private byte[] cachedTerrainBytes;
    private BukkitRunnable renderTask;
    private int nextMapId = 5000;

    // 预匹配缓存颜色字节
    private byte colorBorder;
    private byte colorTarget;
    private byte colorFlight;
    private byte colorAirdrop;
    private byte colorPlayer;
    private byte colorGround;
    private byte colorGrid;
    private byte colorIsland;

    public PacketMapManager(ChickenDinnerPlugin plugin) {
        this.plugin = plugin;
        initColors();
        loadTerrain();
        startRenderTask();
    }

    private void initColors() {
        colorBorder = org.bukkit.map.MapPalette.matchColor(0, 80, 220);
        colorTarget = org.bukkit.map.MapPalette.matchColor(240, 240, 240);
        colorFlight = org.bukkit.map.MapPalette.matchColor(255, 0, 0);
        colorAirdrop = org.bukkit.map.MapPalette.matchColor(0, 255, 255);
        colorPlayer = org.bukkit.map.MapPalette.matchColor(255, 255, 0);
        colorGround = org.bukkit.map.MapPalette.matchColor(100, 160, 90);
        colorGrid = org.bukkit.map.MapPalette.matchColor(50, 50, 50);
        colorIsland = org.bukkit.map.MapPalette.matchColor(0, 100, 0);
    }

    private void loadTerrain() {
        BufferedImage terrainImage = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = terrainImage.createGraphics();

        // 绘制绿色草地背景
        g.setColor(new Color(100, 160, 90));
        g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

        // 绘制网格线
        drawGridLines(g, terrainImage);

        // 绘制岛屿区域
        drawIslandArea(g, terrainImage);

        g.dispose();

        // 一次性预先转换静态底图的全部像素，缓存为 Minecraft 地图颜色字节
        cachedTerrainBytes = new byte[MAP_SIZE * MAP_SIZE];
        for (int i = 0; i < MAP_SIZE * MAP_SIZE; i++) {
            int rgb = terrainImage.getRGB(i % MAP_SIZE, i / MAP_SIZE);
            int r = (rgb >> 16) & 0xFF;
            int g8 = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            cachedTerrainBytes[i] = org.bukkit.map.MapPalette.matchColor(r, g8, b);
        }

        Bukkit.getLogger().info("[PacketMap] 成功载入高清战术地图底图并预缓存颜色！");
    }

    public void reload() {
        initColors();
        loadTerrain();
    }

    private void drawGridLines(Graphics2D g, BufferedImage image) {
        g.setColor(new Color(50, 50, 50));
        int gridColorRgb = g.getColor().getRGB();
        for (int wx = -GameConfig.MAP_GRID_RANGE; wx <= GameConfig.MAP_GRID_RANGE; wx += GameConfig.MAP_GRID_INTERVAL) {
            int cx = worldToPixelX(wx);
            if (cx >= 0 && cx < MAP_SIZE) {
                for (int cy = 0; cy < MAP_SIZE; cy++) {
                    if (cy % 2 == 0) {
                        image.setRGB(cx, cy, gridColorRgb);
                    }
                }
            }
        }
        for (int wz = -GameConfig.MAP_GRID_RANGE; wz <= GameConfig.MAP_GRID_RANGE; wz += GameConfig.MAP_GRID_INTERVAL) {
            int cy = worldToPixelZ(wz + GameConfig.MAP_CENTER_Z);
            if (cy >= 0 && cy < MAP_SIZE) {
                for (int cx = 0; cx < MAP_SIZE; cx++) {
                    if (cx % 2 == 0) {
                        image.setRGB(cx, cy, gridColorRgb);
                    }
                }
            }
        }
    }

    private void drawIslandArea(Graphics2D g, BufferedImage image) {
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
                "§7拿在手上即可生效",
                "§7- §c飞行航线（红色虚线）",
                "§7- §f下级安全圈范围（白色虚线）",
                "§7- §9实时毒圈边缘（蓝色实线）",
                "§7- §a你的实时坐标与视角朝向"));
        mapItem.setItemMeta(meta);

        player.getInventory().setItem(0, mapItem);

        // 立即发送初始地图数据
        byte[] pixels = renderFrame(player);
        sendMapPacket(player, mapId, pixels);
    }

    public void removeMap(Player player) {
        UUID pid = player.getUniqueId();
        playerMapIds.remove(pid);
    }

    public void clearAll() {
        // 彻底清空所有玩家的地图缓存并发送空白地图数据
        for (UUID pid : new ArrayList<>(playerMapIds.keySet())) {
            Player player = Bukkit.getPlayer(pid);
            if (player != null && player.isOnline()) {
                Integer mapId = playerMapIds.get(pid);
                if (mapId != null) {
                    byte[] blankPixels = new byte[MAP_SIZE * MAP_SIZE];
                    Arrays.fill(blankPixels, (byte) 0);
                    sendMapPacket(player, mapId, blankPixels);
                }
            }
        }

        playerMapIds.clear();
        Bukkit.getLogger().info("[PacketMap] 已清空所有地图缓存");
    }

    private void startRenderTask() {
        renderTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, Integer> entry : playerMapIds.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline())
                        continue;

                    // 检查玩家是否手持地图（从任意快捷栏键位手持）
                    org.bukkit.inventory.ItemStack hand = player.getItemInHand();
                    if (hand == null || hand.getType() != org.bukkit.Material.MAP)
                        continue;

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
        // 创建帧像素缓冲区，并直接复制预缓存的地形底图
        byte[] frame = new byte[MAP_SIZE * MAP_SIZE];
        System.arraycopy(cachedTerrainBytes, 0, frame, 0, MAP_SIZE * MAP_SIZE);

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
                drawLineOnBytes(frame, sx, sz, ex, ez, colorFlight, true);
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
                    worldToPixelX(bx - bs / 2),
                    worldToPixelZ(bz - bs / 2),
                    worldToPixelX(bx + bs / 2),
                    worldToPixelZ(bz + bs / 2),
                    colorBorder);

            // 绘制下一个安全区（白色虚线框）
            if (zm.getCurrentPhase() < 5) {
                drawRectOnBytes(frame,
                        worldToPixelX(zm.getTargetX() - zm.getTargetSize() / 2),
                        worldToPixelZ(zm.getTargetZ() - zm.getTargetSize() / 2),
                        worldToPixelX(zm.getTargetX() + zm.getTargetSize() / 2),
                        worldToPixelZ(zm.getTargetZ() + zm.getTargetSize() / 2),
                        colorTarget);
            }
        }

        // 绘制空投位置
        if (state == GameState.INGAME || state == GameState.FLIGHT) {
            for (Location drop : plugin.getAirdropManager().getActiveAirdrops()) {
                int dx = worldToPixelX(drop.getX());
                int dz = worldToPixelZ(drop.getZ());
                for (int r = -1; r <= 1; r++) {
                    setPixel(frame, dx + r, dz, colorAirdrop);
                    setPixel(frame, dx, dz + r, colorAirdrop);
                }
            }
        }

        // 绘制玩家位置（黄色十字）
        int px = worldToPixelX(player.getLocation().getX());
        int pz = worldToPixelZ(player.getLocation().getZ());
        for (int r = -1; r <= 1; r++) {
            for (int c = -1; c <= 1; c++) {
                if (Math.abs(r) + Math.abs(c) <= 1) {
                    setPixel(frame, px + r, pz + c, colorPlayer);
                }
            }
        }

        // 绘制玩家视角朝向线（根据玩家 Yaw 转向延伸）
        double yawRad = Math.toRadians(player.getLocation().getYaw());
        int dx = (int) Math.round(px - Math.sin(yawRad) * GameConfig.MAP_DIRECTION_LINE_LENGTH);
        int dz = (int) Math.round(pz + Math.cos(yawRad) * GameConfig.MAP_DIRECTION_LINE_LENGTH);
        drawLineOnBytes(frame, px, pz, dx, dz, colorPlayer, false);

        return frame;
    }

    private void sendMapPacket(Player player, int mapId, byte[] pixels) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                    .createPacket(PacketType.Play.Server.MAP);

            packet.getIntegers().write(0, mapId); // 地图 ID
            packet.getBytes().write(0, (byte) 3); // 缩放比例
            packet.getSpecificModifier(Collection.class).write(0, new ArrayList<>()); // 图标 (Cursors)
            packet.getByteArrays().write(0, pixels); // 像素数据

            packet.getIntegers().write(1, 0); // minX
            packet.getIntegers().write(2, 0); // minZ
            packet.getIntegers().write(3, 128); // 宽度 (Columns)
            packet.getIntegers().write(4, 128); // 高度 (Rows)

            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            // 静默处理，防止刷屏
        }
    }

    private int worldToPixelX(double wx) {
        return 64 + (int) Math.round((wx - GameConfig.MAP_CENTER_X) / GameConfig.MAP_SCALE);
    }

    private int worldToPixelZ(double wz) {
        return 64 + (int) Math.round((wz - GameConfig.MAP_CENTER_Z) / GameConfig.MAP_SCALE);
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
            if (x1 == x2 && y1 == y2)
                break;
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
}
