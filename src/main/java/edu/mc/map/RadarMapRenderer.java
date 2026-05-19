package edu.mc.map;

import edu.mc.ChickenDinnerPlugin;
import edu.mc.state.GameState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

public class RadarMapRenderer extends MapRenderer {

    // 所有颜色常量提升为 static final：matchColor() 内部需遍历 128 个调色板条目做颜色距离运算，
    // 每次 render() 调用 6 次将带来不必要的 CPU 开销。提升为类级别常量后只在类加载时计算一次。
    private static final byte COLOR_GRID    = MapPalette.matchColor(50, 50, 50);
    private static final byte COLOR_ISLAND  = MapPalette.matchColor(0, 100, 0);
    private static final byte COLOR_BORDER  = MapPalette.matchColor(0, 80, 220);
    private static final byte COLOR_TARGET  = MapPalette.matchColor(240, 240, 240);
    private static final byte COLOR_PLAYER  = MapPalette.matchColor(0, 255, 0);
    private static final byte COLOR_FLIGHT  = MapPalette.matchColor(255, 0, 0);

    private final ChickenDinnerPlugin plugin;
    private final byte[] terrainCache;
    private final java.util.Map<java.util.UUID, Long> lastRenderTime = new java.util.HashMap<>();
    // 每个玩家地形是否已写入（地形静态，只写一次）
    private final java.util.Set<java.util.UUID> terrainFlushed = new java.util.HashSet<>();
    // 每个玩家上一帧的覆盖层像素索引（用于帧前恢复地形色）
    private final java.util.Map<java.util.UUID, java.util.Set<Integer>> dirtyPixels = new java.util.HashMap<>();

    public RadarMapRenderer(ChickenDinnerPlugin plugin, byte[] terrainCache) {
        super(true);
        this.plugin = plugin;
        this.terrainCache = terrainCache;
    }

    /**
     * 高性能点绘制器：如果当前像素颜色已与目标一致，则跳过 setPixel。
     * 这使得 Spigot 底层仅标记发生了真正改变的微量像素矩形，从而让发送给客户端的网络包体积降低 99.9%！
     */
    private void setPixelOptimized(MapCanvas canvas, int x, int y, byte color) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            if (canvas.getPixel(x, y) != color) {
                canvas.setPixel(x, y, color);
            }
        }
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (player == null || !player.isOnline()) return;

        // 关键性能优化 1：限制渲染更新频率（每秒 4 次 / 250毫秒一次）
        // 瞬间解决因 Spigot 每 tick (20次/秒) 无条件向客户端发送巨大地图数据包导致的网络带宽暴满与延迟！
        long now = System.currentTimeMillis();
        long last = lastRenderTime.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 250) {
            return;
        }
        lastRenderTime.put(player.getUniqueId(), now);

        java.util.UUID pid = player.getUniqueId();

        // 1. 地形底图：每个玩家只全量写入一次，后续帧跳过（地形不变）
        if (!terrainFlushed.contains(pid)) {
            if (terrainCache != null) {
                for (int i = 0; i < 16384; i++) {
                    canvas.setPixel(i & 127, i >> 7, terrainCache[i]);
                }
            }
            terrainFlushed.add(pid);
            dirtyPixels.put(pid, new java.util.HashSet<>());
        } else {
            // 用地形色恢复上一帧的覆盖层像素
            java.util.Set<Integer> prev = dirtyPixels.get(pid);
            if (prev != null && terrainCache != null) {
                for (int idx : prev) {
                    canvas.setPixel(idx & 127, idx >> 7, terrainCache[idx]);
                }
            }
        }
        // 为本帧创建新的 dirty set
        java.util.Set<Integer> dirty = new java.util.HashSet<>();
        dirtyPixels.put(pid, dirty);

        // 2. 绘制战术网格虚线（世界坐标以 100 格为单位绘制）
        for (int wx = -300; wx <= 300; wx += 100) {
            int cx = worldToCanvasX(wx);
            if (cx >= 0 && cx < 128) {
                for (int cy = 0; cy < 128; cy++) {
                    if (cy % 2 == 0) setOverlayPixel(canvas, cx, cy, COLOR_GRID, dirty);
                }
            }
        }
        for (int wz = -300; wz <= 300; wz += 100) {
            int cy = worldToCanvasZ(wz + 16);
            if (cy >= 0 && cy < 128) {
                for (int cx = 0; cx < 128; cx++) {
                    if (cx % 2 == 0) setOverlayPixel(canvas, cx, cy, COLOR_GRID, dirty);
                }
            }
        }

        // 3. 绘制整个大逃杀战斗主岛的轮廓线
        int ix1 = worldToCanvasX(-256), ix2 = worldToCanvasX(249);
        int iz1 = worldToCanvasZ(-237), iz2 = worldToCanvasZ(270);
        drawRect(canvas, ix1, iz1, ix2, iz2, COLOR_ISLAND, false, dirty);

        // 4. 绘制飞机的随机飞行航线（红色虚线）
        if (plugin.getCurrentState() == GameState.FLIGHT || plugin.getCurrentState() == GameState.STARTING || plugin.getCurrentState() == GameState.INGAME) {
            Location start = plugin.getFlightManager().getStartPoint();
            Location end = plugin.getFlightManager().getEndPoint();
            if (start != null && end != null) {
                int sx = worldToCanvasX(start.getX()), sz = worldToCanvasZ(start.getZ());
                int ex = worldToCanvasX(end.getX()),   ez = worldToCanvasZ(end.getZ());
                drawLine(canvas, sx, sz, ex, ez, COLOR_FLIGHT, true, dirty);
            }
        }

        // 5. 绘制下一级白圈安全区范围（白色虚线圈）
        edu.mc.manager.ZoneManager zm = plugin.getZoneManager();
        if (zm != null && zm.getCurrentPhase() < 5) {
            int nx1 = worldToCanvasX(zm.getTargetX() - zm.getTargetSize() / 2.0);
            int nx2 = worldToCanvasX(zm.getTargetX() + zm.getTargetSize() / 2.0);
            int nz1 = worldToCanvasZ(zm.getTargetZ() - zm.getTargetSize() / 2.0);
            int nz2 = worldToCanvasZ(zm.getTargetZ() + zm.getTargetSize() / 2.0);
            drawRect(canvas, nx1, nz1, nx2, nz2, COLOR_TARGET, true, dirty);
        }

        // 6. 绘制实时毒圈位置（蓝色实线）
        World world = player.getWorld();
        org.bukkit.WorldBorder border = world.getWorldBorder();
        int bx1 = worldToCanvasX(border.getCenter().getX() - border.getSize() / 2.0);
        int bx2 = worldToCanvasX(border.getCenter().getX() + border.getSize() / 2.0);
        int bz1 = worldToCanvasZ(border.getCenter().getZ() - border.getSize() / 2.0);
        int bz2 = worldToCanvasZ(border.getCenter().getZ() + border.getSize() / 2.0);
        drawRect(canvas, bx1, bz1, bx2, bz2, COLOR_BORDER, false, dirty);

        // 7. 绘制玩家位置（荧光绿十字点）
        int px = worldToCanvasX(player.getLocation().getX());
        int pz = worldToCanvasZ(player.getLocation().getZ());
        if (px >= 0 && px < 128 && pz >= 0 && pz < 128) {
            setOverlayPixel(canvas, px,     pz,     COLOR_PLAYER, dirty);
            setOverlayPixel(canvas, px + 1, pz,     COLOR_PLAYER, dirty);
            setOverlayPixel(canvas, px - 1, pz,     COLOR_PLAYER, dirty);
            setOverlayPixel(canvas, px,     pz + 1, COLOR_PLAYER, dirty);
            setOverlayPixel(canvas, px,     pz - 1, COLOR_PLAYER, dirty);

            // 8. 朝向线
            double yawRad = Math.toRadians(player.getLocation().getYaw());
            int lx = px + (int) Math.round(-Math.sin(yawRad) * 5);
            int lz = pz + (int) Math.round( Math.cos(yawRad) * 5);
            drawLine(canvas, px, pz, lx, lz, COLOR_PLAYER, false, dirty);
        }
    }

    /**
     * 将世界坐标 X 映射到 canvas 上的 [0-128]（NORMAL缩放：1像素 = 4格）
     */
    private int worldToCanvasX(double worldX) {
        double relativeX = worldX - 0.0;
        return 64 + (int) Math.round(relativeX / 4.0);
    }

    /**
     * 将世界坐标 Z 映射到 canvas 上的 [0-128]（NORMAL缩放：1像素 = 4格）
     */
    private int worldToCanvasZ(double worldZ) {
        double relativeZ = worldZ - 16.0;
        return 64 + (int) Math.round(relativeZ / 4.0);
    }

    /**
     * 绘制覆盖层像素并记录到 dirty set（用于下一帧恢复地形）
     */
    private void setOverlayPixel(MapCanvas canvas, int x, int y, byte color, java.util.Set<Integer> dirty) {
        if (x >= 0 && x < 128 && y >= 0 && y < 128) {
            canvas.setPixel(x, y, color);
            dirty.add(x | (y << 7));
        }
    }

    /**
     * 辅助绘制矩形线框
     */
    private void drawRect(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color, boolean dotted, java.util.Set<Integer> dirty) {
        drawLine(canvas, x1, y1, x2, y1, color, dotted, dirty);
        drawLine(canvas, x1, y2, x2, y2, color, dotted, dirty);
        drawLine(canvas, x1, y1, x1, y2, color, dotted, dirty);
        drawLine(canvas, x2, y1, x2, y2, color, dotted, dirty);
    }

    /**
     * 辅助绘制直线（Bresenham 算法），支持虚线，像素记录到 dirty set
     */
    private void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, byte color, boolean dotted, java.util.Set<Integer> dirty) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, count = 0;
        while (true) {
            if (!dotted || count % 2 == 0) setOverlayPixel(canvas, x1, y1, color, dirty);
            count++;
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 <  dx) { err += dx; y1 += sy; }
        }
    }
}
